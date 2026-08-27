package pp.sheero.permapiola.commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.command.brigadier.argument.ArgumentTypes;
import io.papermc.paper.command.brigadier.argument.resolvers.selector.PlayerSelectorArgumentResolver;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.GameMode;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import pp.sheero.permapiola.core.LanguageManager;
import pp.sheero.permapiola.utils.ColorUtils;
import pp.sheero.permapiola.utils.ParticleUtils;

import java.util.List;

public class GmcCommand {

    public static void register(Commands commands, LanguageManager lang) {
        var gmcNode = Commands.literal("gmc")
                .requires(source -> source.getSender().hasPermission("permapiola.admin.gamemode.creative"))

                .executes(context -> {
                    CommandSender sender = context.getSource().getSender();
                    if (!(sender instanceof Player)) {
                        sender.sendMessage(ColorUtils.format(lang.getMsg(sender, "commands.generic.console-only")));
                        return Command.SINGLE_SUCCESS;
                    }
                    applyGamemode((Player) sender, sender, true);
                    return Command.SINGLE_SUCCESS;
                })

                .then(Commands.argument("target", ArgumentTypes.players())
                        .executes(context -> {
                            CommandSender sender = context.getSource().getSender();
                            PlayerSelectorArgumentResolver resolver = context.getArgument("target", PlayerSelectorArgumentResolver.class);

                            try {
                                List<Player> targets = resolver.resolve(context.getSource());

                                if (targets.isEmpty()) {
                                    sender.sendMessage(Component.translatable("argument.entity.notfound.player").color(NamedTextColor.RED));
                                    return Command.SINGLE_SUCCESS;
                                }

                                for (Player targetPlayer : targets) {
                                    applyGamemode(targetPlayer, sender, targetPlayer.equals(sender));
                                }

                            } catch (CommandSyntaxException e) {
                                sender.sendMessage(Component.translatable("argument.entity.notfound.player").color(NamedTextColor.RED));
                            }
                            return Command.SINGLE_SUCCESS;
                        })
                );

        commands.register(gmcNode.build());
    }

    private static void applyGamemode(Player targetPlayer, CommandSender sender, boolean isSelf) {
        if (targetPlayer.getGameMode() == GameMode.CREATIVE) return;

        targetPlayer.setGameMode(GameMode.CREATIVE);
        ParticleUtils.spawnGamemodeParticles(targetPlayer, GameMode.CREATIVE);

        Component translatedMode = Component.translatable("gameMode.creative");

        if (isSelf) {
            sender.sendMessage(Component.translatable("commands.gamemode.success.self", translatedMode));
        } else {
            sender.sendMessage(Component.translatable("commands.gamemode.success.other", targetPlayer.displayName(), translatedMode));
            targetPlayer.sendMessage(Component.translatable("gameMode.changed", translatedMode));
        }
    }
}