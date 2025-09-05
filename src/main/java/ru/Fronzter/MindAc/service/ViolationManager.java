package ru.Fronzter.MindAc.service;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import ru.Fronzter.MindAc.MindAI;
import ru.Fronzter.MindAc.entity.PlayerEntity;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

public class ViolationManager {

    private final MindAI plugin;
    private final Map<UUID, Double> violationLevels = new ConcurrentHashMap<>();
    private final Map<UUID, Double> lastPunishmentThreshold = new ConcurrentHashMap<>();
    private final Map<UUID, List<Long>> recentFlagTimestamps = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lastFlagTimestamp = new ConcurrentHashMap<>();

    private final long flagStreakWindowMillis;
    private final boolean enabled;
    private final double vlIncrement;
    private final double violationThreshold;
    private final double decayAmount;
    private final boolean resetVlAfterPunishment;
    private final List<PunishmentStep> punishmentSteps = new ArrayList<>();

    public ViolationManager(MindAI plugin) {
        this.plugin = plugin;
        this.enabled = plugin.getConfig().getBoolean("ml-check.enabled", true);
        this.vlIncrement = plugin.getConfig().getDouble("ml-check.vl-increment", 1.0);
        this.violationThreshold = plugin.getConfig().getDouble("ml-check.violation-threshold", 7.0);

        this.flagStreakWindowMillis = TimeUnit.SECONDS.toMillis(
                plugin.getConfig().getLong("mitigations.activation.flag-streak.time-window-seconds", 20)
        );

        double windowSeconds = plugin.getConfig().getDouble("ml-check.window", 600);
        if (windowSeconds > 0) {
            double checksPerWindow = windowSeconds / 60.0;
            this.decayAmount = this.vlIncrement / checksPerWindow;
        } else {
            this.decayAmount = 0;
        }

        this.resetVlAfterPunishment = plugin.getConfig().getBoolean("progressive-punishment.reset-vl-after-punishment", true);
        ConfigurationSection stepsSection = plugin.getConfig().getConfigurationSection("progressive-punishment.steps");
        if (stepsSection != null) {
            for (String key : stepsSection.getKeys(false)) {
                double threshold = stepsSection.getDouble(key + ".threshold");
                String command = stepsSection.getString(key + ".command");
                if (command != null && !command.isEmpty()) {
                    punishmentSteps.add(new PunishmentStep(threshold, command));
                }
            }
            punishmentSteps.sort(Comparator.comparingDouble(s -> s.threshold));
        }

        if (enabled && this.decayAmount > 0) {
            startDecayTask();
        }
    }

    public void handleViolation(PlayerEntity entity, double probability) {
        if (!enabled) return;
        UUID uuid = entity.getUuid();
        trackFlagTimestamp(uuid);
        updateLastFlagTimestamp(uuid);

        double currentVL = violationLevels.getOrDefault(uuid, 0.0);
        double newVL = currentVL + vlIncrement;
        violationLevels.put(uuid, newVL);

        plugin.getDatabaseService().logViolationAsync(uuid, entity.getName(), probability);
        sendAlert(entity, probability, newVL);
        checkForPunishment(entity, newVL);
    }

    private void trackFlagTimestamp(UUID uuid) {
        List<Long> timestamps = recentFlagTimestamps.computeIfAbsent(uuid, k -> new LinkedList<>());
        long currentTime = System.currentTimeMillis();
        timestamps.add(currentTime);
        timestamps.removeIf(ts -> (currentTime - ts) > flagStreakWindowMillis);
    }

    private void updateLastFlagTimestamp(UUID uuid) {
        lastFlagTimestamp.put(uuid, System.currentTimeMillis());
    }

    public long getLastFlagTimestamp(UUID uuid) {
        return lastFlagTimestamp.getOrDefault(uuid, 0L);
    }

    public int getRecentFlagsCount(UUID uuid) {
        return recentFlagTimestamps.getOrDefault(uuid, Collections.emptyList()).size();
    }

    public double getViolationLevel(UUID uuid) {
        return violationLevels.getOrDefault(uuid, 0.0);
    }

