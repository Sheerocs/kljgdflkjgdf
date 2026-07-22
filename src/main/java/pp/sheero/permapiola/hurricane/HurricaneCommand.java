package pp.sheero.permapiola.hurricane;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import pp.sheero.permapiola.PermaPiola;
import pp.sheero.permapiola.managers.LanguageManager;
import pp.sheero.permapiola.utils.ColorUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class HurricaneCommand implements CommandExecutor, TabCompleter {

    private final PermaPiola plugin;
    private final LanguageManager lang;

    private static final Pattern TIME_PATTERN = Pattern.compile("(?:(\\d+)h)?(?:(\\d+)m)?(?:(\\d+)s)?");

    private static final List<String> SUB_COMMANDS = Arrays.asList("debug", "enable", "disable", "edit");
    private static final List<String> EDIT_ACTIONS = Arrays.asList("add", "remove", "set");
    private static final List<String> TIME_SUGGESTIONS = Arrays.asList("1h30m", "45m", "10s", "1h2m3s");

    public HurricaneCommand(PermaPiola plugin, LanguageManager lang) {
        this.plugin = plugin;
        this.lang = lang;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("permapiola.admin.hurricane")) {
            sender.sendMessage(ColorUtils.format(lang.getMsg(sender, "commands.generic.no-permission")));
            return true;
        }

        if (args.length == 0) {
            sender.sendMessage(ColorUtils.format(lang.getMsg(sender, "hurricane.commands.usage")));
            return true;
        }

        String subCommand = args[0].toLowerCase();
        HurricaneManager manager = plugin.getHurricaneManager();

        switch (subCommand) {
            case "debug":
                String debugPath = manager.isActive() ? "hurricane.commands.debug-active" : "hurricane.commands.debug-inactive";
                sender.sendMessage(ColorUtils.format(lang.getMsg(sender, debugPath)));
                break;

            case "enable":
                if (!sender.hasPermission("permapiola.admin.hurricane.enable")) {
                    sender.sendMessage(ColorUtils.format(lang.getMsg(sender, "commands.generic.no-permission")));
                    return true;
                }
                if (manager.isActive()) {
                    sender.sendMessage(ColorUtils.format(lang.getMsg(sender, "hurricane.commands.already-enabled")));
                    return true;
                }
                manager.addHurricaneTime();
                sender.sendMessage(ColorUtils.format(lang.getMsg(sender, "hurricane.commands.enabled")));
                break;

            case "disable":
                if (!sender.hasPermission("permapiola.admin.hurricane.disable")) {
                    sender.sendMessage(ColorUtils.format(lang.getMsg(sender, "commands.generic.no-permission")));
                    return true;
                }
                if (!manager.isActive()) {
                    sender.sendMessage(ColorUtils.format(lang.getMsg(sender, "hurricane.commands.already-disabled")));
                    return true;
                }
                manager.stopHurricane();
                sender.sendMessage(ColorUtils.format(lang.getMsg(sender, "hurricane.commands.disabled")));
                break;

            case "edit":
                if (!sender.hasPermission("permapiola.admin.hurricane.edit")) {
                    sender.sendMessage(ColorUtils.format(lang.getMsg(sender, "commands.generic.no-permission")));
                    return true;
                }

                if (!manager.isActive()) {
                    sender.sendMessage(ColorUtils.format(lang.getMsg(sender, "hurricane.commands.already-disabled")));
                    return true;
                }

                if (args.length < 3) {
                    sender.sendMessage(ColorUtils.format(lang.getMsg(sender, "hurricane.commands.usage-edit")));
                    return true;
                }

                String action = args[1].toLowerCase();
                long secondsToApply = parseTime(args[2].toLowerCase());

                if (secondsToApply <= 0) {
                    sender.sendMessage(ColorUtils.format(lang.getMsg(sender, "hurricane.commands.invalid-time-format")));
                    return true;
                }

                String formattedTime = formatTimeDisplay(secondsToApply);

                switch (action) {
                    case "add":
                        manager.addTime(secondsToApply);
                        sender.sendMessage(ColorUtils.format(lang.getMsg(sender, "hurricane.commands.time-added").replace("%time%", formattedTime)));
                        sendBroadcastToAdminsAndConsole(sender, "hurricane.commands.broadcast-added", formattedTime);
                        break;

                    case "remove":
                        if (manager.getTimeRemaining() - secondsToApply <= 0) {
                            sender.sendMessage(ColorUtils.format(lang.getMsg(sender, "hurricane.commands.cannot-remove-all")));
                            return true;
                        }
                        manager.removeTime(secondsToApply);
                        sender.sendMessage(ColorUtils.format(lang.getMsg(sender, "hurricane.commands.time-removed").replace("%time%", formattedTime)));
                        sendBroadcastToAdminsAndConsole(sender, "hurricane.commands.broadcast-removed", formattedTime);
                        break;

                    case "set":
                        manager.setTime(secondsToApply);
                        sender.sendMessage(ColorUtils.format(lang.getMsg(sender, "hurricane.commands.time-set").replace("%time%", formattedTime)));
                        sendBroadcastToAdminsAndConsole(sender, "hurricane.commands.broadcast-set", formattedTime);
                        break;

                    default:
                        sender.sendMessage(ColorUtils.format(lang.getMsg(sender, "hurricane.commands.usage-edit")));
                        break;
                }
                break;

            default:
                sender.sendMessage(ColorUtils.format(lang.getMsg(sender, "hurricane.commands.usage")));
                break;
        }

        return true;
    }

    private long parseTime(String timeString) {
        long totalSeconds = 0;
        boolean matched = false;
        Matcher m = TIME_PATTERN.matcher(timeString);

        if (m.matches()) {
            if (m.group(1) != null) { totalSeconds += Long.parseLong(m.group(1)) * 3600; matched = true; }
            if (m.group(2) != null) { totalSeconds += Long.parseLong(m.group(2)) * 60; matched = true; }
            if (m.group(3) != null) { totalSeconds += Long.parseLong(m.group(3)); matched = true; }
        }
        return matched ? totalSeconds : -1;
    }

    private String formatTimeDisplay(long totalSeconds) {
        if (totalSeconds == 0) return "0s";
        long h = totalSeconds / 3600;
        long m = (totalSeconds % 3600) / 60;
        long s = totalSeconds % 60;
        StringBuilder sb = new StringBuilder();
        if (h > 0) sb.append(h).append("h");
        if (m > 0) sb.append(m).append("m");
        if (s > 0) sb.append(s).append("s");
        return sb.toString();
    }

    private void sendBroadcastToAdminsAndConsole(CommandSender sender, String messagePath, String formattedTime) {
        String msgRaw = lang.getMsg(Bukkit.getConsoleSender(), messagePath)
                .replace("%player%", sender.getName())
                .replace("%time%", formattedTime);

        String coloredMsg = ColorUtils.format(msgRaw);

        Bukkit.getConsoleSender().sendMessage(coloredMsg);

        for (Player p : Bukkit.getOnlinePlayers()) {
            if (p.hasPermission("permapiola.admin") && !p.equals(sender)) {
                p.sendMessage(ColorUtils.format(lang.getMsg(p, messagePath)
                        .replace("%player%", sender.getName())
                        .replace("%time%", formattedTime)));
            }
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission("permapiola.admin.hurricane")) return new ArrayList<>();

        if (args.length == 1) {
            return SUB_COMMANDS.stream().filter(c -> c.toLowerCase().startsWith(args[0].toLowerCase())).collect(Collectors.toList());
        }

        if (args.length == 2 && args[0].equalsIgnoreCase("edit")) {
            return EDIT_ACTIONS.stream().filter(c -> c.toLowerCase().startsWith(args[1].toLowerCase())).collect(Collectors.toList());
        }

        if (args.length == 3 && args[0].equalsIgnoreCase("edit")) {
            return TIME_SUGGESTIONS.stream().filter(c -> c.toLowerCase().startsWith(args[2].toLowerCase())).collect(Collectors.toList());
        }

        return new ArrayList<>();
    }
}