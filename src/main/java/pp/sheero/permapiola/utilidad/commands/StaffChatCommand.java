package pp.sheero.permapiola.utilidad.commands;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import pp.sheero.permapiola.managers.ChatManager;
import pp.sheero.permapiola.managers.EmoteManager;
import pp.sheero.permapiola.managers.LanguageManager;
import pp.sheero.permapiola.utils.ColorUtils;

import java.util.ArrayList;
import java.util.List;

public class StaffChatCommand implements CommandExecutor, TabCompleter {

    private final ChatManager chatManager;
    private final LanguageManager lang;
    private final EmoteManager emoteManager;

    public StaffChatCommand(ChatManager chatManager, LanguageManager lang, EmoteManager emoteManager) {
        this.chatManager = chatManager;
        this.lang = lang;
        this.emoteManager = emoteManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(ColorUtils.format(lang.getMsg(sender, "commands.generic.console-only")));
            return true;
        }

        Player pSender = (Player) sender;

        if (!pSender.hasPermission("permapiola.admin.staffchat")) {
            pSender.sendMessage(ColorUtils.format(lang.getMsg(sender, "commands.generic.no-permission")));
            return true;
        }

        if (args.length == 0) {
            pSender.sendMessage(ColorUtils.format(lang.getMsg(sender, "commands.staffchat.usage")));
            return true;
        }

        String message = String.join(" ", args);
        message = emoteManager.translateEmotes(pSender, message);

        chatManager.sendStaffMessage(pSender, message);
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        return new ArrayList<>();
    }
}