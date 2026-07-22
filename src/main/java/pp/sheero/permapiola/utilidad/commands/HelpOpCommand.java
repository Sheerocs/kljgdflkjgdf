package pp.sheero.permapiola.utilidad.commands;

import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import pp.sheero.permapiola.PermaPiola;
import pp.sheero.permapiola.managers.EmoteManager;
import pp.sheero.permapiola.managers.LanguageManager;
import pp.sheero.permapiola.utils.ColorUtils;
import pp.sheero.permapiola.utils.LuckPermsUtils;

import java.util.*;

public class HelpOpCommand implements CommandExecutor, TabCompleter {

    private final PermaPiola plugin;
    private final LanguageManager lang;
    private final EmoteManager emoteManager;
    private final Map<UUID, Long> cooldowns = new HashMap<>();

    public HelpOpCommand(PermaPiola plugin, LanguageManager lang, EmoteManager emoteManager) {
        this.plugin = plugin;
        this.lang = lang;
        this.emoteManager = emoteManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(ColorUtils.format(lang.getMsg(sender, "commands.generic.console-only")));
            return true;
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
                return true;
            }
        }

        if (args.length == 0) {
            pSender.sendMessage(ColorUtils.format(lang.getMsg(sender, "commands.helpop.usage")));
            return true;
        }

        String message = String.join(" ", args);
        message = emoteManager.translateEmotes(pSender, message);

        String finalMessage = pSender.hasPermission("permapiola.donor.color") ? ColorUtils.format("&f" + message) : message;

        String senderFormat = LuckPermsUtils.getPrefix(pSender) + pSender.getName() + LuckPermsUtils.getSuffix(pSender);

        String formatBase = lang.getMsg(pSender, "commands.helpop.format");
        String fullMessage = ColorUtils.format(formatBase
                .replace("%player_format%", senderFormat)
                .replace("%message%", finalMessage));

        pSender.sendMessage(fullMessage);

        for (Player onlinePlayer : Bukkit.getOnlinePlayers()) {
            if (onlinePlayer.hasPermission("permapiola.admin.helpop") && !onlinePlayer.equals(pSender)) {
                onlinePlayer.sendMessage(fullMessage);
                onlinePlayer.playSound(onlinePlayer.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 100f, 1.5f);
            }
        }

        if (cooldownTimeMillis > 0) cooldowns.put(playerUUID, System.currentTimeMillis());
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        return new ArrayList<>();
    }
}