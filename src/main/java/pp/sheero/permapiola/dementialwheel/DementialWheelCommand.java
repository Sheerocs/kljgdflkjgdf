package pp.sheero.permapiola.dementialwheel;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import pp.sheero.permapiola.PermaPiola;
import pp.sheero.permapiola.managers.LanguageManager;
import pp.sheero.permapiola.utils.ColorUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class DementialWheelCommand implements CommandExecutor, TabCompleter {

    private final PermaPiola plugin;
    private final LanguageManager lang;

    public DementialWheelCommand(PermaPiola plugin) {
        this.plugin = plugin;
        this.lang = plugin.getLanguageManager();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("permapiola.admin.dementialwheel")) {
            sender.sendMessage(ColorUtils.format(lang.getMsg(sender, "commands.dementialwheel.no-permission")));
            return true;
        }

        if (args.length == 0) {
            sender.sendMessage(ColorUtils.format(lang.getMsg(sender, "commands.dementialwheel.usage")));
            return true;
        }

        String subCommand = args[0].toLowerCase();
        DementialWheelManager manager = plugin.getDementialWheelManager();

        if (subCommand.equals("disable")) {
            if (args.length == 1) {
                if (!manager.isActive()) {
                    sender.sendMessage(ColorUtils.format(lang.getMsg(sender, "commands.dementialwheel.no-events-active")));
                    return true;
                }
                manager.stopWheel();
                sender.sendMessage(ColorUtils.format(lang.getMsg(sender, "commands.dementialwheel.disabled-all")));
                return true;
            }

            try {
                DementialEventType type = DementialEventType.valueOf(args[1].toUpperCase());

                if (type == DementialEventType.NONE) {
                    sender.sendMessage(ColorUtils.format(lang.getMsg(sender, "commands.dementialwheel.invalid-event")));
                    return true;
                }

                if (!manager.hasEvent(type)) {
                    sender.sendMessage(ColorUtils.format(lang.getMsg(sender, "commands.dementialwheel.event-not-active")));
                    return true;
                }

                manager.removeEvent(type);
                sender.sendMessage(ColorUtils.format(lang.getMsg(sender, "commands.dementialwheel.force-disabled").replace("%event%", type.name())));

            } catch (IllegalArgumentException e) {
                sender.sendMessage(ColorUtils.format(lang.getMsg(sender, "commands.dementialwheel.invalid-event")));
            }
            return true;
        }

        if (subCommand.equals("enable")) {
            if (args.length == 1) {
                boolean allActive = true;
                for (DementialEventType type : DementialEventType.values()) {
                    if (type != DementialEventType.NONE && !manager.hasEvent(type)) {
                        allActive = false;
                        break;
                    }
                }

                if (allActive) {
                    sender.sendMessage(ColorUtils.format(lang.getMsg(sender, "commands.dementialwheel.all-events-already-active")));
                    return true;
                }

                for (DementialEventType type : DementialEventType.values()) {
                    if (type != DementialEventType.NONE && !manager.hasEvent(type)) {
                        manager.forceEvent(type);
                    }
                }
                sender.sendMessage(ColorUtils.format(lang.getMsg(sender, "commands.dementialwheel.enabled-all-events")));
                return true;
            }

            try {
                DementialEventType type = DementialEventType.valueOf(args[1].toUpperCase());

                if (type == DementialEventType.NONE) {
                    sender.sendMessage(ColorUtils.format(lang.getMsg(sender, "commands.dementialwheel.invalid-event")));
                    return true;
                }

                if (manager.hasEvent(type)) {
                    sender.sendMessage(ColorUtils.format(lang.getMsg(sender, "commands.dementialwheel.already-active")));
                    return true;
                }

                manager.forceEvent(type);
                sender.sendMessage(ColorUtils.format(lang.getMsg(sender, "commands.dementialwheel.force-enabled").replace("%event%", type.name())));

            } catch (IllegalArgumentException e) {
                sender.sendMessage(ColorUtils.format(lang.getMsg(sender, "commands.dementialwheel.invalid-event")));
            }
            return true;
        }

        sender.sendMessage(ColorUtils.format(lang.getMsg(sender, "commands.dementialwheel.usage")));
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();

        if (!sender.hasPermission("permapiola.admin.dementialwheel")) return completions;

        if (args.length == 1) {
            completions.add("enable");
            completions.add("disable");
            return completions.stream().filter(c -> c.toLowerCase().startsWith(args[0].toLowerCase())).collect(Collectors.toList());
        }

        if (args.length == 2 && (args[0].equalsIgnoreCase("enable") || args[0].equalsIgnoreCase("disable"))) {
            for (DementialEventType type : DementialEventType.values()) {
                if (type != DementialEventType.NONE) {
                    completions.add(type.name());
                }
            }
            return completions.stream().filter(c -> c.toLowerCase().startsWith(args[1].toLowerCase())).collect(Collectors.toList());
        }

        return new ArrayList<>();
    }
}