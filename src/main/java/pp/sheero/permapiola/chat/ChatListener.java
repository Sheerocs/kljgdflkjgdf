package pp.sheero.permapiola.chat;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import pp.sheero.permapiola.PermaPiola;
import pp.sheero.permapiola.core.LanguageManager;
import pp.sheero.permapiola.teams.TeamManager;
import pp.sheero.permapiola.utils.ColorUtils;
import pp.sheero.permapiola.utils.LuckPermsUtils;
import pp.sheero.permapiola.hurricane.DeathStateManager;

public class ChatListener implements Listener {

    private final PermaPiola plugin;
    private final ChatManager chatManager;
    private final LanguageManager lang;
    private final EmoteManager emoteManager;

    public ChatListener(PermaPiola plugin, ChatManager chatManager, LanguageManager lang, EmoteManager emoteManager) {
        this.plugin = plugin;
        this.chatManager = chatManager;
        this.lang = lang;
        this.emoteManager = emoteManager;
    }

    @SuppressWarnings("deprecation")
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        String message = event.getMessage();
        message = emoteManager.translateEmotes(player, message);
        ChatChannel channel = chatManager.getChannel(player);

        boolean isDead = DeathStateManager.isDead(player.getUniqueId());
        boolean isStaff = player.hasPermission("permapiola.admin") || player.hasPermission("permapiola.staff");

        if (isDead && !isStaff) {
            channel = ChatChannel.SPEC;
        }

        String symbol = chatManager.getOverrideSymbol();
        if (chatManager.isOverrideEnabled() && message.startsWith(symbol)) {
            if (!isDead || isStaff) {
                message = message.substring(1);
                channel = ChatChannel.ALL;
            }
        }

        if (channel == ChatChannel.STAFF) {
            event.setCancelled(true);
            chatManager.sendStaffMessage(player, message);
        } else if (channel == ChatChannel.TEAM) {
            event.setCancelled(true);
            if (TeamManager.hasTeam(player)) {
                TeamManager.sendTeamChatMessage(player, message, lang);
            } else {
                player.sendMessage(ColorUtils.format(lang.getMsg(player, "teams.not-in-team")));
                chatManager.setChannel(player, ChatChannel.ALL);
            }
        } else if (channel == ChatChannel.SPEC) {
            event.setCancelled(true);

            String format = player.hasPermission("permapiola.donor.color")
                    ? chatManager.getSpecFormatDonator()
                    : chatManager.getSpecFormatDefault();

            String prefix = LuckPermsUtils.getPrefix(player);
            String suffix = LuckPermsUtils.getSuffix(player);

            String formattedMessageText = player.hasPermission("permapiola.donor.color") ? ColorUtils.format(message) : message;

            String finalFormat = format.replace("%player_prefix%", prefix != null ? prefix : "")
                    .replace("%player_suffix%", suffix != null ? suffix : "")
                    .replace("%player%", player.getName())
                    .replace("%message%", formattedMessageText);

            String coloredMessage = ColorUtils.format(finalFormat);

            for (Player p : Bukkit.getOnlinePlayers()) {
                if (DeathStateManager.isDead(p.getUniqueId()) || p.hasPermission("permapiola.admin") || p.hasPermission("permapiola.staff")) {
                    p.sendMessage(coloredMessage);
                }
            }
        } else {
            String format = player.hasPermission("permapiola.donor.color")
                    ? chatManager.getGlobalFormatDonator()
                    : chatManager.getGlobalFormatDefault();

            String prefix = LuckPermsUtils.getPrefix(player);
            String suffix = LuckPermsUtils.getSuffix(player);

            String finalFormat = format.replace("%player_prefix%", prefix != null ? prefix : "")
                    .replace("%player_suffix%", suffix != null ? suffix : "")
                    .replace("%player%", player.getName())
                    .replace("%message%", "%2$s");

            event.setFormat(ColorUtils.format(finalFormat));
            if (player.hasPermission("permapiola.donor.color")) {
                event.setMessage(ColorUtils.format(message));
            }
        }
    }
}