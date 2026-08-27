package pp.sheero.permapiola.teams;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import io.papermc.paper.command.brigadier.Commands;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import pp.sheero.permapiola.core.LanguageManager;
import pp.sheero.permapiola.utils.ColorUtils;
import pp.sheero.permapiola.hurricane.DeathStateManager;

import java.util.Arrays;

public class TeamChatCommand {

    public static void register(Commands commands, LanguageManager languageManager) {

        var tcNode = Commands.literal("teamchat")

                .then(Commands.argument("message", StringArgumentType.greedyString())
                        .executes(context -> {
                            CommandSender sender = context.getSource().getSender();

                            if (!(sender instanceof Player)) {
                                sender.sendMessage(Component.translatable("permissions.requires.player").color(NamedTextColor.RED));
                                return Command.SINGLE_SUCCESS;
                            }

                            Player player = (Player) sender;

                            boolean isDead = DeathStateManager.isDead(player.getUniqueId());
                            boolean isStaff = player.hasPermission("permapiola.admin") || player.hasPermission("permapiola.staff");

                            if (isDead && !isStaff) {
                                player.sendMessage(ColorUtils.format(languageManager.getMsg(player, "commands.chat.dead-restricted")));
                                return Command.SINGLE_SUCCESS;
                            }

                            if (!TeamManager.isTeamsEnabled()) {
                                player.sendMessage(ColorUtils.format(languageManager.getMsg(player, "teams.system-disabled")));
                                return Command.SINGLE_SUCCESS;
                            }

                            if (!TeamManager.hasTeam(player)) {
                                player.sendMessage(ColorUtils.format(languageManager.getMsg(player, "teams.not-in-team")));
                                return Command.SINGLE_SUCCESS;
                            }

                            String message = StringArgumentType.getString(context, "message");
                            TeamManager.sendTeamChatMessage(player, message, languageManager);

                            return Command.SINGLE_SUCCESS;
                        })
                );

        for (String alias : Arrays.asList("teamchat", "tc")) {
            commands.register(Commands.literal(alias).redirect(tcNode.build()).build(), "Team chat command");
        }
    }
}