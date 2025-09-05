package ru.Fronzter.MindAc.mitigation;

import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import ru.Fronzter.MindAc.MindAI;
import ru.Fronzter.MindAc.mitigation.types.*;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

public class MitigationManager {

    private final MindAI plugin;
    private final boolean isEnabled;
    private final String activationMode;
    private final double vlThreshold;
    private final double scalingMinVl;
    private final double scalingMaxVl;
    private final int streakFlagsNeeded;
    private final long durationMillis;

    private final List<PacketReceiveMitigation> packetMitigations = new ArrayList<>();
    private final List<DamageMitigation> damageMitigations = new ArrayList<>();
    private final List<MoveMitigation> moveMitigations = new ArrayList<>();

    public MitigationManager(MindAI plugin) {
        this.plugin = plugin;
        this.isEnabled = plugin.getConfig().getBoolean("mitigations.enabled", false);

        this.activationMode = plugin.getConfig().getString("mitigations.activation.mode", "SCALING_CHANCE").toUpperCase();
        this.vlThreshold = plugin.getConfig().getDouble("mitigations.activation.threshold.vl", 4.0);
        this.scalingMinVl = plugin.getConfig().getDouble("mitigations.activation.scaling-chance.min-vl", 2.0);
        this.scalingMaxVl = plugin.getConfig().getDouble("mitigations.activation.scaling-chance.max-vl", 8.0);
        this.streakFlagsNeeded = plugin.getConfig().getInt("mitigations.activation.flag-streak.flags-needed", 3);

        long durationSeconds = plugin.getConfig().getLong("mitigations.duration-seconds", 120);
        this.durationMillis = (durationSeconds > 0) ? TimeUnit.SECONDS.toMillis(durationSeconds) : -1;

        loadMitigations();
    }

    private void loadMitigations() {
        if (!isEnabled) return;

        if (plugin.getConfig().getBoolean("mitigations.types.ghost-hits.enabled", false)) {
            packetMitigations.add(new GhostHits(plugin));
        }
        if (plugin.getConfig().getBoolean("mitigations.types.aim-punch.enabled", false)) {
            packetMitigations.add(new AimPunch(plugin));
        }
        if (plugin.getConfig().getBoolean("mitigations.types.damage-reduction.enabled", false)) {
            damageMitigations.add(new DamageReduction(plugin));
        }
        if (plugin.getConfig().getBoolean("mitigations.types.jump-cancel.enabled", false)) {
            moveMitigations.add(new JumpCancel(plugin));
        }
    }

    public void handlePacketAttack(Player player, PacketReceiveEvent event) {
        if (shouldMitigate(player)) {
            for (PacketReceiveMitigation mitigation : packetMitigations) {
                mitigation.onPacket(player, event);
            }
        }
    }

    public void handleDamage(Player attacker, EntityDamageByEntityEvent event) {
        if (shouldMitigate(attacker)) {
            for (DamageMitigation mitigation : damageMitigations) {
                mitigation.onDamage(attacker, event);
            }
        }
    }

    public void handleMove(Player player, PlayerMoveEvent event) {
        if (shouldMitigate(player)) {
            for (MoveMitigation mitigation : moveMitigations) {
                mitigation.onMove(player, event);
            }
        }
    }

    private boolean shouldMitigate(Player player) {
        if (!isEnabled || player == null) return false;

        boolean meetsActivationCriteria = checkActivationCriteria(player);
        if (!meetsActivationCriteria) {
            return false;
        }

        if (durationMillis != -1) {
            long lastFlagTime = plugin.getViolationManager().getLastFlagTimestamp(player.getUniqueId());
            if (System.currentTimeMillis() - lastFlagTime > durationMillis) {
                return false;
            }
        }

        return true;
    }

    private boolean checkActivationCriteria(Player player) {
        UUID uuid = player.getUniqueId();

        switch (activationMode) {
            case "VL_THRESHOLD":
                return plugin.getViolationManager().getViolationLevel(uuid) >= vlThreshold;

            case "SCALING_CHANCE":
                double currentVl = plugin.getViolationManager().getViolationLevel(uuid);
                if (currentVl < scalingMinVl) return false;
                if (currentVl >= scalingMaxVl) return true;

                double chance = (currentVl - scalingMinVl) / (scalingMaxVl - scalingMinVl);
                return ThreadLocalRandom.current().nextDouble() < chance;

            case "FLAG_STREAK":
                return plugin.getViolationManager().getRecentFlagsCount(uuid) >= streakFlagsNeeded;

            default:
                return false;
        }
    }
}
