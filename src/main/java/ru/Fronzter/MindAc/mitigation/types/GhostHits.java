package ru.Fronzter.MindAc.mitigation.types;

import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import org.bukkit.entity.Player;
import ru.Fronzter.MindAc.MindAI;
import java.util.concurrent.ThreadLocalRandom;

public class GhostHits implements PacketReceiveMitigation {

    private final double chance;

    public GhostHits(MindAI plugin) {
        this.chance = plugin.getConfig().getDouble("mitigations.types.ghost-hits.chance", 0.25);
    }

    @Override
    public void onPacket(Player player, PacketReceiveEvent event) {
        if (ThreadLocalRandom.current().nextDouble() < chance) {
            event.setCancelled(true);
        }
    }
}