package pp.sheero.permapiola.utilidad.commands;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import pp.sheero.permapiola.managers.ChatManager;
import pp.sheero.permapiola.managers.LanguageManager;
import pp.sheero.permapiola.teams.TeamManager;
import pp.sheero.permapiola.utils.ChatChannel;
import pp.sheero.permapiola.utils.ColorUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class ChatCommand implements CommandExecutor, TabCompleter {

    private final ChatManager chatManager;
    private final LanguageManager lang;

    private static final List<String> BASE_ARGS = Arrays.asList("all", "team");

    public ChatCommand(ChatManager chatManager, LanguageManager lang) {
        this.chatManager = chatManager;
        this.lang = lang;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(ColorUtils.format(lang.getMsg(sender, "commands.generic.console-only")));
            return true;
        }
        Player pSender = (Player) sender;

        boolean isDead = pp.sheero.permapiola.utils.DeathStateManager.isDead(pSender.getUniqueId());
        boolean isStaff = pSender.hasPermission("permapiola.admin") || pSender.hasPermission("permapiola.staff");

        if (isDead && !isStaff) {
            pSender.sendMessage(ColorUtils.format(lang.getMsg(pSender, "commands.generic.no-permission")));
            return true;
        }

        if (args.length == 0) {
            pSender.sendMessage(ColorUtils.format(lang.getMsg(sender, "commands.chat.usage")));
            return true;
        }

        String arg = args[0].toLowerCase();
        ChatChannel currentChannel = chatManager.getChannel(pSender);

        if (arg.equals("a") || arg.equals("all")) {
            if (currentChannel == ChatChannel.ALL) {
                pSender.sendMessage(ColorUtils.format(lang.getMsg(sender, "commands.chat.already-in-channel")));
                return true;
            }

            chatManager.setChannel(pSender, ChatChannel.ALL);
            pSender.sendMessage(ColorUtils.format(lang.getMsg(sender, "commands.chat.changed-all")));
            return true;
        }
        else if (arg.equals("s") || arg.equals("staff")) {
            if (!pSender.hasPermission("permapiola.admin.staffchat")) {
                pSender.sendMessage(ColorUtils.format(lang.getMsg(sender, "commands.generic.no-permission")));
                return true;
            }

            if (currentChannel == ChatChannel.STAFF) {
                pSender.sendMessage(ColorUtils.format(lang.getMsg(sender, "commands.chat.already-in-channel")));
                return true;
            }

            chatManager.setChannel(pSender, ChatChannel.STAFF);
            pSender.sendMessage(ColorUtils.format(lang.getMsg(sender, "commands.chat.changed-staff")));
            return true;
        }
        else if (arg.equals("t") || arg.equals("team")) {
            if (currentChannel == ChatChannel.TEAM) {
                pSender.sendMessage(ColorUtils.format(lang.getMsg(sender, "commands.chat.already-in-channel")));
                return true;
            }

            if (TeamManager.hasTeam(pSender)) {
                chatManager.setChannel(pSender, ChatChannel.TEAM);
                pSender.sendMessage(ColorUtils.format(lang.getMsg(sender, "commands.chat.changed-team")));
            } else {
                pSender.sendMessage(ColorUtils.format(lang.getMsg(sender, "commands.chat.no-team")));
            }
            return true;
        }
        else if (arg.equals("spec")) {
            if (!pSender.hasPermission("permapiola.admin.spec")) {
                pSender.sendMessage(ColorUtils.format(lang.getMsg(sender, "commands.generic.no-permission")));
                return true;
            }

            if (currentChannel == ChatChannel.SPEC) {
                pSender.sendMessage(ColorUtils.format(lang.getMsg(sender, "commands.chat.already-in-channel")));
                return true;
            }

            chatManager.setChannel(pSender, ChatChannel.SPEC);
            pSender.sendMessage(ColorUtils.format(lang.getMsg(sender, "commands.chat.changed-spec")));
            return true;
        }
        else {
            pSender.sendMessage(ColorUtils.format(lang.getMsg(sender, "commands.chat.invalid-channel")));
            return true;
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            List<String> options = new ArrayList<>(BASE_ARGS);
            if (sender.hasPermission("permapiola.admin.staffchat")) options.add("staff");
            if (sender.hasPermission("permapiola.admin.spec")) options.add("spec");

            return options.stream()
                    .filter(option -> option.startsWith(args[0].toLowerCase()))
                    .collect(Collectors.toList());
        }
        return new ArrayList<>();
    }
}