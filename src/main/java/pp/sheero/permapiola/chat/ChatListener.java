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
import pp.sheero.permapiola.teams.ReTeamManager;
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

    public static String getPlayerNameColor(Player player, PermaPiola plugin) {
        String defaultColor = plugin.getConfig().getString("rank-colors.default", "&#44D889");
        try {
            net.luckperms.api.LuckPerms api = net.luckperms.api.LuckPermsProvider.get();
            net.luckperms.api.model.user.User user = api.getUserManager().getUser(player.getUniqueId());
            if (user != null) {
                String group = user.getPrimaryGroup();
                return plugin.getConfig().getString("rank-colors." + group, defaultColor);
            }
        } catch (Exception ignored) {
        }
        return defaultColor;
    }

    public static String getPlayerTag(Player player) {
        if (ReTeamManager.hasReTeam(player)) {
            return ReTeamManager.getReTeam(player).getTag() + " ";
        } else if (TeamManager.hasTeam(player)) {
            return TeamManager.getTeam(player).getTag() + " ";
        } else {
            String lpPrefix = LuckPermsUtils.getPrefix(player);
            return (lpPrefix != null && !lpPrefix.isEmpty()) ? lpPrefix + "" : "";
        }
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

        String tag = getPlayerTag(player);
        String nameColor = getPlayerNameColor(player, plugin);
        String formattedName = nameColor + player.getName();

        if (channel == ChatChannel.STAFF) {
            event.setCancelled(true);
            chatManager.sendStaffMessage(player, message);

        } else if (channel == ChatChannel.TEAM) {
            event.setCancelled(true);
            if (ReTeamManager.hasReTeam(player)) {
                ReTeamManager.sendReTeamChatMessage(player, message, lang);
            } else if (TeamManager.hasTeam(player)) {
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

            String formattedMessageText = player.hasPermission("permapiola.donor.color") ? ColorUtils.format(message) : message;

            String baseFormat = ColorUtils.format(format
                    .replace("%player_prefix%", tag)
                    .replace("%player%", formattedName));

            String coloredMessage = baseFormat.replace("%message%", formattedMessageText);

            Bukkit.getConsoleSender().sendMessage(coloredMessage);

            for (Player p : Bukkit.getOnlinePlayers()) {
                if (DeathStateManager.isDead(p.getUniqueId()) || p.hasPermission("permapiola.admin") || p.hasPermission("permapiola.staff")) {
                    p.sendMessage(coloredMessage);
                }
            }

        } else {
            String format = player.hasPermission("permapiola.donor.color")
                    ? chatManager.getGlobalFormatDonator()
                    : chatManager.getGlobalFormatDefault();

            String finalFormat = format
                    .replace("%player_prefix%", tag)
                    .replace("%player%", formattedName)
                    .replace("%message%", "%2$s");

            event.setFormat(ColorUtils.format(finalFormat));

            if (player.hasPermission("permapiola.donor.color")) {
                event.setMessage(ColorUtils.format(message));
            } else {
                event.setMessage(message);
            }
        }
    }
}