package pp.sheero.permapiola.hurricane;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import io.papermc.paper.command.brigadier.Commands;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import pp.sheero.permapiola.PermaPiola;
import pp.sheero.permapiola.core.LanguageManager;
import pp.sheero.permapiola.utils.ColorUtils;

import java.util.Arrays;
import java.util.regex.Pattern;

public class DeathMessageCommand {

    private static final Pattern VALID_CHARS = Pattern.compile("^[a-zA-Z0-9 áéíóúÁÉÍÓÚñÑüÜ.,!?¡¿'\"()-]+$");

    public static void register(Commands commands, PermaPiola plugin, LanguageManager lang) {

        var dmNode = Commands.literal("deathmessage")

                .executes(context -> {
                    CommandSender sender = context.getSource().getSender();

                    if (!(sender instanceof Player)) {
                        sender.sendMessage(Component.translatable("permissions.requires.player").color(NamedTextColor.RED));
                        return Command.SINGLE_SUCCESS;
                    }

                    Player player = (Player) sender;
                    DeathMessageManager manager = plugin.getDeathMessageManager();

                    if (manager.hasMessage(player.getUniqueId())) {
                        String currentMsg = manager.getMessage(player.getUniqueId());
                        player.sendMessage(ColorUtils.format(lang.getMsg(player, "hurricane.death-message.current").replace("%message%", currentMsg)));
                    } else {
                        player.sendMessage(ColorUtils.format(lang.getMsg(player, "hurricane.death-message.not-set")));
                    }

                    return Command.SINGLE_SUCCESS;
                })

                .then(Commands.argument("message", StringArgumentType.greedyString())
                        .executes(context -> {
                            CommandSender sender = context.getSource().getSender();

                            if (!(sender instanceof Player)) {
                                sender.sendMessage(Component.translatable("permissions.requires.player").color(NamedTextColor.RED));
                                return Command.SINGLE_SUCCESS;
                            }

                            Player player = (Player) sender;
                            DeathMessageManager manager = plugin.getDeathMessageManager();

                            String rawMessage = StringArgumentType.getString(context, "message");
                            String cleanMessage = ColorUtils.stripColors(rawMessage);
                            String lowerMsg = cleanMessage.toLowerCase();

                            String[] blockedExtensions = {
                                    ".com", ".net", ".org", ".edu", ".gov", ".mil", ".int", ".io", ".co",
                                    ".me", ".info", ".biz", ".xyz", ".online", ".site", ".tech", ".store",
                                    ".gg", ".tv", ".to", ".cc", ".us", ".uk", ".de", ".jp", ".br", ".es",
                                    ".mx", ".ar", ".cl", ".ru", ".fr", ".it", ".nl", ".au", ".ca", ".pe",
                                    ".uy", ".py", ".bo", ".ve", ".ec", ".pa", ".cr", ".cu", ".do"
                            };

                            boolean containsLink = false;

                            if (lowerMsg.contains("http") || lowerMsg.contains("www.") || lowerMsg.contains("/") || lowerMsg.contains(":")) {
                                containsLink = true;
                            } else {
                                for (String ext : blockedExtensions) {
                                    if (lowerMsg.contains(ext)) {
                                        containsLink = true;
                                        break;
                                    }
                                }
                            }

                            if (containsLink || !VALID_CHARS.matcher(cleanMessage).matches()) {
                                player.sendMessage(ColorUtils.format(lang.getMsg(player, "hurricane.death-message.invalid-chars")));
                                return Command.SINGLE_SUCCESS;
                            }

                            manager.setMessage(player.getUniqueId(), cleanMessage);
                            player.sendMessage(ColorUtils.format(lang.getMsg(player, "hurricane.death-message.set-success").replace("%message%", cleanMessage)));

                            return Command.SINGLE_SUCCESS;
                        })
                );

        for (String alias : Arrays.asList("deathmessage", "dm")) {
            commands.register(Commands.literal(alias).redirect(dmNode.build()).build(), "Set your custom death message");
        }
    }
}