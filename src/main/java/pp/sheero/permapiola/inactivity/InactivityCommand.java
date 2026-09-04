package pp.sheero.permapiola.inactivity;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import io.papermc.paper.command.brigadier.Commands;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import pp.sheero.permapiola.PermaPiola;
import pp.sheero.permapiola.core.LanguageManager;
import pp.sheero.permapiola.hurricane.DeathStateManager;
import pp.sheero.permapiola.utils.ColorUtils;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

public class InactivityCommand {

    public static void register(Commands commands, InactivityManager manager, PermaPiola plugin, LanguageManager lang) {

        var inactivityNode = Commands.literal("inactivity")
                .requires(source -> source.getSender().hasPermission("permapiola.admin.inactivity"))

                // =========================================================
                // SUBCOMANDO: LOG
                // =========================================================
                .then(Commands.literal("log")
                        .then(Commands.argument("target", StringArgumentType.word())
                                .suggests((context, builder) -> {
                                    String input = builder.getRemaining().toLowerCase();
                                    for (String name : manager.getPlayersWithHistory()) {
                                        if (name.toLowerCase().startsWith(input)) {
                                            builder.suggest(name);
                                        }
                                    }
                                    return builder.buildFuture();
                                })
                                .executes(context -> {
                                    CommandSender sender = context.getSource().getSender();
                                    String targetName = StringArgumentType.getString(context, "target");

                                    UUID targetUuid = manager.getUUIDFromHistory(targetName);
                                    if (targetUuid == null) {
                                        sender.sendMessage(ColorUtils.format(lang.getMsg(sender, "commands.inactivity.invalid-player")));
                                        return Command.SINGLE_SUCCESS;
                                    }

                                    List<String> logs = manager.getHistoryLogs(targetUuid, sender, lang);
                                    if (logs.isEmpty()) {
                                        sender.sendMessage(ColorUtils.format(lang.getMsg(sender, "commands.inactivity.log-empty")));
                                        return Command.SINGLE_SUCCESS;
                                    }

                                    String header = lang.getMsg(sender, "commands.inactivity.log-header").replace("%player%", targetName);
                                    sender.sendMessage(ColorUtils.format(header));
                                    for (String log : logs) {
                                        sender.sendMessage(ColorUtils.format(log));
                                    }

                                    return Command.SINGLE_SUCCESS;
                                })
                        )
                )

                // =========================================================
                // SUBCOMANDO: RESTORE
                // =========================================================
                .then(Commands.literal("restore")
                        .then(Commands.argument("target", StringArgumentType.word())
                                .suggests((context, builder) -> {
                                    String input = builder.getRemaining().toLowerCase();
                                    for (UUID deadId : manager.getDeadByInactivity()) {
                                        String name = DeathStateManager.getDeadPlayerNames().get(deadId);
                                        if (name != null && name.toLowerCase().startsWith(input)) {
                                            builder.suggest(name);
                                        }
                                    }
                                    return builder.buildFuture();
                                })
                                .executes(context -> {
                                    CommandSender sender = context.getSource().getSender();
                                    String targetName = StringArgumentType.getString(context, "target");

                                    UUID targetUuid = manager.getDeadUUIDByName(targetName);

                                    if (targetUuid == null || !manager.getDeadByInactivity().contains(targetUuid)) {
                                        sender.sendMessage(ColorUtils.format(lang.getMsg(sender, "commands.inactivity.restore-not-banned")));
                                        return Command.SINGLE_SUCCESS;
                                    }

                                    manager.removeDeadByInactivity(targetUuid);
                                    DeathStateManager.setDead(targetUuid, false);
                                    DeathStateManager.decrementTotalDeaths();
                                    plugin.getDiscordManager().deleteDeathMessage(targetUuid);

                                    Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "customwhitelist add " + targetName);

                                    manager.resetPlayerTimer(targetUuid);
                                    manager.clearHistory(targetUuid);

                                    String success = lang.getMsg(sender, "commands.inactivity.restore-success").replace("%player%", targetName);
                                    sender.sendMessage(ColorUtils.format(success));

                                    return Command.SINGLE_SUCCESS;
                                })
                        )
                );

        for (String alias : Arrays.asList("inactivity", "afkban")) {
            commands.register(Commands.literal(alias).redirect(inactivityNode.build()).build(), "Manage offline inactivity");
        }
    }
}