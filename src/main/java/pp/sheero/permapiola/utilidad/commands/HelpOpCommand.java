package pp.sheero.permapiola.utilidad.commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import io.papermc.paper.command.brigadier.Commands;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import pp.sheero.permapiola.PermaPiola;
import pp.sheero.permapiola.managers.EmoteManager;
import pp.sheero.permapiola.managers.LanguageManager;
import pp.sheero.permapiola.utils.ColorUtils;
import pp.sheero.permapiola.utils.LuckPermsUtils;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class HelpOpCommand {

    private static final Map<UUID, Long> cooldowns = new HashMap<>();

    public static void register(Commands commands, PermaPiola plugin, LanguageManager lang, EmoteManager emoteManager) {

        var helpopNode = Commands.literal("helpop")

                .then(Commands.argument("message", StringArgumentType.greedyString())
                        .executes(context -> {
                            CommandSender sender = context.getSource().getSender();

                            if (!(sender instanceof Player)) {
                                sender.sendMessage(Component.translatable("permissions.requires.player").color(NamedTextColor.RED));
                                return Command.SINGLE_SUCCESS;
                            }

                            Player pSender = (Player) sender;
                            UUID playerUUID = pSender.getUniqueId();

                            long configCooldownSeconds = plugin.getConfig().getLong("helpop.cooldown-seconds", 5);
                            long cooldownTimeMillis = configCooldownSeconds * 1000;

                            if (cooldownTimeMillis > 0 && cooldowns.containsKey(playerUUID)) {
                                long timeElapsed = System.currentTimeMillis() - cooldowns.get(playerUUID);
                                if (timeElapsed < cooldownTimeMillis) {
                                    long timeLeft = (cooldownTimeMillis - timeElapsed) / 1000;
                                    pSender.sendMessage(ColorUtils.format(lang.getMsg(sender, "commands.helpop.cooldown")
                                            .replace("%time%", String.valueOf(timeLeft + 1))));
                                    return Command.SINGLE_SUCCESS;
                                }
                            }

                            String rawMessage = StringArgumentType.getString(context, "message");
                            rawMessage = emoteManager.translateEmotes(pSender, rawMessage);

                            boolean isSenderDonor = pSender.hasPermission("permapiola.donor.color");

                            String finalMessageText = isSenderDonor
                                    ? ColorUtils.format("&f" + rawMessage)
                                    : rawMessage;

                            String prefix = LuckPermsUtils.getPrefix(pSender);
                            String suffix = LuckPermsUtils.getSuffix(pSender);
                            String senderFormat = (prefix != null ? prefix : "") + pSender.getName() + (suffix != null ? suffix : "");

                            String formatBase = lang.getMsg(pSender, "commands.helpop.format");

                            String coloredFormatBase = ColorUtils.format(formatBase.replace("%player_format%", senderFormat));

                            String fullMessage = coloredFormatBase.replace("%message%", finalMessageText);

                            pSender.sendMessage(fullMessage);

                            for (Player onlinePlayer : Bukkit.getOnlinePlayers()) {
                                if (onlinePlayer.hasPermission("permapiola.admin.helpop") && !onlinePlayer.equals(pSender)) {
                                    onlinePlayer.sendMessage(fullMessage);
                                    onlinePlayer.playSound(onlinePlayer.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 100f, 1.5f);
                                }
                            }

                            if (cooldownTimeMillis > 0) {
                                cooldowns.put(playerUUID, System.currentTimeMillis());
                            }

                            return Command.SINGLE_SUCCESS;
                        })
                );

        commands.register(helpopNode.build(), "Send a message to the staff");
    }
}