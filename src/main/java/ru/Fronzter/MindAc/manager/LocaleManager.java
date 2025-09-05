package ru.Fronzter.MindAc.manager;

import org.bukkit.ChatColor;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import ru.Fronzter.MindAc.MindAI;
import java.io.File;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;

public class LocaleManager {

    private final MindAI plugin;
    private FileConfiguration langConfig;
    private String langCode;

    public LocaleManager(MindAI plugin) {
        this.plugin = plugin;
        loadMessages();
    }

    public void loadMessages() {
        this.langCode = plugin.getConfig().getString("language", "en").toLowerCase();
        File langFile = new File(plugin.getDataFolder(), "lang_" + langCode + ".yml");

        if (!langFile.exists()) {
            try {
                plugin.saveResource("lang_" + langCode + ".yml", false);
            } catch (IllegalArgumentException e) {
                langCode = "en";
                langFile = new File(plugin.getDataFolder(), "lang_en.yml");
                if (!langFile.exists()) {
                    plugin.saveResource("lang_en.yml", false);
                }
            }
        }

        langConfig = YamlConfiguration.loadConfiguration(langFile);

        try (Reader defConfigStream = new InputStreamReader(plugin.getResource("lang_" + langCode + ".yml"), StandardCharsets.UTF_8)) {
            if (defConfigStream != null) {
                YamlConfiguration defConfig = YamlConfiguration.loadConfiguration(defConfigStream);
                langConfig.setDefaults(defConfig);
            }
        } catch (Exception ignored) {}
    }

    public String getMessage(String path) {
        String message = langConfig.getString(path);
        if (message == null) {
            return ChatColor.RED + "Error: Message not found for key '" + path + "'";
        }
        return ChatColor.translateAlternateColorCodes('&', message);
    }
}
