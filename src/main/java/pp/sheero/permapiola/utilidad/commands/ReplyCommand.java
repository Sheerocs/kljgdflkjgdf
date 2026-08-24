package pp.sheero.permapiola.utilidad.commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import io.papermc.paper.command.brigadier.Commands;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import pp.sheero.permapiola.managers.EmoteManager;
import pp.sheero.permapiola.managers.LanguageManager;
import pp.sheero.permapiola.utils.ColorUtils;
import pp.sheero.permapiola.utils.LuckPermsUtils;
import pp.sheero.permapiola.utils.ReplyManager;

import java.util.Arrays;
import java.util.UUID;

public class ReplyCommand {

    public static void register(Commands commands, LanguageManager lang, EmoteManager emoteManager) {

        var replyNode = Commands.literal("reply")

                .then(Commands.argument("message", StringArgumentType.greedyString())
                        .executes(context -> {
                            CommandSender sender = context.getSource().getSender();

                            if (!(sender instanceof Player)) {
                                sender.sendMessage(ColorUtils.format(lang.getMsg(sender, "commands.generic.console-only")));
                                return Command.SINGLE_SUCCESS;
                            }

                            Player pSender = (Player) sender;
                            boolean isDead = pp.sheero.permapiola.utils.DeathStateManager.isDead(pSender.getUniqueId());
                            boolean isStaff = pSender.hasPermission("permapiola.admin") || pSender.hasPermission("permapiola.staff");

                            if (isDead && !isStaff) {
                                pSender.sendMessage(ColorUtils.format(lang.getMsg(pSender, "commands.generic.no-permission")));
                                return Command.SINGLE_SUCCESS;
                            }

                            UUID targetUUID = ReplyManager.getReplyTarget(pSender.getUniqueId());
                            if (targetUUID == null) {
                                pSender.sendMessage(ColorUtils.format(lang.getMsg(sender, "commands.reply.no-target")));
                                return Command.SINGLE_SUCCESS;
                            }

                            Player target = Bukkit.getPlayer(targetUUID);
                            if (target == null || !target.isOnline()) {
                                pSender.sendMessage(ColorUtils.format(lang.getMsg(sender, "commands.generic.player-offline")));
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
                            String rawFormatFrom = lang.getMsg(target, formatFromPath);

                            String targetPrefix = LuckPermsUtils.getPrefix(target) != null ? LuckPermsUtils.getPrefix(target) : "";
                            String targetSuffix = LuckPermsUtils.getSuffix(target) != null ? LuckPermsUtils.getSuffix(target) : "";
                            String senderPrefix = LuckPermsUtils.getPrefix(pSender) != null ? LuckPermsUtils.getPrefix(pSender) : "";
                            String senderSuffix = LuckPermsUtils.getSuffix(pSender) != null ? LuckPermsUtils.getSuffix(pSender) : "";

                            String toTemplate = rawFormatTo
                                    .replace("%target_prefix%", targetPrefix)
                                    .replace("%target_suffix%", targetSuffix)
                                    .replace("%target%", target.getName());

                            String fromTemplate = rawFormatFrom
                                    .replace("%sender_prefix%", senderPrefix)
                                    .replace("%sender_suffix%", senderSuffix)
                                    .replace("%sender%", pSender.getName());

                            String coloredToTemplate = ColorUtils.format(toTemplate);
                            String coloredFromTemplate = ColorUtils.format(fromTemplate);

                            String finalToMessage = coloredToTemplate.replace("%message%", formattedMessage);
                            String finalFromMessage = coloredFromTemplate.replace("%message%", formattedMessage);

                            pSender.sendMessage(finalToMessage);
                            target.sendMessage(finalFromMessage);
                            target.playSound(target.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 100f, 0.5f);

                            ReplyManager.setReplyTarget(pSender.getUniqueId(), target.getUniqueId());
                            ReplyManager.setReplyTarget(target.getUniqueId(), pSender.getUniqueId());

                            return Command.SINGLE_SUCCESS;
                        })
                );

        for (String alias : Arrays.asList("reply", "r")) {
            commands.register(Commands.literal(alias).redirect(replyNode.build()).build(), "Reply to a private message");
        }
    }
}