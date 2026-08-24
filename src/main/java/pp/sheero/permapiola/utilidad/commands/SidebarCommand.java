package pp.sheero.permapiola.utilidad.commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.LiteralMessage;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import io.papermc.paper.command.brigadier.Commands;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import pp.sheero.permapiola.managers.LanguageManager;
import pp.sheero.permapiola.managers.ScoreboardManager;

import java.util.Arrays;
import java.util.List;

public class SidebarCommand {

    private static final SimpleCommandExceptionType ERROR_INVALID = new SimpleCommandExceptionType(new LiteralMessage("Invalid argument"));

    public static void register(Commands commands, ScoreboardManager scoreboardManager, LanguageManager lang) {

        var sidebarNode = Commands.literal("sidebar")

                .executes(context -> {
                    CommandSender sender = context.getSource().getSender();

                    if (!(sender instanceof Player)) {
                        sender.sendMessage(Component.translatable("permissions.requires.player").color(NamedTextColor.RED));
                        return Command.SINGLE_SUCCESS;
                    }

                    scoreboardManager.toggle((Player) sender);
                    return Command.SINGLE_SUCCESS;
                })

                .then(Commands.argument("state", StringArgumentType.word())
                        .suggests((context, builder) -> {
                            String input = builder.getRemaining().toLowerCase();
                            List<String> options = Arrays.asList("on", "off", "toggle");

                            for (String option : options) {
                                if (option.startsWith(input)) {
                                    builder.suggest(option);
                                }
                            }
                            return builder.buildFuture();
                        })
                        .executes(context -> {
                            CommandSender sender = context.getSource().getSender();

                            if (!(sender instanceof Player)) {
                                sender.sendMessage(Component.translatable("permissions.requires.player").color(NamedTextColor.RED));
                                return Command.SINGLE_SUCCESS;
                            }

                            Player player = (Player) sender;
                            String state = StringArgumentType.getString(context, "state").toLowerCase();

                            switch (state) {
                                case "on":
                                case "enable":
                                case "true":
                                    scoreboardManager.setScoreboardState(player, true);
                                    break;
                                case "off":
                                case "disable":
                                case "false":
                                    scoreboardManager.setScoreboardState(player, false);
                                    break;
                                case "toggle":
                                    scoreboardManager.toggle(player);
                                    break;
                                default:
                                    throw ERROR_INVALID.create();
                            }

                            return Command.SINGLE_SUCCESS;
                        })
                );

        for (String alias : Arrays.asList("sidebar", "sb")) {
            commands.register(Commands.literal(alias).redirect(sidebarNode.build()).build(), "Toggle or set your sidebar");
        }
    }
}