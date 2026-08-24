package pp.sheero.permapiola.utilidad.commands;

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
import pp.sheero.permapiola.managers.LanguageManager;
import pp.sheero.permapiola.utils.ColorUtils;
import pp.sheero.permapiola.utils.ParticleUtils;

import java.util.List;

public class GmCommand {

    public static void register(Commands commands, LanguageManager lang) {
        var gmNode = Commands.literal("gm")
                .requires(source -> source.getSender().hasPermission("permapiola.admin.gamemode"));

        for (int i = 0; i <= 3; i++) {
            final int modeNum = i;
            GameMode targetMode = getGameModeByInt(modeNum);
            String modeKey = getModeKeyByInt(modeNum);

            gmNode.then(Commands.literal(String.valueOf(modeNum))

                    .executes(context -> {
                        CommandSender sender = context.getSource().getSender();
                        if (!(sender instanceof Player)) {
                            sender.sendMessage(ColorUtils.format(lang.getMsg(sender, "commands.generic.console-only")));
                            return Command.SINGLE_SUCCESS;
                        }
                        applyGamemode((Player) sender, sender, targetMode, modeKey, true);
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
                                        boolean isSelf = targetPlayer.equals(sender);
                                        applyGamemode(targetPlayer, sender, targetMode, modeKey, isSelf);
                                    }

                                } catch (CommandSyntaxException e) {
                                    sender.sendMessage(Component.translatable("argument.entity.notfound.player").color(NamedTextColor.RED));
                                }

                                return Command.SINGLE_SUCCESS;
                            })
                    )
            );
        }

        commands.register(gmNode.build());
    }

    private static GameMode getGameModeByInt(int mode) {
        switch (mode) {
            case 0: return GameMode.SURVIVAL;
            case 1: return GameMode.CREATIVE;
            case 2: return GameMode.ADVENTURE;
            case 3: return GameMode.SPECTATOR;
            default: return GameMode.SURVIVAL;
        }
    }

    private static String getModeKeyByInt(int mode) {
        switch (mode) {
            case 0: return "survival";
            case 1: return "creative";
            case 2: return "adventure";
            case 3: return "spectator";
            default: return "survival";
        }
    }

    private static void applyGamemode(Player targetPlayer, CommandSender sender, GameMode targetMode, String modeKey, boolean isSelf) {
        if (targetPlayer.getGameMode() == targetMode) return;

        targetPlayer.setGameMode(targetMode);
        ParticleUtils.spawnGamemodeParticles(targetPlayer, targetMode);
        Component translatedMode = Component.translatable("gameMode." + modeKey);

        if (isSelf) {
            sender.sendMessage(Component.translatable("commands.gamemode.success.self", translatedMode));
        } else {
            sender.sendMessage(Component.translatable("commands.gamemode.success.other", targetPlayer.displayName(), translatedMode));
            targetPlayer.sendMessage(Component.translatable("gameMode.changed", translatedMode));
        }
    }
}