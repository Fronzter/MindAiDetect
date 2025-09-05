package ru.Fronzter.MindAc.mitigation.types;

import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerMoveEvent;

public interface MoveMitigation {

    void onMove(Player player, PlayerMoveEvent event);
}