    private void checkForPunishment(PlayerEntity entity, double currentVl) {
        String mode = plugin.getConfig().getString("auto-punishment.mode", "INSTANT").toUpperCase();
        switch (mode) {
            case "INSTANT":
            case "ANIMATED":
                if (currentVl >= violationThreshold) {
                    executePunishment(entity, mode);
                    violationLevels.remove(entity.getUuid());
                }
                break;
            case "PROGRESSIVE":
                executeProgressivePunishment(entity, currentVl);
                break;
            case "ALERTS_ONLY":
            default:
                break;
        }
    }

    private void executePunishment(PlayerEntity entity, String mode) {
        String commandToExecute = null;
        if ("INSTANT".equals(mode)) {
            commandToExecute = plugin.getConfig().getString("auto-punishment.command");
        } else if ("ANIMATED".equals(mode)) {
            commandToExecute = "mindai punish " + entity.getName();
        }
        if (commandToExecute == null || commandToExecute.isEmpty()) {
            return;
        }
        String finalCommand = commandToExecute.replace("%player%", entity.getName());
        Bukkit.getScheduler().runTask(plugin, () -> Bukkit.dispatchCommand(Bukkit.getConsoleSender(), finalCommand));
    }

    private void executeProgressivePunishment(PlayerEntity entity, double currentVl) {
        double lastPunishedAt = lastPunishmentThreshold.getOrDefault(entity.getUuid(), 0.0);
        PunishmentStep stepToApply = null;
        for (PunishmentStep step : punishmentSteps) {
            if (currentVl >= step.threshold && step.threshold > lastPunishedAt) {
                stepToApply = step;
            }
        }
        if (stepToApply != null) {
            String command = stepToApply.command.replace("%player%", entity.getName());
            Bukkit.getScheduler().runTask(plugin, () -> Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command));
            lastPunishmentThreshold.put(entity.getUuid(), stepToApply.threshold);
            if (resetVlAfterPunishment) {
                violationLevels.remove(entity.getUuid());
            }
        }
    }

    private void startDecayTask() {
        long decayIntervalTicks = 20L * 60;
        Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, () -> {
            for (UUID uuid : violationLevels.keySet()) {
                double currentVL = violationLevels.get(uuid);
                double newVL = currentVL - decayAmount;

                if (newVL <= 0) {
                    violationLevels.remove(uuid);
                    lastPunishmentThreshold.remove(uuid);
                    recentFlagTimestamps.remove(uuid);
                    lastFlagTimestamp.remove(uuid);
                } else {
                    violationLevels.put(uuid, newVL);
                }
            }
        }, decayIntervalTicks, decayIntervalTicks);
    }

    private void sendAlert(PlayerEntity entity, double probability, double currentVl) {
        if (!plugin.getConfig().getBoolean("alerts.enabled", true)) return;
        String message = plugin.getLocaleManager().getMessage("alerts.message");
        if (message.isEmpty()) return;

        String formattedProb = String.format("%.2f%%", probability * 100.0D);
        String mode = plugin.getConfig().getString("auto-punishment.mode", "INSTANT").toUpperCase();
        String vlString;
        if ("PROGRESSIVE".equals(mode)) {
            vlString = String.format("%.1f", currentVl);
        } else {
            vlString = String.format("%.1f/%.0f", currentVl, violationThreshold);
        }
        String finalMessage = ChatColor.translateAlternateColorCodes('&',
                message.replace("%player%", entity.getName())
                        .replace("%probability%", formattedProb)
                        .replace("%vl%", vlString));
        String permission = plugin.getConfig().getString("alerts.permission", "mindai.alerts");
        for (Player admin : Bukkit.getOnlinePlayers()) {
            if (admin.hasPermission(permission) && plugin.areAlertsEnabledFor(admin.getUniqueId())) {
                admin.sendMessage(finalMessage);
            }
        }
    }

    public void clearPlayerData(UUID uuid) {
        violationLevels.remove(uuid);
        lastPunishmentThreshold.remove(uuid);
        recentFlagTimestamps.remove(uuid);
        lastFlagTimestamp.remove(uuid);
    }

    private static class PunishmentStep {
        private final double threshold;
        private final String command;

        public PunishmentStep(double threshold, String command) {
            this.threshold = threshold;
            this.command = command;
        }
    }
}
