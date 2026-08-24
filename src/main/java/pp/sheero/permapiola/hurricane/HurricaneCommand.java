package pp.sheero.permapiola.hurricane;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import io.papermc.paper.command.brigadier.Commands;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import pp.sheero.permapiola.managers.LanguageManager;
import pp.sheero.permapiola.utils.ColorUtils;

import java.util.Arrays;

public class HurricaneCommand {

    public static void register(Commands commands, HurricaneManager hurricaneManager, LanguageManager lang) {

        var hurricaneNode = Commands.literal("hurricane")
                .requires(source -> source.getSender().hasPermission("permapiola.admin.hurricane"))

                // ==========================================
                // SUBCOMANDO: START
                // ==========================================
                .then(Commands.literal("start")
                        .executes(context -> {
                            CommandSender sender = context.getSource().getSender();

                            if (hurricaneManager.isActive()) {
                                sender.sendMessage(ColorUtils.format(lang.getMsg(sender, "hurricane.already-active")));
                                return Command.SINGLE_SUCCESS;
                            }

                            hurricaneManager.addHurricaneTime();

                            sender.sendMessage(ColorUtils.format(lang.getMsg(sender, "hurricane.start")));

                            long addedSeconds = hurricaneManager.getDurationSecondsCache();

                            for (Player p : Bukkit.getOnlinePlayers()) {
                                String formattedDur = hurricaneManager.getFormattedDuration(addedSeconds, p);
                                String broadcastRaw = lang.getMsg(p, "hurricane.death-event.hurricane-start").replace("%duration%", formattedDur);
                                p.sendMessage(ColorUtils.format(broadcastRaw));
                            }

                            String cFormat = hurricaneManager.getFormattedDuration(addedSeconds, Bukkit.getConsoleSender());
                            String consoleRaw = lang.getMsg(Bukkit.getConsoleSender(), "hurricane.death-event.hurricane-start").replace("%duration%", cFormat);
                            Bukkit.getConsoleSender().sendMessage(ColorUtils.format(consoleRaw));

                            String alert = lang.getMsg(sender, "hurricane.staff-alert-start")
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

                            if (!hurricaneManager.isActive()) {
                                sender.sendMessage(ColorUtils.format(lang.getMsg(sender, "hurricane.not-active")));
                                return Command.SINGLE_SUCCESS;
                            }

                            hurricaneManager.stopHurricane();

                            sender.sendMessage(ColorUtils.format(lang.getMsg(sender, "hurricane.stop")));

                            String alert = lang.getMsg(sender, "hurricane.staff-alert-stop")
                                    .replace("%admin%", sender.getName());
                            notifyStaff(alert, sender);

                            return Command.SINGLE_SUCCESS;
                        })
                )

                // ==========================================
                // SUBCOMANDO: SET (Modifica el config.yml global)
                // ==========================================
                .then(Commands.literal("set")
                        .then(Commands.argument("tiempo", StringArgumentType.word())
                                .suggests((context, builder) -> {
                                    handleTimeSuggestions(builder);
                                    return builder.buildFuture();
                                })
                                .executes(context -> {
                                    CommandSender sender = context.getSource().getSender();
                                    String rawTime = StringArgumentType.getString(context, "tiempo").toLowerCase();

                                    try {
                                        int seconds = parseTimeFormat(rawTime);

                                        // Validación anti-spam
                                        if (seconds == hurricaneManager.getDurationSecondsCache()) {
                                            sender.sendMessage(ColorUtils.format(lang.getMsg(sender, "hurricane.duration-already-set")));
                                            return Command.SINGLE_SUCCESS;
                                        }

                                        hurricaneManager.setDefaultDuration(rawTime, seconds);

                                        String msg = lang.getMsg(sender, "hurricane.duration-set").replace("%time%", rawTime);
                                        sender.sendMessage(ColorUtils.format(msg));

                                        String alert = lang.getMsg(sender, "hurricane.staff-alert-duration")
                                                .replace("%admin%", sender.getName())
                                                .replace("%time%", rawTime);
                                        notifyStaff(alert, sender);

                                    } catch (IllegalArgumentException e) {
                                        sender.sendMessage(ColorUtils.format(lang.getMsg(sender, "hurricane.invalid-time")));
                                    }

                                    return Command.SINGLE_SUCCESS;
                                })
                        )
                )

                // ==========================================
                // SUBCOMANDO: TIME (Agrupa add y remove)
                // ==========================================
                .then(Commands.literal("time")

                        // ADD
                        .then(Commands.literal("add")
                                .then(Commands.argument("tiempo", StringArgumentType.word())
                                        .suggests((context, builder) -> {
                                            handleTimeSuggestions(builder);
                                            return builder.buildFuture();
                                        })
                                        .executes(context -> handleTimeAction(context, hurricaneManager, lang, "add"))
                                )
                        )

                        // REMOVE
                        .then(Commands.literal("remove")
                                .then(Commands.argument("tiempo", StringArgumentType.word())
                                        .suggests((context, builder) -> {
                                            handleTimeSuggestions(builder);
                                            return builder.buildFuture();
                                        })
                                        .executes(context -> handleTimeAction(context, hurricaneManager, lang, "remove"))
                                )
                        )
                );

        for (String alias : Arrays.asList("hurricane", "hc")) {
            commands.register(Commands.literal(alias).redirect(hurricaneNode.build()).build(), "Manage the Hurricane event");
        }
    }

