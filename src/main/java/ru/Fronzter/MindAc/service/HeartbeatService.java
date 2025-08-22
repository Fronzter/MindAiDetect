package ru.Fronzter.MindAc.service;

import com.squareup.moshi.JsonAdapter;
import com.squareup.moshi.Moshi;
import com.squareup.moshi.Types;
import okhttp3.*;
import org.bukkit.Bukkit;
import ru.Fronzter.MindAc.MindAI;

import java.io.IOException;
import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class HeartbeatService {

    public static void sendHeartbeat() {
        MindAI plugin = MindAI.getInstance();
        if (!MindAI.isLicenseActive()) {
            return;
        }

        String heartbeatUrl = plugin.getConfig().getString("api-endpoints.license-server", "") + "/heartbeat";
        if (heartbeatUrl.isEmpty() || heartbeatUrl.equals("/heartbeat")) {
            return;
        }

        String apiKey = plugin.getConfig().getString("api-key");
        if (apiKey == null || apiKey.isEmpty() || apiKey.equals("CHANGE-ME-TO-YOUR-API-KEY")) {
            return;
        }

        int onlinePlayers = Bukkit.getOnlinePlayers().size();

        Map<String, Object> data = new HashMap<>();
        data.put("license_key", apiKey);
        data.put("online_players", onlinePlayers);

        String json = LazyHolder.JSON_ADAPTER.toJson(data);
        RequestBody body = RequestBody.create(json, MediaType.parse("application/json; charset=utf-8"));
        Request request = new Request.Builder().url(heartbeatUrl).post(body).build();

        try {
            Response response = LazyHolder.CLIENT.newCall(request).execute();
            if (!response.isSuccessful()) {
            }
            response.close();
        } catch (IOException e) {
        }
    }

    private static class LazyHolder {
        private static final OkHttpClient CLIENT = new OkHttpClient.Builder()
                .connectTimeout(5, TimeUnit.SECONDS)
                .readTimeout(5, TimeUnit.SECONDS)
                .build();
        private static final Moshi MOSHI = new Moshi.Builder().build();
        private static final Type MAP_STRING_OBJECT_TYPE = Types.newParameterizedType(Map.class, String.class, Object.class);
        private static final JsonAdapter<Map<String, Object>> JSON_ADAPTER = MOSHI.adapter(MAP_STRING_OBJECT_TYPE);
    }
}