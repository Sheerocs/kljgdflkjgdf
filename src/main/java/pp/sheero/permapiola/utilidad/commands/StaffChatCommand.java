package pp.sheero.permapiola.utilidad.commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import io.papermc.paper.command.brigadier.Commands;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import pp.sheero.permapiola.managers.ChatManager;
import pp.sheero.permapiola.managers.EmoteManager;
import pp.sheero.permapiola.managers.LanguageManager;

import java.util.Arrays;

public class StaffChatCommand {

    public static void register(Commands commands, ChatManager chatManager, LanguageManager lang, EmoteManager emoteManager) {

        var scNode = Commands.literal("staffchat")

                .then(Commands.argument("message", StringArgumentType.greedyString())
                        .executes(context -> {
                            CommandSender sender = context.getSource().getSender();

                            if (!(sender instanceof Player)) {
                                sender.sendMessage(Component.translatable("permissions.requires.player").color(NamedTextColor.RED));
                                return Command.SINGLE_SUCCESS;
                            }

                            Player pSender = (Player) sender;

                            if (!pSender.hasPermission("permapiola.admin.staffchat")) {
                                pSender.sendMessage(Component.translatable("commands.help.failed").color(NamedTextColor.RED));
                                return Command.SINGLE_SUCCESS;
                            }

                            String message = StringArgumentType.getString(context, "message");
                            message = emoteManager.translateEmotes(pSender, message);

                            chatManager.sendStaffMessage(pSender, message);

                            return Command.SINGLE_SUCCESS;
                        })
                );

        for (String alias : Arrays.asList("staffchat", "sc")) {
            commands.register(Commands.literal(alias).redirect(scNode.build()).build(), "Staff chat command");
        }
    }
}