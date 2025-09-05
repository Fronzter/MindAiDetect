package ru.Fronzter.MindAc.command.subcommands;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.util.StringUtil;
import ru.Fronzter.MindAc.MindAI;
import ru.Fronzter.MindAc.command.SubCommand;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

public class StatsCommand extends SubCommand {

    private final SimpleDateFormat dateFormat = new SimpleDateFormat("dd.MM.yyyy HH:mm:ss");

    public StatsCommand(MindAI plugin) {
        super(plugin);
    }

    @Override
    public String getName() { return "stats"; }
    @Override
    public String getDescription() { return "Показать статистику нарушений игрока"; }
    @Override
    public String getUsage() { return "/mindai stats <игрок>"; }

    @Override
    public void execute(CommandSender sender, String[] args) {
        if (args.length < 1) {
            sender.sendMessage(plugin.getLocaleManager().getMessage("commands.stats.usage"));
            return;
        }
        String playerName = args[0];
        @SuppressWarnings("deprecation")
        OfflinePlayer target = Bukkit.getOfflinePlayer(playerName);
        if (!target.hasPlayedBefore() && !target.isOnline()) {
            sender.sendMessage(plugin.getLocaleManager().getMessage("player-not-found").replace("%player%", playerName));
            return;
        }

        sender.sendMessage(plugin.getLocaleManager().getMessage("commands.stats.loading").replace("%player%", target.getName()));
        plugin.getDatabaseService().getPlayerStatsAsync(target.getUniqueId(), stats -> {
            sender.sendMessage(plugin.getLocaleManager().getMessage("commands.stats.header").replace("%player%", target.getName()));
            sender.sendMessage(plugin.getLocaleManager().getMessage("commands.stats.total-violations").replace("%count%", String.valueOf(stats.getTotalViolations())));
            if (stats.getTotalViolations() > 0) {
                sender.sendMessage(plugin.getLocaleManager().getMessage("commands.stats.average-probability").replace("%avg_prob%", String.format("%.2f", stats.getAverageProbability() * 100)));
                sender.sendMessage(plugin.getLocaleManager().getMessage("commands.stats.last-violation").replace("%date%", dateFormat.format(new Date(stats.getLastViolationTimestamp()))));
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