package ru.Fronzter.MindAc.listener;

import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import ru.Fronzter.MindAc.MindAI;
import ru.Fronzter.MindAc.entity.PlayerEntity;
import ru.Fronzter.MindAc.registry.PlayerRegistry;

public class CombatListener implements Listener {

    private final long combatTimerTicks;

    public CombatListener(MindAI plugin) {
        this.combatTimerTicks = plugin.getConfig().getLong("ml-check.combat-timer-seconds", 10) * 20;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerDamage(EntityDamageByEntityEvent event) {
        Entity damager = event.getDamager();
        Entity victim = event.getEntity();

        if (damager instanceof Player && victim instanceof Player) {
            PlayerEntity damagerEntity = PlayerRegistry.getPlayer(damager.getUniqueId());
            PlayerEntity victimEntity = PlayerRegistry.getPlayer(victim.getUniqueId());

            if (damagerEntity != null) {
                damagerEntity.tagCombat(combatTimerTicks);
            }
            if (victimEntity != null) {
                victimEntity.tagCombat(combatTimerTicks);
            }
        }
    }
}