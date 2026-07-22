package pp.sheero.permapiola.utilidad.commands;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import pp.sheero.permapiola.managers.EmoteManager;
import pp.sheero.permapiola.managers.LanguageManager;
import pp.sheero.permapiola.utils.ColorUtils;

import java.util.ArrayList;
import java.util.List;

public class BroadcastCommand implements CommandExecutor, TabCompleter {

    private final LanguageManager lang;
    private final EmoteManager emoteManager;

    public BroadcastCommand(LanguageManager lang, EmoteManager emoteManager) {
        this.lang = lang;
        this.emoteManager = emoteManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("permapiola.admin.broadcast")) {
            sender.sendMessage(ColorUtils.format(lang.getMsg(sender, "commands.generic.no-permission")));
            return true;
        }

        if (args.length == 0) {
            sender.sendMessage(ColorUtils.format(lang.getMsg(sender, "commands.broadcast.usage")));
            return true;
        }

        String message = String.join(" ", args);

        if (sender instanceof org.bukkit.entity.Player) {
            org.bukkit.entity.Player pSender = (org.bukkit.entity.Player) sender;
            message = emoteManager.translateEmotes(pSender, message);
        }

        String broadcastFormat = lang.getMsg(sender, "commands.broadcast.format");
        String fullMessage = ColorUtils.format(broadcastFormat.replace("%message%", message));

        Bukkit.broadcastMessage(fullMessage);
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        return new ArrayList<>();
    }
}