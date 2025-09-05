package ru.Fronzter.MindAc.command.subcommands;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.util.StringUtil;
import ru.Fronzter.MindAc.MindAI;
import ru.Fronzter.MindAc.command.SubCommand;
import ru.Fronzter.MindAc.data.ViolationRecord;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

public class HistoryCommand extends SubCommand {

    private final SimpleDateFormat dateFormat = new SimpleDateFormat("dd.MM.yyyy HH:mm:ss");

    public HistoryCommand(MindAI plugin) {
        super(plugin);
    }

    @Override
    public String getName() { return "history"; }
    @Override
    public String getDescription() { return "Показать историю нарушений игрока"; }
    @Override
    public String getUsage() { return "/mindai history <игрок>"; }

    @Override
    public void execute(CommandSender sender, String[] args) {
        if (args.length < 1) {
            sender.sendMessage(plugin.getLocaleManager().getMessage("commands.history.usage"));
            return;
        }

        String playerName = args[0];
        @SuppressWarnings("deprecation")
        OfflinePlayer target = Bukkit.getOfflinePlayer(playerName);

        if (!target.hasPlayedBefore() && !target.isOnline()) {
            sender.sendMessage(plugin.getLocaleManager().getMessage("player-not-found").replace("%player%", playerName));
            return;
        }

        sender.sendMessage(plugin.getLocaleManager().getMessage("commands.history.loading").replace("%player%", target.getName()));
        plugin.getDatabaseService().getPlayerHistoryAsync(target.getUniqueId(), history -> {
            if (history.isEmpty()) {
                sender.sendMessage(plugin.getLocaleManager().getMessage("commands.history.no-history").replace("%player%", target.getName()));
                return;
            }
            sender.sendMessage(plugin.getLocaleManager().getMessage("commands.history.header").replace("%player%", target.getName()));
            for (ViolationRecord record : history) {
                String formattedDate = dateFormat.format(new Date(record.getTimestamp()));
                String formattedProb = String.format("%.2f%%", record.getProbability() * 100.0D);
                sender.sendMessage(
                        plugin.getLocaleManager().getMessage("commands.history.entry")
                                .replace("%date%", formattedDate)
                                .replace("%probability%", formattedProb)
                );
            }
        });
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, String[] args) {
        if (args.length == 1) {
            return Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName)
                    .filter(name -> StringUtil.startsWithIgnoreCase(name, args[0]))
                    .collect(Collectors.toList());
        }
        return super.onTabComplete(sender, args);
    }
}