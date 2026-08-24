package pp.sheero.permapiola.utilidad.commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.LiteralMessage;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import io.papermc.paper.command.brigadier.Commands;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import pp.sheero.permapiola.PermaPiola;
import pp.sheero.permapiola.managers.EmoteManager;
import pp.sheero.permapiola.managers.LanguageManager;
import pp.sheero.permapiola.teams.TeamManager;
import pp.sheero.permapiola.utils.ColorUtils;

import java.util.List;

public class PermaPiolaCommand {

    private static final SimpleCommandExceptionType ERROR_INVALID = new SimpleCommandExceptionType(new LiteralMessage("Invalid argument"));

    public static void register(Commands commands, PermaPiola plugin, LanguageManager lang, EmoteManager emoteManager) {

        var permapiolaNode = Commands.literal("permapiola")
                .requires(source -> source.getSender().hasPermission("permapiola.admin.reload"))

                .then(Commands.literal("reload")
                        .executes(context -> {
                            CommandSender sender = context.getSource().getSender();

                            reloadAll(plugin, lang, emoteManager);

                            sender.sendMessage(ColorUtils.format(lang.getMsg(sender, "commands.permapiola.reload-all")));
                            return Command.SINGLE_SUCCESS;
                        })

                        .then(Commands.argument("target", StringArgumentType.word())
                                .suggests((context, builder) -> {
                                    String input = builder.getRemaining().toLowerCase();

                                    List<String> options = List.of("config", "emotes", "locales");

                                    for (String option : options) {
                                        if (option.startsWith(input)) {
                                            builder.suggest(option);
                                        }
                                    }
                                    return builder.buildFuture();
                                })
                                .executes(context -> {
                                    CommandSender sender = context.getSource().getSender();
                                    String target = StringArgumentType.getString(context, "target").toLowerCase();

                                    switch (target) {
                                        case "config":
                                            plugin.reloadConfig();
                                            reloadManagersConfig(plugin);
                                            TeamManager.loadConfigCache(plugin);
                                            sender.sendMessage(ColorUtils.format(lang.getMsg(sender, "commands.permapiola.reload-file").replace("%file%", "Configuración")));
                                            break;

                                        case "emotes":
                                            emoteManager.loadEmotes();
                                            sender.sendMessage(ColorUtils.format(lang.getMsg(sender, "commands.permapiola.reload-file").replace("%file%", "Emotes")));
                                            break;

                                        case "locales":
                                            lang.loadLocales();
                                            sender.sendMessage(ColorUtils.format(lang.getMsg(sender, "commands.permapiola.reload-file").replace("%file%", "Idiomas")));
                                            break;

                                        default:
                                            throw ERROR_INVALID.create();
                                    }

                                    return Command.SINGLE_SUCCESS;
                                })
                        )
                );

        commands.register(permapiolaNode.build(), "Main PermaPiola reload command");
    }

    private static void reloadAll(PermaPiola plugin, LanguageManager lang, EmoteManager emoteManager) {
        plugin.reloadConfig();
        lang.loadLocales();
        emoteManager.loadEmotes();

        reloadManagersConfig(plugin);
        TeamManager.loadConfigCache(plugin);

        for (Player p : Bukkit.getOnlinePlayers()) {
            p.updateCommands();
        }
    }

    private static void reloadManagersConfig(PermaPiola plugin) {
        if (plugin.getDementialWheelManager() != null) plugin.getDementialWheelManager().loadConfigCache();
        if (plugin.getHurricaneManager() != null) plugin.getHurricaneManager().loadConfigCache();
        if (plugin.getAfkManager() != null) plugin.getAfkManager().loadConfigCache();
        if (plugin.getTotemListener() != null) plugin.getTotemListener().loadConfigCache();
        if (plugin.getDiscordManager() != null) plugin.getDiscordManager().loadConfigCache();
        if (plugin.getChatManager() != null) plugin.getChatManager().loadConfigCache();
    }
}