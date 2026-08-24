package pp.sheero.permapiola.utils;

import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.model.group.Group;
import net.luckperms.api.model.user.User;
import net.luckperms.api.node.NodeType;
import net.luckperms.api.node.types.InheritanceNode;
import net.luckperms.api.node.types.PrefixNode;
import net.luckperms.api.node.types.SuffixNode;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.Team;
import pp.sheero.permapiola.PermaPiola;
import pp.sheero.permapiola.teams.TeamManager;

public class LuckPermsUtils {

    public static void registerListeners(PermaPiola plugin) {
        LuckPerms api = LuckPermsProvider.get();

        api.getEventBus().subscribe(plugin, net.luckperms.api.event.user.UserDataRecalculateEvent.class, event -> {
            User user = event.getUser();

            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                Player player = Bukkit.getPlayer(user.getUniqueId());
                if (player == null || !player.isOnline()) return;

                Team team = TeamManager.getTeam(player);
                if (team == null) return;

                boolean isRanked = hasSpecialRank(player);

                boolean hasPrefixTag = user.getNodes().stream()
                        .anyMatch(NodeType.PREFIX.predicate(n -> n.getPriority() == 9999));

                boolean hasSuffixTag = user.getNodes().stream()
                        .anyMatch(NodeType.SUFFIX.predicate(n -> n.getPriority() == 9999));

                if (isRanked && hasPrefixTag) {
                    Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
                        applyPersonalTeamTag(player, team.getDisplayName());
                    });
                }
                else if (!isRanked && hasSuffixTag) {
                    Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
                        applyPersonalTeamTag(player, team.getDisplayName());
                    });
                }
            }, 1L);
        });
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

    public static void createTeamGroupAndAddPlayer(Player player, String groupName, String teamDisplayName) {
        LuckPerms api = LuckPermsProvider.get();
        String safeGroupName = groupName.toLowerCase();

        api.getGroupManager().createAndLoadGroup(safeGroupName).thenAccept(group -> {
            group.data().clear(NodeType.PREFIX::matches);
            group.data().clear(NodeType.SUFFIX::matches);
            api.getGroupManager().saveGroup(group).thenRun(() -> addPlayerToGroup(player.getName(), groupName));
        });
    }

    public static void addPlayerToGroup(String playerName, String groupName) {
        LuckPerms api = LuckPermsProvider.get();
        @SuppressWarnings("deprecation")
        OfflinePlayer op = Bukkit.getOfflinePlayer(playerName);
        User user = api.getUserManager().loadUser(op.getUniqueId()).join();

        if (user != null) {
            InheritanceNode node = InheritanceNode.builder(groupName.toLowerCase()).build();
            user.data().add(node);
            api.getUserManager().saveUser(user);

            Player p = Bukkit.getPlayerExact(playerName);
            if (p != null) {
                Team team = TeamManager.getTeamByName(groupName);
                if (team != null) applyPersonalTeamTag(p, team.getDisplayName());
                else applyPersonalTeamTag(p, groupName);
            }
        }
    }

    public static void removePlayerFromGroup(String playerName, String groupName) {
        LuckPerms api = LuckPermsProvider.get();
        @SuppressWarnings("deprecation")
        OfflinePlayer op = Bukkit.getOfflinePlayer(playerName);
        User user = api.getUserManager().loadUser(op.getUniqueId()).join();

        if (user != null) {
            InheritanceNode node = InheritanceNode.builder(groupName).build();
            user.data().remove(node);
            user.data().clear(NodeType.PREFIX.predicate(n -> n.getPriority() == 9999));
            user.data().clear(NodeType.SUFFIX.predicate(n -> n.getPriority() == 9999));
            api.getUserManager().saveUser(user);
        }
    }

    public static void deleteTeamGroup(String groupName) {
        LuckPerms api = LuckPermsProvider.get();
        Group group = api.getGroupManager().getGroup(groupName.toLowerCase());
        if (group != null) api.getGroupManager().deleteGroup(group);
    }

    public static void applyPersonalTeamTag(Player player, String teamDisplayName) {
        LuckPerms api = LuckPermsProvider.get();
        User user = api.getUserManager().getUser(player.getUniqueId());
        if (user == null) return;

        user.data().clear(NodeType.PREFIX.predicate(n -> n.getPriority() == 9999));
        user.data().clear(NodeType.SUFFIX.predicate(n -> n.getPriority() == 9999));

        boolean isRanked = hasSpecialRank(player);

        if (!isRanked) {
            String formattedPrefix = "&8[" + teamDisplayName + "&8] &7";
            user.data().add(PrefixNode.builder(formattedPrefix, 9999).build());
        } else {
            String formattedSuffix = " &8[" + teamDisplayName + "&8]";
            user.data().add(SuffixNode.builder(formattedSuffix, 9999).build());
        }
        api.getUserManager().saveUser(user);
    }

    public static String cleanTeamTag(String prefixOrSuffix, Team team) {
        if (prefixOrSuffix == null || team == null) return prefixOrSuffix;
        String teamTagPrefix = "&8[" + team.getDisplayName() + "&8] &7";
        String teamTagSuffix = " &8[" + team.getDisplayName() + "&8]";
        return prefixOrSuffix.replace(teamTagPrefix, "").replace(teamTagSuffix, "");
    }

    public static String formatPlayerForList(String playerName, Team team, CommandSender viewer, PermaPiola plugin) {
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

        finalPrefix = cleanTeamTag(finalPrefix, team);
        finalSuffix = cleanTeamTag(finalSuffix, team);

        return plugin.getLanguageManager().getMsg(viewer, "teams.memberlist.player-format")
                .replace("%status%", icon)
                .replace("%player_prefix%", finalPrefix != null ? finalPrefix : "")
                .replace("%player_suffix%", finalSuffix != null ? finalSuffix : "")
                .replace("%player%", playerName);
    }
}