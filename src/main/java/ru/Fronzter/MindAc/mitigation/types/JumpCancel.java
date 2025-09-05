package ru.Fronzter.MindAc.mitigation.types;

import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.util.Vector;
import ru.Fronzter.MindAc.MindAI;
import java.util.concurrent.ThreadLocalRandom;

public class JumpCancel implements MoveMitigation {
    private final double chance;
    private final double downwardForce;

    public JumpCancel(MindAI plugin) {
        this.chance = plugin.getConfig().getDouble("mitigations.types.jump-cancel.chance", 0.8);
        this.downwardForce = plugin.getConfig().getDouble("mitigations.types.jump-cancel.downward-force", -0.2);
    }

    @Override
    public void onMove(Player player, PlayerMoveEvent event) {
        if (event.getTo().getY() > event.getFrom().getY()) {
            if (player.isOnGround() || player.getLocation().subtract(0, 0.1, 0).getBlock().getType().isSolid()) {
                if (ThreadLocalRandom.current().nextDouble() < chance) {
                    player.setVelocity(player.getVelocity().add(new Vector(0, downwardForce, 0)));
                }
            }
        }
    }
}