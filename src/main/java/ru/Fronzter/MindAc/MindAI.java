package ru.Fronzter.MindAc;

import com.github.retrooper.packetevents.PacketEvents;
import com.squareup.moshi.JsonAdapter;
import com.squareup.moshi.Moshi;
import com.squareup.moshi.Types;
import io.github.retrooper.packetevents.factory.spigot.SpigotPacketEventsBuilder;
import okhttp3.*;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;
import ru.Fronzter.MindAc.command.CommandManager;
import ru.Fronzter.MindAc.listener.ConnectionListener;
import ru.Fronzter.MindAc.listener.MitigationListener;
import ru.Fronzter.MindAc.listener.PacketListener;
import ru.Fronzter.MindAc.manager.LocaleManager;
import ru.Fronzter.MindAc.mitigation.MitigationManager;
import ru.Fronzter.MindAc.service.DatabaseService;
import ru.Fronzter.MindAc.service.HeartbeatService;
import ru.Fronzter.MindAc.service.ViolationManager;
import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

public final class MindAI extends JavaPlugin {
    private static MindAI instance;
    private static boolean isLicenseValid = false;
    private int heartbeatTaskID = -1;
    private String publicServerIp = null;
    private final Set<UUID> alertsDisabledAdmins = ConcurrentHashMap.newKeySet();
    private DatabaseService databaseService;
    private OkHttpClient httpClient;
    private ViolationManager violationManager;
    private LocaleManager localeManager;
    private MitigationManager mitigationManager;

    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();

        this.localeManager = new LocaleManager(this);

        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(10, TimeUnit.SECONDS)
                .writeTimeout(10, TimeUnit.SECONDS)
                .build();

        databaseService = new DatabaseService(this);
        databaseService.init();

        this.violationManager = new ViolationManager(this);
        this.mitigationManager = new MitigationManager(this);

        String apiKey = getConfig().getString("api-key", "");
        if (apiKey.isEmpty() || apiKey.equals("CHANGE-ME-TO-YOUR-API-KEY")) {
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        discoverPublicIpAndValidateLicense(apiKey);

        CommandManager commandManager = new CommandManager(this);
        getCommand("mindai").setExecutor(commandManager);
        getCommand("mindai").setTabCompleter(commandManager);

        getServer().getPluginManager().registerEvents(new MitigationListener(this), this);
    }

    private void discoverPublicIpAndValidateLicense(final String apiKey) {
        Bukkit.getScheduler().runTaskAsynchronously(this, () -> {
            try {
                Request ipRequest = new Request.Builder().url("https://api.ipify.org").build();
                try (Response ipResponse = this.httpClient.newCall(ipRequest).execute()) {
                    if (ipResponse.isSuccessful() && ipResponse.body() != null) {
                        this.publicServerIp = ipResponse.body().string().trim();
                        validateLicense(apiKey, this.publicServerIp);
                    } else {
                        disablePlugin();
                    }
                }
            } catch (Exception e) {
                disablePlugin();
            }
        });
    }

    private void validateLicense(String apiKey, String serverIp) {
        String licenseUrl = getConfig().getString("api-endpoints.license-server", "") + "/validate";
        if (licenseUrl.isEmpty() || licenseUrl.equals("/validate")) {
            disablePlugin();
            return;
        }
        try {
            Map<String, String> data = new HashMap<>();
            data.put("license_key", apiKey);
            data.put("server_ip", serverIp);
            String json = LazyHolder.JSON_ADAPTER.toJson(data);
            RequestBody body = RequestBody.create(json, MediaType.parse("application/json; charset=utf-8"));
            Request request = new Request.Builder().url(licenseUrl).post(body).build();
            try (Response response = this.httpClient.newCall(request).execute()) {
                String responseBody = response.body() != null ? response.body().string() : "";
                if (response.isSuccessful()) {
                    Map<String, Object> result = LazyHolder.RESPONSE_ADAPTER.fromJson(responseBody);
                    if (result != null && (Boolean) result.getOrDefault("valid", false)) {
                        isLicenseValid = true;
                        Bukkit.getScheduler().runTask(this, this::initializePluginServices);
                    } else {
                        disablePlugin();
                    }
                } else {
                    disablePlugin();
                }
            }
        } catch (Exception e) {
            disablePlugin();
        }
    }

    private void disablePlugin() {
        Bukkit.getScheduler().runTask(this, () -> getServer().getPluginManager().disablePlugin(this));
    }

    private void initializePluginServices() {
        PacketEvents.setAPI(SpigotPacketEventsBuilder.build(this));
        PacketEvents.getAPI().getSettings().checkForUpdates(false).reEncodeByDefault(false);
        PacketEvents.getAPI().load();
        PacketEvents.getAPI().getEventManager().registerListeners(new ConnectionListener(), new PacketListener());
        PacketEvents.getAPI().init();
        long interval = 20L * 60 * 5;
        heartbeatTaskID = Bukkit.getScheduler().runTaskTimerAsynchronously(this, HeartbeatService::sendHeartbeat, 100L, interval).getTaskId();
    }

    @Override
    public void onDisable() {
        if (databaseService != null) {
            databaseService.close();
        }
        if (heartbeatTaskID != -1) {
            Bukkit.getScheduler().cancelTask(heartbeatTaskID);
        }
        if (httpClient != null) {
            httpClient.dispatcher().executorService().shutdown();
            httpClient.connectionPool().evictAll();
        }
        try {
            if (PacketEvents.getAPI() != null && PacketEvents.getAPI().isLoaded()) {
                PacketEvents.getAPI().terminate();
            }
        } catch (NoClassDefFoundError ignored) {}
    }

    public void reloadPluginConfig() {
        reloadConfig();
        localeManager.loadMessages();
    }

    public boolean toggleAlerts(UUID uuid) {
        if (alertsDisabledAdmins.contains(uuid)) {
            alertsDisabledAdmins.remove(uuid);
            return true;
        } else {
            alertsDisabledAdmins.add(uuid);
            return false;
        }
    }

    public boolean areAlertsEnabledFor(UUID uuid) {
        return !alertsDisabledAdmins.contains(uuid);
    }

    public static MindAI getInstance() { return instance; }
    public static boolean isLicenseActive() { return isLicenseValid; }
    public String getPublicServerIp() { return this.publicServerIp; }
    public DatabaseService getDatabaseService() { return databaseService; }
    public OkHttpClient getHttpClient() { return this.httpClient; }
    public ViolationManager getViolationManager() { return this.violationManager; }
    public LocaleManager getLocaleManager() { return this.localeManager; }
    public MitigationManager getMitigationManager() { return mitigationManager; }

    private static class LazyHolder {
        private static final Moshi MOSHI = new Moshi.Builder().build();
        private static final Type MAP_STRING_STRING_TYPE = Types.newParameterizedType(Map.class, String.class, String.class);
        private static final JsonAdapter<Map<String, String>> JSON_ADAPTER = MOSHI.adapter(MAP_STRING_STRING_TYPE);
        private static final Type MAP_STRING_OBJECT_TYPE = Types.newParameterizedType(Map.class, String.class, Object.class);
        private static final JsonAdapter<Map<String, Object>> RESPONSE_ADAPTER = MOSHI.adapter(MAP_STRING_OBJECT_TYPE);
    }
}
