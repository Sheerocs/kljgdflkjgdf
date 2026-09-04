package pp.sheero.permapiola.teams;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import pp.sheero.permapiola.PermaPiola;
import pp.sheero.permapiola.core.LanguageManager;
import pp.sheero.permapiola.utils.ColorUtils;
import pp.sheero.permapiola.utils.LuckPermsUtils;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class ReTeamManager {

    private static final Map<UUID, PiolaReTeam> reteamsById = new ConcurrentHashMap<>();
    private static final Map<UUID, UUID> playerToReTeam = new ConcurrentHashMap<>();
    private static final Map<String, Integer> activeReTeamInvites = new ConcurrentHashMap<>();

    private static boolean reteamSystemEnabled = false;
    private static String teamChatFormat;

    public static void loadConfigCache(PermaPiola plugin) {
        reteamSystemEnabled = plugin.getConfig().getBoolean("teams.reteam", false);
        teamChatFormat = plugin.getConfig().getString("chat.team-format", "&8[&bTeamChat&8] %player_prefix%%player%&f: %message%");
    }

    public static boolean isReteamSystemEnabled() { return reteamSystemEnabled; }

    // ==========================================
    // LÓGICA PRINCIPAL (POO)
    // ==========================================

    public static PiolaReTeam getReTeam(Player player) {
        UUID reteamId = playerToReTeam.get(player.getUniqueId());
        return reteamId != null ? reteamsById.get(reteamId) : null;
    }

    public static boolean hasReTeam(Player player) {
        return playerToReTeam.containsKey(player.getUniqueId());
    }

    public static boolean isReTeamNameTaken(String cleanName) {
        String query = cleanName.toLowerCase();
        for (PiolaReTeam reteam : reteamsById.values()) {
            if (reteam.getName().equals(query) || reteam.getDisplayName().equalsIgnoreCase(query)) {
                return true;
            }
        }
        return false;
    }

    public static PiolaReTeam createReTeam(Player creator, String name, String displayName, String tag, List<String> originalTeams) {
        PiolaReTeam newReTeam = new PiolaReTeam(creator.getUniqueId(), name, displayName, tag, originalTeams);
        reteamsById.put(newReTeam.getReteamId(), newReTeam);
        playerToReTeam.put(creator.getUniqueId(), newReTeam.getReteamId());
        return newReTeam;
    }

    public static void addPlayerToReTeam(UUID playerUuid, PiolaReTeam reteam) {
        reteam.addMember(playerUuid);
        playerToReTeam.put(playerUuid, reteam.getReteamId());
    }

    public static void deleteReTeamForcefully(PiolaReTeam reteam) {
        if (reteam == null) return;
        for (UUID memberId : reteam.getMembers()) {
            playerToReTeam.remove(memberId);
        }
        reteamsById.remove(reteam.getReteamId());
    }

    public static Collection<PiolaReTeam> getAllReTeams() {
        return reteamsById.values();
    }

    public static int getAliveMembersCount(PiolaTeam team) {
        int aliveCount = 0;
        for (UUID memberId : team.getMembers()) {
            if (!pp.sheero.permapiola.hurricane.DeathStateManager.isDead(memberId)) {
                aliveCount++;
            }
        }
        return aliveCount;
    }

    // ==========================================
    // CHAT DE ALIANZA
    // ==========================================

    public static void sendReTeamChatMessage(Player sender, String message, LanguageManager languageManager) {
        PiolaReTeam reteam = getReTeam(sender);
        if (reteam == null) return;

        if (!sender.hasPermission("permapiola.donor.color")) {
            message = ColorUtils.stripColors(message);
        }

        String prefix = LuckPermsUtils.getPrefix(sender);
        if (prefix == null) prefix = "";

        String format = teamChatFormat
                .replace("%player_prefix%", prefix)
                .replace("%player%", sender.getName())
                .replace("%message%", message);

        reteam.broadcast(format);

        String spyFormatRaw = languageManager.getMsg(Bukkit.getConsoleSender(), "teams.staff.spy-format");
        String spyMessage = ColorUtils.format(spyFormatRaw
                .replace("%team_name%", reteam.getDisplayName())
                .replace("%player_prefix%", prefix)
                .replace("%player%", sender.getName())
                .replace("%message%", message));

        Bukkit.getConsoleSender().sendMessage(spyMessage.replace("&8[&cSPY&8] ", ""));

        for (Player onlinePlayer : Bukkit.getOnlinePlayers()) {
            if (TeamManager.hasSpyEnabled(onlinePlayer) && !reteam.hasMember(onlinePlayer.getUniqueId())) {
                onlinePlayer.sendMessage(spyMessage);
            }
        }
    }

    // ==========================================
    // SISTEMA DE INVITACIONES DE RETEAM
    // ==========================================

    private static String getInviteKey(UUID inviterLeader, String targetTeamName) {
        return targetTeamName.toLowerCase() + "_" + inviterLeader.toString();
    }

    public static void addInvite(Player inviterLeader, String targetTeamName, int taskId) {
        activeReTeamInvites.put(getInviteKey(inviterLeader.getUniqueId(), targetTeamName), taskId);
    }

    public static boolean hasInvite(Player inviterLeader, String targetTeamName) {
        return activeReTeamInvites.containsKey(getInviteKey(inviterLeader.getUniqueId(), targetTeamName));
    }

    public static void removeInvite(Player inviterLeader, String targetTeamName) {
        Integer taskId = activeReTeamInvites.remove(getInviteKey(inviterLeader.getUniqueId(), targetTeamName));
        if (taskId != null) Bukkit.getScheduler().cancelTask(taskId);
    }
}