package ru.Fronzter.MindAc.mitigation.types;

import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import ru.Fronzter.MindAc.MindAI;

public class DamageReduction implements DamageMitigation {
    private final double multiplier;

    public DamageReduction(MindAI plugin) {
        this.multiplier = plugin.getConfig().getDouble("mitigations.types.damage-reduction.multiplier", 0.7);
    }

    @Override
    public void onDamage(Player attacker, EntityDamageByEntityEvent event) {
        double newDamage = event.getDamage() * multiplier;
        event.setDamage(newDamage);
    }
}