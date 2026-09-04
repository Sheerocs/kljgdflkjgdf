package pp.sheero.permapiola.chat.commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.command.brigadier.argument.ArgumentTypes;
import io.papermc.paper.command.brigadier.argument.resolvers.selector.PlayerSelectorArgumentResolver;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Sound;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import pp.sheero.permapiola.PermaPiola;
import pp.sheero.permapiola.chat.ChatListener;
import pp.sheero.permapiola.chat.EmoteManager;
import pp.sheero.permapiola.core.LanguageManager;
import pp.sheero.permapiola.hurricane.DeathStateManager;
import pp.sheero.permapiola.utils.ColorUtils;
import pp.sheero.permapiola.chat.ReplyManager;

import java.util.List;

public class MsgCommand {

    public static void register(Commands commands, PermaPiola plugin, LanguageManager lang, EmoteManager emoteManager) {

        var msgNode = Commands.literal("msg")
                .then(Commands.argument("targets", ArgumentTypes.players())
                        .then(Commands.argument("message", StringArgumentType.greedyString())
                                .executes(context -> {
                                    CommandSender sender = context.getSource().getSender();

                                    if (!(sender instanceof Player)) {
                                        sender.sendMessage(ColorUtils.format(lang.getMsg(sender, "commands.generic.console-only")));
                                        return Command.SINGLE_SUCCESS;
                                    }

                                    Player pSender = (Player) sender;
                                    boolean isDead = DeathStateManager.isDead(pSender.getUniqueId());
                                    boolean isStaff = pSender.hasPermission("permapiola.admin") || pSender.hasPermission("permapiola.staff");

                                    if (isDead && !isStaff) {
                                        pSender.sendMessage(ColorUtils.format(lang.getMsg(pSender, "commands.generic.no-permission")));
                                        return Command.SINGLE_SUCCESS;
                                    }

                                    String rawMessage = StringArgumentType.getString(context, "message");
                                    rawMessage = emoteManager.translateEmotes(pSender, rawMessage);

                                    boolean isSenderDonor = pSender.hasPermission("permapiola.donor.color");
                                    String pathType = isSenderDonor ? "donator" : "default";
                                    String formatToPath = "private.format-to." + pathType;
                                    String formatFromPath = "private.format-from." + pathType;

                                    String formattedMessage = isSenderDonor ? ColorUtils.format(rawMessage) : rawMessage;
                                    String rawFormatTo = lang.getMsg(pSender, formatToPath);

                                    PlayerSelectorArgumentResolver resolver = context.getArgument("targets", PlayerSelectorArgumentResolver.class);

                                    try {
                                        List<Player> targets = resolver.resolve(context.getSource());

                                        if (targets.isEmpty()) {
                                            sender.sendMessage(Component.translatable("argument.entity.notfound.player").color(NamedTextColor.RED));
                                            return Command.SINGLE_SUCCESS;
                                        }

                                        for (Player target : targets) {

                                            String targetTag = ChatListener.getPlayerTag(target);
                                            String targetColor = ChatListener.getPlayerNameColor(target, plugin);
                                            String senderTag = ChatListener.getPlayerTag(pSender);
                                            String senderColor = ChatListener.getPlayerNameColor(pSender, plugin);

                                            String toTemplate = rawFormatTo
                                                    .replace("%target_prefix%", targetTag)
                                                    .replace("%target_suffix%", "")
                                                    .replace("%target%", targetColor + target.getName());

                                            String rawFormatFrom = lang.getMsg(target, formatFromPath);
                                            String fromTemplate = rawFormatFrom
                                                    .replace("%sender_prefix%", senderTag)
                                                    .replace("%sender_suffix%", "")
                                                    .replace("%sender%", senderColor + pSender.getName());

                                            String coloredToTemplate = ColorUtils.format(toTemplate);
                                            String coloredFromTemplate = ColorUtils.format(fromTemplate);

                                            String finalToMessage = coloredToTemplate.replace("%message%", formattedMessage);
                                            String finalFromMessage = coloredFromTemplate.replace("%message%", formattedMessage);

                                            pSender.sendMessage(finalToMessage);
                                            target.sendMessage(finalFromMessage);
                                            target.playSound(target.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 100f, 0.5f);

                                            ReplyManager.setReplyTarget(pSender.getUniqueId(), target.getUniqueId());
                                            ReplyManager.setReplyTarget(target.getUniqueId(), pSender.getUniqueId());
                                        }

                                    } catch (CommandSyntaxException e) {
                                        sender.sendMessage(Component.translatable("argument.entity.notfound.player").color(NamedTextColor.RED));
                                    }

                                    return Command.SINGLE_SUCCESS;
                                })
                        )
                );

        commands.register(msgNode.build());
    }
}