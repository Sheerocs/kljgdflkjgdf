package pp.sheero.permapiola.utilidad.commands;

import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import pp.sheero.permapiola.managers.EmoteManager;
import pp.sheero.permapiola.managers.LanguageManager;
import pp.sheero.permapiola.utils.ColorUtils;
import pp.sheero.permapiola.utils.LuckPermsUtils;
import pp.sheero.permapiola.utils.ReplyManager;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class ReplyCommand implements CommandExecutor, TabCompleter {

    private final LanguageManager lang;
    private final EmoteManager emoteManager;

    public ReplyCommand(LanguageManager lang, EmoteManager emoteManager) {
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

        boolean isDead = pp.sheero.permapiola.utils.DeathStateManager.isDead(pSender.getUniqueId());
        boolean isStaff = pSender.hasPermission("permapiola.admin") || pSender.hasPermission("permapiola.staff");

        if (isDead && !isStaff) {
            pSender.sendMessage(ColorUtils.format(lang.getMsg(pSender, "commands.generic.no-permission")));
            return true;
        }

        if (args.length == 0) {
            pSender.sendMessage(ColorUtils.format(lang.getMsg(sender, "commands.reply.usage")));
            return true;
        }

        UUID targetUUID = ReplyManager.getReplyTarget(pSender.getUniqueId());
        if (targetUUID == null) {
            pSender.sendMessage(ColorUtils.format(lang.getMsg(sender, "commands.reply.no-target")));
            return true;
        }

        Player target = Bukkit.getPlayer(targetUUID);
        if (target == null || !target.isOnline()) {
            pSender.sendMessage(ColorUtils.format(lang.getMsg(sender, "commands.generic.player-offline")));
            return true;
        }

        String rawMessage = String.join(" ", args);
        rawMessage = emoteManager.translateEmotes(pSender, rawMessage);

        boolean isSenderDonor = pSender.hasPermission("permapiola.donor.color");
        String pathType = isSenderDonor ? "donator" : "default";

        String formatToPath = "private.format-to." + pathType;
        String formatFromPath = "private.format-from." + pathType;

        String formattedMessage = isSenderDonor ? ColorUtils.format(rawMessage) : rawMessage;

        String rawFormatTo = lang.getMsg(pSender, formatToPath);
        String rawFormatFrom = lang.getMsg(target, formatFromPath);

        String toMessage = rawFormatTo
                .replace("%target_prefix%", LuckPermsUtils.getPrefix(target))
                .replace("%target_suffix%", LuckPermsUtils.getSuffix(target))
                .replace("%target%", target.getName())
                .replace("%message%", formattedMessage);

        String fromMessage = rawFormatFrom
                .replace("%sender_prefix%", LuckPermsUtils.getPrefix(pSender))
                .replace("%sender_suffix%", LuckPermsUtils.getSuffix(pSender))
                .replace("%sender%", pSender.getName())
                .replace("%message%", formattedMessage);

        pSender.sendMessage(ColorUtils.format(toMessage));
        target.sendMessage(ColorUtils.format(fromMessage));
        target.playSound(target.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 100f, 0.5f);

        ReplyManager.setReplyTarget(pSender.getUniqueId(), target.getUniqueId());
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        return new ArrayList<>();
    }
}