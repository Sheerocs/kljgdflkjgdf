package pp.sheero.permapiola.commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.command.brigadier.argument.ArgumentTypes;
import io.papermc.paper.command.brigadier.argument.resolvers.selector.PlayerSelectorArgumentResolver;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import pp.sheero.permapiola.core.LanguageManager;
import pp.sheero.permapiola.utils.ColorUtils;

import java.net.InetSocketAddress;
import java.util.Arrays;
import java.util.List;

public class PlayerIpCommand {

    public static void register(Commands commands, LanguageManager lang) {

        var ipNode = Commands.literal("playerip")
                .requires(source -> source.getSender().hasPermission("permapiola.admin.playerip"))

                .then(Commands.argument("target", ArgumentTypes.player())
                        .executes(context -> {
                            CommandSender sender = context.getSource().getSender();
                            PlayerSelectorArgumentResolver resolver = context.getArgument("target", PlayerSelectorArgumentResolver.class);

                            try {
                                List<Player> targets = resolver.resolve(context.getSource());

                                if (targets.isEmpty()) {
                                    sender.sendMessage(Component.translatable("argument.entity.notfound.player").color(NamedTextColor.RED));
                                    return Command.SINGLE_SUCCESS;
                                }

                                Player target = targets.get(0);
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

                            } catch (CommandSyntaxException e) {
                                sender.sendMessage(Component.translatable("argument.entity.notfound.player").color(NamedTextColor.RED));
                            }

                            return Command.SINGLE_SUCCESS;
                        })
                );

        for (String alias : Arrays.asList("playerip", "getip")) {
            commands.register(Commands.literal(alias).redirect(ipNode.build()).build(), "Get a player's IP address");
        }
    }
}