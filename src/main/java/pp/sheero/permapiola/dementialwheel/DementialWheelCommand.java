package pp.sheero.permapiola.dementialwheel;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import io.papermc.paper.command.brigadier.Commands;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import pp.sheero.permapiola.core.LanguageManager;
import pp.sheero.permapiola.utils.ColorUtils;

import java.util.Arrays;

public class DementialWheelCommand {

    public static void register(Commands commands, DementialWheelManager wheelManager, LanguageManager lang) {

        var dwNode = Commands.literal("dementialwheel")
                .requires(source -> source.getSender().hasPermission("permapiola.admin.dementialwheel"))

                // ==========================================
                // SUBCOMANDO: START
                // ==========================================
                .then(Commands.literal("start")
                        .executes(context -> {
                            CommandSender sender = context.getSource().getSender();

                            if (wheelManager.isActive() || wheelManager.isRolling()) {
                                sender.sendMessage(ColorUtils.format(lang.getMsg(sender, "commands.dementialwheel.already-rolling")));
                                return Command.SINGLE_SUCCESS;
                            }

                            wheelManager.startSequence(null);

                            sender.sendMessage(ColorUtils.format(lang.getMsg(sender, "commands.dementialwheel.start")));

                            String alert = lang.getMsg(sender, "commands.dementialwheel.staff-alert-start")
                                    .replace("%admin%", sender.getName());
                            notifyStaff(alert, sender);

                            return Command.SINGLE_SUCCESS;
                        })
                )

                // ==========================================
                // SUBCOMANDO: STOP
                // ==========================================
                .then(Commands.literal("stop")
                        .executes(context -> {
                            CommandSender sender = context.getSource().getSender();

                            if (!wheelManager.isActive() && !wheelManager.isRolling()) {
                                sender.sendMessage(ColorUtils.format(lang.getMsg(sender, "commands.dementialwheel.no-events-active")));
                                return Command.SINGLE_SUCCESS;
                            }

                            wheelManager.stopWheel();

                            sender.sendMessage(ColorUtils.format(lang.getMsg(sender, "commands.dementialwheel.stop")));

                            String alert = lang.getMsg(sender, "commands.dementialwheel.staff-alert-stop")
                                    .replace("%admin%", sender.getName());
                            notifyStaff(alert, sender);

                            return Command.SINGLE_SUCCESS;
                        })
                )

                // ==========================================
                // SUBCOMANDO: FORCE
                // ==========================================
                .then(Commands.literal("force")
                        .then(Commands.argument("event", StringArgumentType.word())
                                .suggests((context, builder) -> {
                                    String input = builder.getRemaining().toLowerCase();
                                    for (DementialEventType type : DementialEventType.values()) {
                                        if (type != DementialEventType.NONE && !wheelManager.hasEvent(type) && type.name().toLowerCase().startsWith(input)) {
                                            builder.suggest(type.name().toLowerCase());
                                        }
                                    }
                                    return builder.buildFuture();
                                })
                                .executes(context -> {
                                    CommandSender sender = context.getSource().getSender();
                                    String eventInput = StringArgumentType.getString(context, "event").toUpperCase();

                                    try {
                                        DementialEventType type = DementialEventType.valueOf(eventInput);
                                        if (type == DementialEventType.NONE) throw new IllegalArgumentException();

                                        if (wheelManager.hasEvent(type)) {
                                            sender.sendMessage(ColorUtils.format(lang.getMsg(sender, "commands.dementialwheel.already-active")));
                                            return Command.SINGLE_SUCCESS;
                                        }

                                        wheelManager.forceEvent(type);
                                        String niceName = formatEventName(type.name());

                                        String msg = lang.getMsg(sender, "commands.dementialwheel.force-enabled").replace("%event%", niceName);
                                        sender.sendMessage(ColorUtils.format(msg));

                                        wheelManager.broadcastEventMessage(type);

                                        String alert = lang.getMsg(sender, "commands.dementialwheel.staff-alert-force")
                                                .replace("%admin%", sender.getName())
                                                .replace("%event%", niceName);
                                        notifyStaff(alert, sender);

                                    } catch (IllegalArgumentException e) {
                                        sender.sendMessage(ColorUtils.format(lang.getMsg(sender, "commands.dementialwheel.invalid-event")));
                                    }
                                    return Command.SINGLE_SUCCESS;
                                })
                        )
                )

                // ==========================================
                // SUBCOMANDO: DISABLE
                // ==========================================
                .then(Commands.literal("disable")
                        .then(Commands.argument("event", StringArgumentType.word())
                                .suggests((context, builder) -> {
                                    String input = builder.getRemaining().toLowerCase();
                                    for (DementialEventType type : DementialEventType.values()) {
                                        if (type != DementialEventType.NONE && wheelManager.hasEvent(type) && type.name().toLowerCase().startsWith(input)) {
                                            builder.suggest(type.name().toLowerCase());
                                        }
                                    }
                                    return builder.buildFuture();
                                })
                                .executes(context -> {
                                    CommandSender sender = context.getSource().getSender();
                                    String eventInput = StringArgumentType.getString(context, "event").toUpperCase();

                                    try {
                                        DementialEventType type = DementialEventType.valueOf(eventInput);
                                        if (type == DementialEventType.NONE) throw new IllegalArgumentException();

                                        if (!wheelManager.hasEvent(type)) {
                                            sender.sendMessage(ColorUtils.format(lang.getMsg(sender, "commands.dementialwheel.event-not-active")));
                                            return Command.SINGLE_SUCCESS;
                                        }

                                        wheelManager.removeEvent(type);
                                        String niceName = formatEventName(type.name());

                                        sender.sendMessage(ColorUtils.format(lang.getMsg(sender, "commands.dementialwheel.force-disabled").replace("%event%", niceName)));

                                        String alert = lang.getMsg(sender, "commands.dementialwheel.staff-alert-disable")
                                                .replace("%admin%", sender.getName())
                                                .replace("%event%", niceName);
                                        notifyStaff(alert, sender);

                                    } catch (IllegalArgumentException e) {
                                        sender.sendMessage(ColorUtils.format(lang.getMsg(sender, "commands.dementialwheel.invalid-event")));
                                    }
                                    return Command.SINGLE_SUCCESS;
                                })
                        )
                );

        for (String alias : Arrays.asList("dementialwheel", "dw")) {
            commands.register(Commands.literal(alias).redirect(dwNode.build()).build(), "Demential Wheel Manager");
        }
    }

    private static void notifyStaff(String rawMessage, CommandSender exclude) {
        String formatted = ColorUtils.format(rawMessage);

        if (!(exclude instanceof org.bukkit.command.ConsoleCommandSender)) {
            Bukkit.getConsoleSender().sendMessage(formatted);
        }

        for (Player staff : Bukkit.getOnlinePlayers()) {
            if (staff.hasPermission("permapiola.admin.staffchat") && !staff.equals(exclude)) {
                staff.sendMessage(formatted);
            }
        }
    }

    public static String formatEventName(String rawName) {
        if (rawName == null || rawName.isEmpty()) return "";
        String[] words = rawName.toLowerCase().split("_");
        StringBuilder formatted = new StringBuilder();
        for (String word : words) {
            if (!word.isEmpty()) {
                formatted.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1)).append(" ");
            }
        }
        return formatted.toString().trim();
    }
}