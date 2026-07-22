package pp.sheero.permapiola.utilidad.commands;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import pp.sheero.permapiola.managers.LanguageManager;
import pp.sheero.permapiola.utils.ColorUtils;
import pp.sheero.permapiola.utils.ParticleUtils;

import java.util.ArrayList;
import java.util.List;

public class GmsCommand implements CommandExecutor, TabCompleter {
    private final LanguageManager lang;
    public GmsCommand(LanguageManager lang) { this.lang = lang; }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("permapiola.admin.gamemode.survival")) {
            sender.sendMessage(ColorUtils.format(lang.getMsg(sender, "commands.generic.no-permission")));
            return true;
        }

        Player targetPlayer;
        if (args.length >= 1) {
            targetPlayer = Bukkit.getPlayerExact(args[0]);
            if (targetPlayer == null) {
                sender.sendMessage(ColorUtils.format(lang.getMsg(sender, "commands.generic.player-offline")));
                return true;
            }
        } else {
            if (!(sender instanceof Player)) {
                sender.sendMessage(ColorUtils.format(lang.getMsg(sender, "commands.generic.console-only")));
                return true;
            }
            targetPlayer = (Player) sender;
        }

        if (targetPlayer.getGameMode() == GameMode.SURVIVAL) return true;

        targetPlayer.setGameMode(GameMode.SURVIVAL);
        ParticleUtils.spawnGamemodeParticles(targetPlayer, GameMode.SURVIVAL);

        if (targetPlayer.equals(sender)) {
            sender.sendMessage(ColorUtils.format(lang.getMsg(sender, "commands.gamemode.self").replace("%mode%", lang.getMsg(sender, "commands.gamemode.names.survival"))));
        } else {
            targetPlayer.sendMessage(ColorUtils.format(lang.getMsg(targetPlayer, "commands.gamemode.other-target").replace("%mode%", lang.getMsg(targetPlayer, "commands.gamemode.names.survival"))));
            sender.sendMessage(ColorUtils.format(lang.getMsg(sender, "commands.gamemode.other-sender").replace("%player%", targetPlayer.getName()).replace("%mode%", lang.getMsg(sender, "commands.gamemode.names.survival"))));
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();
        if (sender.hasPermission("permapiola.admin.gamemode.survival") && args.length == 1) {
            Bukkit.getOnlinePlayers().forEach(p -> {
                if (p.getName().toLowerCase().startsWith(args[0].toLowerCase())) completions.add(p.getName());
            });
        }
        return completions;
    }
}