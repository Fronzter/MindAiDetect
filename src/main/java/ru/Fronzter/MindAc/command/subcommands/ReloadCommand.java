package ru.Fronzter.MindAc.command.subcommands;

import org.bukkit.command.CommandSender;
import ru.Fronzter.MindAc.MindAI;
import ru.Fronzter.MindAc.command.SubCommand;

public class ReloadCommand extends SubCommand {

    public ReloadCommand(MindAI plugin) {
        super(plugin);
    }

    @Override
    public String getName() { return "reload"; }
    @Override
    public String getDescription() { return "Перезагрузить конфигурацию плагина"; }
    @Override
    public String getUsage() { return "/mindai reload"; }

    @Override
    public void execute(CommandSender sender, String[] args) {
        plugin.reloadPluginConfig();
        sender.sendMessage(plugin.getLocaleManager().getMessage("commands.reload.success"));
    }
}