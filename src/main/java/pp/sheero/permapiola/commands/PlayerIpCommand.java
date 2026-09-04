package pp.sheero.permapiola.commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import io.papermc.paper.command.brigadier.Commands;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import pp.sheero.permapiola.core.LanguageManager;
import pp.sheero.permapiola.utils.ColorUtils;

import java.net.InetSocketAddress;
import java.util.Arrays;

public class PlayerIpCommand {

    public static void register(Commands commands, LanguageManager lang) {

        var ipNode = Commands.literal("playerip")
                .requires(source -> source.getSender().hasPermission("permapiola.admin.playerip"))

                .then(Commands.argument("target", StringArgumentType.word())
                        .suggests((context, builder) -> {
                            String input = builder.getRemaining().toLowerCase();
                            for (Player p : Bukkit.getOnlinePlayers()) {
                                if (p.getName().toLowerCase().startsWith(input)) {
                                    builder.suggest(p.getName());
                                }
                            }
                            return builder.buildFuture();
                        })
                        .executes(context -> {
                            CommandSender sender = context.getSource().getSender();
                            String targetName = context.getArgument("target", String.class);
                            Player target = Bukkit.getPlayer(targetName);

                            if (target == null) {
                                sender.sendMessage(ColorUtils.format(lang.getMsg(sender, "commands.generic.player-offline")));
                                return Command.SINGLE_SUCCESS;
                            }

                            InetSocketAddress address = target.getAddress();

                            if (address != null && address.getAddress() != null) {
                                String cleanIp = address.getAddress().getHostAddress();
                                String successMsg = lang.getMsg(sender, "commands.playerip.success")
                                        .replace("%player%", target.getName())
                                        .replace("%ip%", cleanIp);
                                sender.sendMessage(ColorUtils.format(successMsg));
                            } else {
                                sender.sendMessage(ColorUtils.format(lang.getMsg(sender, "commands.playerip.error")));
                            }

                            return Command.SINGLE_SUCCESS;
                        })
                );

        for (String alias : Arrays.asList("playerip", "getip")) {
            commands.register(Commands.literal(alias).redirect(ipNode.build()).build(), "Get a player's IP address");
        }
    }
}