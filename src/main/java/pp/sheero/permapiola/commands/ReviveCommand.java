package pp.sheero.permapiola.commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.command.brigadier.argument.ArgumentTypes;
import io.papermc.paper.command.brigadier.argument.resolvers.selector.PlayerSelectorArgumentResolver;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import pp.sheero.permapiola.PermaPiola;
import pp.sheero.permapiola.core.LanguageManager;
import pp.sheero.permapiola.utils.ColorUtils;
import pp.sheero.permapiola.inventory.DeathInventoryManager;
import pp.sheero.permapiola.hurricane.DeathStateManager;

import java.util.List;

public class ReviveCommand {

    public static void register(Commands commands, PermaPiola plugin, LanguageManager lang) {

        var reviveNode = Commands.literal("revive")
                .requires(source -> source.getSender().hasPermission("permapiola.admin.revive"))

                .then(Commands.argument("target", ArgumentTypes.player())

                        .suggests((context, builder) -> {
                            String input = builder.getRemaining().toLowerCase();
                            for (String name : DeathStateManager.getDeadPlayerNames().values()) {
                                if (name.toLowerCase().startsWith(input)) {
                                    builder.suggest(name);
                                }
                            }
                            return builder.buildFuture();
                        })

                        .executes(context -> executeRevive(context, false, plugin, lang))

                        .then(Commands.literal("paid")
                                .executes(context -> executeRevive(context, true, plugin, lang))
                        )
                );

        commands.register(reviveNode.build(), "Revive a dead player");
    }

    private static int executeRevive(CommandContext<CommandSourceStack> context, boolean isPaid, PermaPiola plugin, LanguageManager lang) {
        CommandSender sender = context.getSource().getSender();
        PlayerSelectorArgumentResolver resolver = context.getArgument("target", PlayerSelectorArgumentResolver.class);

        try {
            List<Player> targets = resolver.resolve(context.getSource());

            if (targets.isEmpty()) {
                sender.sendMessage(ColorUtils.format(lang.getMsg(sender, "commands.revive.player-offline")));
                return Command.SINGLE_SUCCESS;
            }

            Player target = targets.get(0);

            if (!DeathStateManager.isDead(target.getUniqueId())) {
                sender.sendMessage(ColorUtils.format(lang.getMsg(sender, "commands.revive.not-dead")));
                return Command.SINGLE_SUCCESS;
            }

            Location spawnLoc = Bukkit.getWorlds().get(0).getSpawnLocation();
            target.teleport(spawnLoc);
            target.setGameMode(GameMode.SURVIVAL);

            if (!isPaid) {
                if (DeathInventoryManager.hasDeathInventory(target)) {
                    DeathInventoryManager.restoreInventory(target);
                }
                plugin.getDiscordManager().deleteDeathMessage(target.getUniqueId());
                DeathStateManager.decrementTotalDeaths();
            } else {
                DeathInventoryManager.clearInventory(target);
                plugin.getDiscordManager().sendReviveEmbed(target);
            }

            DeathStateManager.setDead(target.getUniqueId(), false);

            long durationHours = plugin.getConfig().getLong("hurricane.duration-hours", 1);
            long durationSeconds = durationHours * 3600;
            plugin.getHurricaneManager().removeTime(durationSeconds);

            plugin.getDementialWheelManager().cancelPendingSequences();

            sender.sendMessage(ColorUtils.format(lang.getMsg(sender, "commands.revive.success").replace("%player%", target.getName())));

            String alertMsg = lang.getMsg(sender, "commands.revive.staff-alert")
                    .replace("%admin%", sender.getName())
                    .replace("%player%", target.getName());

            Bukkit.getConsoleSender().sendMessage(ColorUtils.format(alertMsg));

            for (Player staff : Bukkit.getOnlinePlayers()) {
                if (staff.hasPermission("permapiola.admin.staffchat")) {
                    staff.sendMessage(ColorUtils.format(alertMsg));
                }
            }

        } catch (CommandSyntaxException e) {
            sender.sendMessage(ColorUtils.format(lang.getMsg(sender, "commands.revive.player-offline")));
        }

        return Command.SINGLE_SUCCESS;
    }
}