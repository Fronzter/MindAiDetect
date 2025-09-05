package ru.Fronzter.MindAc.mitigation.types;

import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import org.bukkit.entity.Player;

public interface PacketReceiveMitigation {

    void onPacket(Player player, PacketReceiveEvent event);
}