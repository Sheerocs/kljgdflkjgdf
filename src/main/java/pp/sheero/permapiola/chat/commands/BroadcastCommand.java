package pp.sheero.permapiola.chat.commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import io.papermc.paper.command.brigadier.Commands;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import pp.sheero.permapiola.chat.EmoteManager;
import pp.sheero.permapiola.core.LanguageManager;
import pp.sheero.permapiola.utils.ColorUtils;

import java.util.Arrays;

public class BroadcastCommand {

    public static void register(Commands commands, LanguageManager lang, EmoteManager emoteManager) {

        var bcNode = Commands.literal("broadcast")

                .then(Commands.argument("message", StringArgumentType.greedyString())
                        .executes(context -> {
                            CommandSender sender = context.getSource().getSender();

                            if (!sender.hasPermission("permapiola.admin.broadcast")) {
                                sender.sendMessage(Component.translatable("commands.help.failed").color(NamedTextColor.RED));
                                return Command.SINGLE_SUCCESS;
                            }

                            String message = StringArgumentType.getString(context, "message");

                            if (sender instanceof Player) {
                                Player pSender = (Player) sender;
                                message = emoteManager.translateEmotes(pSender, message);
                            }

                            String broadcastFormat = lang.getMsg(sender, "commands.broadcast.format");
                            String fullMessage = ColorUtils.format(broadcastFormat.replace("%message%", message));

                            Bukkit.broadcastMessage(fullMessage);

                            return Command.SINGLE_SUCCESS;
                        })
                );

        for (String alias : Arrays.asList("broadcast", "bc")) {
            commands.register(Commands.literal(alias).redirect(bcNode.build()).build(), "Broadcast a global message");
        }
    }
}