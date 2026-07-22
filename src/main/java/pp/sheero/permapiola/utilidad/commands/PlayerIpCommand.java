package pp.sheero.permapiola.utilidad.commands;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import pp.sheero.permapiola.managers.LanguageManager;
import pp.sheero.permapiola.utils.ColorUtils;

import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.List;

public class PlayerIpCommand implements CommandExecutor, TabCompleter {

    private final LanguageManager lang;

    public PlayerIpCommand(LanguageManager lang) {
        this.lang = lang;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {

        if (!sender.hasPermission("permapiola.admin.playerip")) {
            sender.sendMessage(ColorUtils.format(lang.getMsg(sender, "commands.generic.no-permission")));
            return true;
        }

        if (args.length == 0) {
            sender.sendMessage(ColorUtils.format(lang.getMsg(sender, "commands.playerip.usage")));
            return true;
        }

        Player target = Bukkit.getPlayerExact(args[0]);
        if (target == null) {
            sender.sendMessage(ColorUtils.format(lang.getMsg(sender, "commands.generic.player-offline")));
            return true;
        }

        InetSocketAddress address = target.getAddress();
        if (address != null && address.getAddress() != null) {
            String cleanIp = address.getAddress().getHostAddress();
            String successMsg = lang.getMsg(sender, "commands.playerip.success")
                    .replace("%player%", target.getName())
                    .replace("%ip%", cleanIp);
            sender.sendMessage(ColorUtils.format(successMsg));
        } else {
            sender.sendMessage(ColorUtils.format(lang.getMsg(sender, "commands.playerip.error")));
        }

        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();
        if (!sender.hasPermission("permapiola.admin.playerip")) return completions;

        if (args.length == 1) {
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (p.getName().toLowerCase().startsWith(args[0].toLowerCase())) {
                    completions.add(p.getName());
                }
            }
        }
        return completions;
    }
}