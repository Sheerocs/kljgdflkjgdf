package pp.sheero.permapiola.chat.commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import io.papermc.paper.command.brigadier.Commands;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import pp.sheero.permapiola.chat.ChatManager;
import pp.sheero.permapiola.chat.EmoteManager;
import pp.sheero.permapiola.core.LanguageManager;

import java.util.Arrays;

public class SpectatorChatCommand {

    public static void register(Commands commands, ChatManager chatManager, LanguageManager lang, EmoteManager emoteManager) {

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

                            chatManager.sendSpecMessage(pSender, message);

                            return Command.SINGLE_SUCCESS;
                        })
                );

        for (String alias : Arrays.asList("spectatorchat", "spc")) {
            commands.register(Commands.literal(alias).redirect(spcNode.build()).build(), "Spectator chat command");
        }
    }
}