    // ==========================================
    // MÉTODOS AUXILIARES
    // ==========================================

    private static void handleTimeSuggestions(com.mojang.brigadier.suggestion.SuggestionsBuilder builder) {
        String input = builder.getRemaining().toLowerCase();

        if (!input.isEmpty()) {
            char lastChar = input.charAt(input.length() - 1);
            if (Character.isDigit(lastChar)) {
                builder.suggest(input + "s");
                builder.suggest(input + "m");
                builder.suggest(input + "h");
            }
        }
    }

    private static int handleTimeAction(com.mojang.brigadier.context.CommandContext<io.papermc.paper.command.brigadier.CommandSourceStack> context, HurricaneManager hurricaneManager, LanguageManager lang, String action) {
        CommandSender sender = context.getSource().getSender();

        if (!hurricaneManager.isActive()) {
            sender.sendMessage(ColorUtils.format(lang.getMsg(sender, "hurricane.not-active")));
            return Command.SINGLE_SUCCESS;
        }

        String rawTime = StringArgumentType.getString(context, "tiempo").toLowerCase();

        try {
            int seconds = parseTimeFormat(rawTime);

            String actionWordKey;
            String msgKey;

            if (action.equals("add")) {
                hurricaneManager.addTime(seconds);
                msgKey = "hurricane.time-added";
                actionWordKey = "hurricane.word-added";
            } else {
                hurricaneManager.removeTime(seconds);
                msgKey = "hurricane.time-removed";
                actionWordKey = "hurricane.word-removed";
            }

            String msg = lang.getMsg(sender, msgKey).replace("%time%", rawTime);
            sender.sendMessage(ColorUtils.format(msg));

            String actionWord = lang.getMsg(sender, actionWordKey);
            String alert = lang.getMsg(sender, "hurricane.staff-alert-time")
                    .replace("%admin%", sender.getName())
                    .replace("%action%", actionWord)
                    .replace("%time%", rawTime);
            notifyStaff(alert, sender);

        } catch (IllegalArgumentException e) {
            sender.sendMessage(ColorUtils.format(lang.getMsg(sender, "hurricane.invalid-time")));
        }

        return Command.SINGLE_SUCCESS;
    }

    private static int parseTimeFormat(String input) throws IllegalArgumentException {
        if (input == null || input.isEmpty()) throw new IllegalArgumentException();

        int totalSeconds = 0;
        int currentVal = 0;
        boolean hasValue = false;

        for (char c : input.toCharArray()) {
            if (Character.isDigit(c)) {
                currentVal = (currentVal * 10) + Character.getNumericValue(c);
                hasValue = true;
            } else {
                if (!hasValue) throw new IllegalArgumentException();
                if (c == 'h') totalSeconds += currentVal * 3600;
                else if (c == 'm') totalSeconds += currentVal * 60;
                else if (c == 's') totalSeconds += currentVal;
                else throw new IllegalArgumentException();

                currentVal = 0;
                hasValue = false;
            }
        }

        if (hasValue || totalSeconds <= 0) throw new IllegalArgumentException();

        return totalSeconds;
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
}