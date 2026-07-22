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
import java.util.Arrays;
import java.util.List;

public class GmCommand implements CommandExecutor, TabCompleter {

    private final LanguageManager lang;
    public GmCommand(LanguageManager lang) { this.lang = lang; }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("permapiola.admin.gamemode")) {
            sender.sendMessage(ColorUtils.format(lang.getMsg(sender, "commands.generic.no-permission")));
            return true;
        }

        if (args.length == 0) {
            sender.sendMessage(ColorUtils.format(lang.getMsg(sender, "commands.gamemode.usage-1")));
            sender.sendMessage(ColorUtils.format(lang.getMsg(sender, "commands.gamemode.usage-2")));
            return true;
        }

        GameMode targetMode; String modeKey;
        switch (args[0]) {
            case "0": targetMode = GameMode.SURVIVAL; modeKey = "survival"; break;
            case "1": targetMode = GameMode.CREATIVE; modeKey = "creative"; break;
            case "2": targetMode = GameMode.ADVENTURE; modeKey = "adventure"; break;
            case "3": targetMode = GameMode.SPECTATOR; modeKey = "spectator"; break;
            default:
                sender.sendMessage(ColorUtils.format(lang.getMsg(sender, "commands.gamemode.invalid-number")));
                return true;
        }

        Player targetPlayer;
        if (args.length >= 2) {
            targetPlayer = Bukkit.getPlayerExact(args[1]);
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

        if (targetPlayer.getGameMode() == targetMode) return true;

        targetPlayer.setGameMode(targetMode);
        ParticleUtils.spawnGamemodeParticles(targetPlayer, targetMode);

        if (targetPlayer.equals(sender)) {
            String modeName = lang.getMsg(sender, "commands.gamemode.names." + modeKey);
            sender.sendMessage(ColorUtils.format(lang.getMsg(sender, "commands.gamemode.self").replace("%mode%", modeName)));
        } else {
            String targetModeName = lang.getMsg(targetPlayer, "commands.gamemode.names." + modeKey);
            String senderModeName = lang.getMsg(sender, "commands.gamemode.names." + modeKey);
            targetPlayer.sendMessage(ColorUtils.format(lang.getMsg(targetPlayer, "commands.gamemode.other-target").replace("%mode%", targetModeName)));
            sender.sendMessage(ColorUtils.format(lang.getMsg(sender, "commands.gamemode.other-sender").replace("%player%", targetPlayer.getName()).replace("%mode%", senderModeName)));
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();
        if (!sender.hasPermission("permapiola.admin.gamemode")) return completions;

        if (args.length == 1) {
            for (String mode : Arrays.asList("0", "1", "2", "3")) {
                if (mode.startsWith(args[0])) completions.add(mode);
            }
        } else if (args.length == 2) {
            Bukkit.getOnlinePlayers().forEach(p -> {
                if (p.getName().toLowerCase().startsWith(args[1].toLowerCase())) completions.add(p.getName());
            });
        }
        return completions;
    }
}