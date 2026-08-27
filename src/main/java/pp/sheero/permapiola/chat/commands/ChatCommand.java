package pp.sheero.permapiola.chat.commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.LiteralMessage;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import io.papermc.paper.command.brigadier.Commands;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import pp.sheero.permapiola.chat.ChatManager;
import pp.sheero.permapiola.core.LanguageManager;
import pp.sheero.permapiola.teams.TeamManager;
import pp.sheero.permapiola.chat.ChatChannel;
import pp.sheero.permapiola.utils.ColorUtils;
import pp.sheero.permapiola.hurricane.DeathStateManager;

import java.util.ArrayList;
import java.util.List;

public class ChatCommand {

    private static final SimpleCommandExceptionType ERROR_INVALID = new SimpleCommandExceptionType(new LiteralMessage("Invalid argument"));

    public static void register(Commands commands, ChatManager chatManager, LanguageManager lang) {

        var chatNode = Commands.literal("chat")
                .then(Commands.argument("channel", StringArgumentType.word())
                        .suggests((context, builder) -> {
                            CommandSender sender = context.getSource().getSender();
                            String input = builder.getRemaining().toLowerCase();

                            List<String> options = new ArrayList<>();
                            options.add("all");
                            options.add("team");

                            if (sender.hasPermission("permapiola.admin.staffchat")) {
                                options.add("staff");
                            }
                            if (sender.hasPermission("permapiola.admin.spectator")) {
                                options.add("spectator");
                            }

                            for (String option : options) {
                                if (option.startsWith(input)) {
                                    builder.suggest(option);
                                }
                            }

                            return builder.buildFuture();
                        })
                        .executes(context -> {
                            CommandSender sender = context.getSource().getSender();

                            if (!(sender instanceof Player)) {
                                sender.sendMessage(Component.translatable("permissions.requires.player").color(NamedTextColor.RED));
                                return Command.SINGLE_SUCCESS;
                            }

                            Player pSender = (Player) sender;
                            boolean isDead = DeathStateManager.isDead(pSender.getUniqueId());
                            boolean isStaff = pSender.hasPermission("permapiola.admin") || pSender.hasPermission("permapiola.staff");

                            if (isDead && !isStaff) {
                                pSender.sendMessage(Component.translatable("commands.help.failed").color(NamedTextColor.RED));
                                return Command.SINGLE_SUCCESS;
                            }

                            String arg = StringArgumentType.getString(context, "channel").toLowerCase();
                            ChatChannel currentChannel = chatManager.getChannel(pSender);

                            if (arg.equals("a") || arg.equals("all")) {
                                if (currentChannel == ChatChannel.ALL) {
                                    pSender.sendMessage(ColorUtils.format(lang.getMsg(sender, "commands.chat.already-in-channel")));
                                    return Command.SINGLE_SUCCESS;
                                }
                                chatManager.setChannel(pSender, ChatChannel.ALL);
                                pSender.sendMessage(ColorUtils.format(lang.getMsg(sender, "commands.chat.changed-all")));
                            }
                            else if (arg.equals("s") || arg.equals("staff")) {
                                if (!pSender.hasPermission("permapiola.admin.staffchat")) {
                                    throw ERROR_INVALID.create();
                                }
                                if (currentChannel == ChatChannel.STAFF) {
                                    pSender.sendMessage(ColorUtils.format(lang.getMsg(sender, "commands.chat.already-in-channel")));
                                    return Command.SINGLE_SUCCESS;
                                }
                                chatManager.setChannel(pSender, ChatChannel.STAFF);
                                pSender.sendMessage(ColorUtils.format(lang.getMsg(sender, "commands.chat.changed-staff")));
                            }
                            else if (arg.equals("t") || arg.equals("team")) {
                                if (currentChannel == ChatChannel.TEAM) {
                                    pSender.sendMessage(ColorUtils.format(lang.getMsg(sender, "commands.chat.already-in-channel")));
                                    return Command.SINGLE_SUCCESS;
                                }
                                if (TeamManager.hasTeam(pSender)) {
                                    chatManager.setChannel(pSender, ChatChannel.TEAM);
                                    pSender.sendMessage(ColorUtils.format(lang.getMsg(sender, "commands.chat.changed-team")));
                                } else {
                                    pSender.sendMessage(ColorUtils.format(lang.getMsg(sender, "commands.chat.no-team")));
                                }
                            }
                            else if (arg.equals("sp") || arg.equals("spectator")) {
                                if (!pSender.hasPermission("permapiola.admin.spectator")) {
                                    throw ERROR_INVALID.create();
                                }
                                if (currentChannel == ChatChannel.SPEC) {
                                    pSender.sendMessage(ColorUtils.format(lang.getMsg(sender, "commands.chat.already-in-channel")));
                                    return Command.SINGLE_SUCCESS;
                                }
                                chatManager.setChannel(pSender, ChatChannel.SPEC);
                                pSender.sendMessage(ColorUtils.format(lang.getMsg(sender, "commands.chat.changed-spec")));
                            }
                            else {
                                throw ERROR_INVALID.create();
                            }

                            return Command.SINGLE_SUCCESS;
                        })
                );

        commands.register(chatNode.build());
    }
}