package pp.sheero.permapiola.utilidad.commands;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import pp.sheero.permapiola.PermaPiola;
import pp.sheero.permapiola.managers.LanguageManager;
import pp.sheero.permapiola.utils.ColorUtils;
import pp.sheero.permapiola.utils.DeathInventoryManager;
import pp.sheero.permapiola.utils.DeathStateManager;

import java.util.ArrayList;
import java.util.List;

public class ReviveCommand implements CommandExecutor, TabCompleter {

    private final PermaPiola plugin;
    private final LanguageManager lang;

    public ReviveCommand(PermaPiola plugin, LanguageManager lang) {
        this.plugin = plugin;
        this.lang = lang;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("permapiola.admin.revive")) {
            sender.sendMessage(ColorUtils.format(lang.getMsg(sender, "commands.generic.no-permission")));
            return true;
        }

        if (args.length == 0) {
            sender.sendMessage(ColorUtils.format(lang.getMsg(sender, "commands.revive.usage")));
            return true;
        }

        Player target = Bukkit.getPlayerExact(args[0]);
        if (target == null) {
            sender.sendMessage(ColorUtils.format(lang.getMsg(sender, "commands.generic.player-offline")));
            return true;
        }

        if (!DeathStateManager.isDead(target.getUniqueId())) {
            sender.sendMessage(ColorUtils.format(lang.getMsg(sender, "commands.revive.not-dead")));
            return true;
        }

        Location spawnLoc = Bukkit.getWorlds().get(0).getSpawnLocation();
        target.teleport(spawnLoc);

        target.setGameMode(GameMode.SURVIVAL);

        if (DeathInventoryManager.hasDeathInventory(target)) {
            DeathInventoryManager.restoreInventory(target);
        }

        DeathStateManager.setDead(target.getUniqueId(), false);

        plugin.getDiscordManager().deleteDeathMessage(target.getUniqueId());
        DeathStateManager.decrementTotalDeaths();

        long durationHours = plugin.getConfig().getLong("hurricane.duration-hours", 1);
        long durationSeconds = durationHours * 3600;
        plugin.getHurricaneManager().removeTime(durationSeconds);

        sender.sendMessage(ColorUtils.format(lang.getMsg(sender, "commands.revive.success").replace("%player%", target.getName())));

        String alertMsg = lang.getMsg(sender, "commands.revive.staff-alert")
                .replace("%admin%", sender.getName())
                .replace("%player%", target.getName());

        for (Player staff : Bukkit.getOnlinePlayers()) {
            if (staff.hasPermission("permapiola.admin.staffchat")) {
                staff.sendMessage(ColorUtils.format(alertMsg));
            }
        }

        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();
        if (sender.hasPermission("permapiola.admin.revive") && args.length == 1) {
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (p.getName().toLowerCase().startsWith(args[0].toLowerCase())) {
                    completions.add(p.getName());
                }
            }
        }
        return completions;
    }
}