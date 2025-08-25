package ru.Fronzter.MindAc.service;

import com.squareup.moshi.JsonAdapter;
import com.squareup.moshi.Moshi;
import com.squareup.moshi.Types;
import okhttp3.*;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import ru.Fronzter.MindAc.MindAI;
import ru.Fronzter.MindAc.entity.PlayerEntity;
import ru.Fronzter.MindAc.util.MoshiFactory;
import java.io.IOException;
import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.Map;

public class AnalysisService {

    public static void analyze(final PlayerEntity entity) {
        if (!MindAI.isLicenseActive() || entity.getFrames().isEmpty()) {
            return;
        }
        entity.setLastAnalyzedFrames(entity.getFrames());

        String json;
        try {
            Map<String, Object> dataToSend = new HashMap<>();
            dataToSend.put("license_key", MindAI.getInstance().getConfig().getString("api-key"));
            dataToSend.put("name", entity.getName());
            dataToSend.put("frames", entity.getLastAnalyzedFrames());

            Moshi moshi = MoshiFactory.getInstance();
            Type mapType = Types.newParameterizedType(Map.class, String.class, Object.class);
            JsonAdapter<Map<String, Object>> adapter = moshi.adapter(mapType);
            json = adapter.toJson(dataToSend);
        } catch (Exception e) {
            return;
        }

        String url = MindAI.getInstance().getConfig().getString("api-endpoints.analysis-server", "") + "/predict";
        if (url.isEmpty() || url.equals("/predict")) {
            return;
        }

        RequestBody body = RequestBody.create(json, MediaType.parse("application/json; charset=utf-8"));
        Request request = new Request.Builder().url(url).post(body).build();

        LazyHolder.CLIENT.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                // убраны логеры, я думаю они вам и не нужны в этом классе :) 
            }

            @Override
            public void onResponse(Call call, Response response) {
                try (ResponseBody responseBody = response.body()) {
                    if (!response.isSuccessful() || responseBody == null) {
                        return;
                    }
                    String bodyString = responseBody.string();
                    Map<String, Double> result = LazyHolder.MAP_ADAPTER.fromJson(bodyString);
                    if (result != null && result.containsKey("cheat_probability")) {
                        double probability = result.get("cheat_probability");
                        Bukkit.getScheduler().runTask(MindAI.getInstance(), () -> handleResult(entity, probability));
                    }
                } catch (Exception ignored) {
                }
            }
        });
    }

    private static void handleResult(PlayerEntity entity, double probability) {
        MindAI plugin = MindAI.getInstance();
        double classificationThreshold = plugin.getConfig().getDouble("ml-check.classification-threshold");

        if (probability <= classificationThreshold) {
            return;
        }

        if (entity.isProcessingFlag()) {
            return;
        }
        entity.setProcessingFlag(true);

        long windowMillis = plugin.getConfig().getLong("ml-check.window", 600) * 1000L;

        plugin.getDatabaseService().countRecentViolationsAsync(entity.getUUID(), windowMillis, oldCount -> {

            int newTotalViolations = oldCount + 1;
            int violationThreshold = plugin.getConfig().getInt("ml-check.violation-threshold", 3);

            sendAlert(entity, probability, newTotalViolations, violationThreshold);

            plugin.getDatabaseService().logViolationAsync(entity.getUUID(), entity.getName(), probability);

            if (newTotalViolations >= violationThreshold) {
                punishPlayer(entity, () -> {
                    plugin.getDatabaseService().clearRecentViolationsAsync(entity.getUUID(), windowMillis);
                    entity.setProcessingFlag(false);
                });
            } else {
                entity.setProcessingFlag(false);
            }
        });
    }

    private static void sendAlert(PlayerEntity entity, double probability, int currentVl, int maxVl) {
        if (!MindAI.getInstance().getConfig().getBoolean("alerts.enabled", true)) {
            return;
        }
        String message = MindAI.getInstance().getConfig().getString("alerts.message");
        if (message == null || message.isEmpty()) return;

        String formattedProb = String.format("%.2f%%", probability * 100.0D);
        String vlString = currentVl + "/" + maxVl;

        String finalMessage = ChatColor.translateAlternateColorCodes('&',
                message.replace("%player%", entity.getName())
                        .replace("%probability%", formattedProb)
                        .replace("%vl%", vlString));

        String permission = MindAI.getInstance().getConfig().getString("alerts.permission", "mindai.alerts");
        for (Player admin : Bukkit.getOnlinePlayers()) {
            if (admin.hasPermission(permission) && MindAI.getInstance().areAlertsEnabledFor(admin.getUniqueId())) {
                admin.sendMessage(finalMessage);
            }
        }
    }

    private static void punishPlayer(PlayerEntity entity, Runnable afterPunishment) {
        String command = MindAI.getInstance().getConfig().getString("punishment");
        if (command == null || command.isEmpty()) {
            afterPunishment.run();
            return;
        }

        String finalCommand = command.replace("%player%", entity.getName());

        Bukkit.getScheduler().runTask(MindAI.getInstance(), () -> {
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), finalCommand);
            afterPunishment.run();
        });
    }

    private static class LazyHolder {
        private static final OkHttpClient CLIENT = new OkHttpClient();
        private static final Moshi MOSHI = MoshiFactory.getInstance();
        private static final Type MAP_TYPE = Types.newParameterizedType(Map.class, String.class, Double.class);
        private static final JsonAdapter<Map<String, Double>> MAP_ADAPTER = MOSHI.adapter(MAP_TYPE);
    }
}
