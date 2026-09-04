package pp.sheero.permapiola.chat.commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import io.papermc.paper.command.brigadier.Commands;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import pp.sheero.permapiola.PermaPiola;
import pp.sheero.permapiola.chat.ChatManager;
import pp.sheero.permapiola.chat.ChatListener;
import pp.sheero.permapiola.chat.EmoteManager;
import pp.sheero.permapiola.core.LanguageManager;
import pp.sheero.permapiola.hurricane.DeathStateManager;
import pp.sheero.permapiola.utils.ColorUtils;

import java.util.Arrays;

public class SpectatorChatCommand {

    public static void register(Commands commands, PermaPiola plugin, ChatManager chatManager, LanguageManager lang, EmoteManager emoteManager) {

        var spcNode = Commands.literal("spectatorchat")
                .then(Commands.argument("message", StringArgumentType.greedyString())
                        .executes(context -> {
                            CommandSender sender = context.getSource().getSender();

                            if (!(sender instanceof Player)) {
                                sender.sendMessage(Component.translatable("permissions.requires.player").color(NamedTextColor.RED));
                                return Command.SINGLE_SUCCESS;
                            }

                            Player pSender = (Player) sender;

                            if (!pSender.hasPermission("permapiola.admin.spectatorchat")) {
                                pSender.sendMessage(Component.translatable("commands.help.failed").color(NamedTextColor.RED));
                                return Command.SINGLE_SUCCESS;
                            }

                            String message = StringArgumentType.getString(context, "message");
                            message = emoteManager.translateEmotes(pSender, message);

                            String tag = ChatListener.getPlayerTag(pSender);
                            String nameColor = ChatListener.getPlayerNameColor(pSender, plugin);
                            String formattedName = nameColor + pSender.getName();

                            String format = pSender.hasPermission("permapiola.donor.color")
                                    ? chatManager.getSpecFormatDonator()
                                    : chatManager.getSpecFormatDefault();

                            String formattedMessageText = pSender.hasPermission("permapiola.donor.color") ? ColorUtils.format(message) : message;

                            String baseFormat = ColorUtils.format(format
                                    .replace("%player_prefix%", tag)
                                    .replace("%player%", formattedName));

                            String coloredMessage = baseFormat.replace("%message%", formattedMessageText);

                            Bukkit.getConsoleSender().sendMessage(coloredMessage);

                            for (Player p : Bukkit.getOnlinePlayers()) {
                                if (DeathStateManager.isDead(p.getUniqueId()) || p.hasPermission("permapiola.admin") || p.hasPermission("permapiola.staff")) {
                                    p.sendMessage(coloredMessage);
                                }
                            }

                            return Command.SINGLE_SUCCESS;
                        })
                );

        for (String alias : Arrays.asList("spectatorchat", "spc")) {
            commands.register(Commands.literal(alias).redirect(spcNode.build()).build(), "Spectator chat command");
        }
    }
}