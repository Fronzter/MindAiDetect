package ru.Fronzter.MindAc.command.subcommands;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import ru.Fronzter.MindAc.MindAI;
import ru.Fronzter.MindAc.command.SubCommand;

public class AlertsCommand extends SubCommand {

    public AlertsCommand(MindAI plugin) {
        super(plugin);
    }

    @Override
    public String getName() { return "alerts"; }
    @Override
    public String getDescription() { return "Включить/выключить оповещения для себя"; }
    @Override
    public String getUsage() { return "/mindai alerts"; }

    @Override
    public void execute(CommandSender sender, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(plugin.getLocaleManager().getMessage("player-only-command"));
            return;
        }
        Player player = (Player) sender;
        boolean newState = plugin.toggleAlerts(player.getUniqueId());
        if (newState) {
            player.sendMessage(plugin.getLocaleManager().getMessage("alerts.toggled-on"));
        } else {
            player.sendMessage(plugin.getLocaleManager().getMessage("alerts.toggled-off"));
        }
    }
}