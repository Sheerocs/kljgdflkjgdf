package pp.sheero.permapiola.utils;

import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.model.user.User;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import pp.sheero.permapiola.PermaPiola;
import pp.sheero.permapiola.hurricane.DeathStateManager;
import pp.sheero.permapiola.teams.PiolaTeam;

public class LuckPermsUtils {

    // Dejamos el método vacío para no causar errores de compilación en PermaPiola.java
    public static void registerListeners(PermaPiola plugin) {
    }

    public static String getPrefix(Player player) {
        LuckPerms api = LuckPermsProvider.get();
        User user = api.getUserManager().getUser(player.getUniqueId());
        return (user != null && user.getCachedData().getMetaData().getPrefix() != null)
                ? user.getCachedData().getMetaData().getPrefix() : "";
    }

    public static String getSuffix(Player player) {
        LuckPerms api = LuckPermsProvider.get();
        User user = api.getUserManager().getUser(player.getUniqueId());
        return (user != null && user.getCachedData().getMetaData().getSuffix() != null)
                ? user.getCachedData().getMetaData().getSuffix() : "";
    }

    public static String getPrefixForOffline(String playerName) {
        @SuppressWarnings("deprecation")
        OfflinePlayer op = Bukkit.getOfflinePlayer(playerName);
        LuckPerms api = LuckPermsProvider.get();
        User user = api.getUserManager().loadUser(op.getUniqueId()).join();
        if (user != null && user.getCachedData().getMetaData().getPrefix() != null) {
            return user.getCachedData().getMetaData().getPrefix();
        }
        return "";
    }

    public static String getSuffixForOffline(String playerName) {
        @SuppressWarnings("deprecation")
        OfflinePlayer op = Bukkit.getOfflinePlayer(playerName);
        LuckPerms api = LuckPermsProvider.get();
        User user = api.getUserManager().loadUser(op.getUniqueId()).join();
        if (user != null && user.getCachedData().getMetaData().getSuffix() != null) {
            return user.getCachedData().getMetaData().getSuffix();
        }
        return "";
    }

    public static String getFormattedName(Player player) {
        return getPrefix(player) + player.getName() + getSuffix(player);
    }

    public static boolean hasSpecialRank(Player player) {
        LuckPerms api = LuckPermsProvider.get();
        User user = api.getUserManager().getUser(player.getUniqueId());
        if (user == null) return false;
        return !user.getPrimaryGroup().equalsIgnoreCase("default");
    }

    public static String getRankedNameOnly(Player player) {
        return getPrefix(player) + player.getName();
    }

    public static String formatPlayerForList(String playerName, PiolaTeam team, CommandSender viewer, PermaPiola plugin) {
        Player memberObj = Bukkit.getPlayerExact(playerName);
        @SuppressWarnings("deprecation")
        java.util.UUID memberUuid = Bukkit.getOfflinePlayer(playerName).getUniqueId();

        String icon;
        String finalPrefix;
        String finalSuffix;

        if (DeathStateManager.isDead(memberUuid)) {
            icon = plugin.getLanguageManager().getMsg(viewer, "teams.memberlist.icons.dead");
            finalPrefix = (memberObj != null) ? getPrefix(memberObj) : getPrefixForOffline(playerName);
            finalSuffix = (memberObj != null) ? getSuffix(memberObj) : getSuffixForOffline(playerName);
        } else if (memberObj != null && memberObj.isOnline()) {
            icon = plugin.getLanguageManager().getMsg(viewer, "teams.memberlist.icons.online");
            finalPrefix = getPrefix(memberObj);
            finalSuffix = getSuffix(memberObj);
        } else {
            icon = plugin.getLanguageManager().getMsg(viewer, "teams.memberlist.icons.offline");
            finalPrefix = getPrefixForOffline(playerName);
            finalSuffix = getSuffixForOffline(playerName);
        }

        // Ya no es necesario el cleanTeamTag()

        return plugin.getLanguageManager().getMsg(viewer, "teams.memberlist.player-format")
                .replace("%status%", icon)
                .replace("%player_prefix%", finalPrefix != null ? finalPrefix : "")
                .replace("%player_suffix%", finalSuffix != null ? finalSuffix : "")
                .replace("%player%", playerName);
    }
}