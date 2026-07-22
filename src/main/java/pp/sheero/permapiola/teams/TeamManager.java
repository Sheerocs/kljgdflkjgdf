package pp.sheero.permapiola.teams;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;
import pp.sheero.permapiola.PermaPiola;
import pp.sheero.permapiola.managers.LanguageManager;
import pp.sheero.permapiola.utils.ColorUtils;
import pp.sheero.permapiola.utils.LuckPermsUtils;

import java.io.File;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class TeamManager {

    private static final Map<String, Integer> activeInvites = new ConcurrentHashMap<>();
    private static final Map<String, String> teamLeaders = new ConcurrentHashMap<>();
    private static final Set<UUID> spyPlayers = Collections.newSetFromMap(new ConcurrentHashMap<>());
    private static final Set<UUID> glowPlayers = Collections.newSetFromMap(new ConcurrentHashMap<>());

    private static boolean teamsEnabled;
    private static boolean friendlyFireDefault;
    private static int defaultMaxSize;
    private static String teamChatFormat;

    public static void loadConfigCache(PermaPiola plugin) {
        org.bukkit.configuration.file.FileConfiguration config = plugin.getConfig();
        teamsEnabled = config.getBoolean("teams.enabled", true);
        friendlyFireDefault = config.getBoolean("teams.friendly-fire", false);
        defaultMaxSize = config.getInt("teams.max-size", 5);
        teamChatFormat = config.getString("chat.team-format", "&8[&bTeamChat&8] &7%player_prefix%%player%%player_suffix%&f: %message%");
    }

    public static boolean isTeamsEnabled() { return teamsEnabled; }
    public static int getMaxSize() { return defaultMaxSize; }

    public static Scoreboard getMainScoreboard() { return Bukkit.getScoreboardManager().getMainScoreboard(); }
    public static Team getTeam(Player player) { return getMainScoreboard().getEntryTeam(player.getName()); }
    public static Team getTeamByName(String teamName) { return getMainScoreboard().getTeam(teamName); }
    public static boolean hasTeam(Player player) { return getMainScoreboard().getEntryTeam(player.getName()) != null; }
    public static boolean isTeamNameTaken(String cleanName) { return getMainScoreboard().getTeam(cleanName) != null; }

    public static Team createTeam(Player creator, String cleanName, String displayName, PermaPiola plugin) {
        if (hasTeam(creator)) return null;

        Scoreboard board = getMainScoreboard();
        Team vanillaTeam = board.registerNewTeam(cleanName);

        vanillaTeam.setDisplayName(ColorUtils.format(displayName));
        vanillaTeam.setAllowFriendlyFire(friendlyFireDefault);
        vanillaTeam.setCanSeeFriendlyInvisibles(true);
        vanillaTeam.addEntry(creator.getName());

        setTeamLeader(vanillaTeam, creator.getName());
        return vanillaTeam;
    }

    public static void setGlobalFriendlyFire(boolean enabled) {
        for (Team team : getMainScoreboard().getTeams()) team.setAllowFriendlyFire(enabled);
    }

    public static void removePlayerFromTeam(Player player) {
        Team team = getTeam(player);
        if (team == null) return;

        String teamName = team.getName();
        String leader = teamLeaders.get(teamName);

        if (player.getName().equals(leader)) {
            team.unregister();
            teamLeaders.remove(teamName);
        } else {
            team.removeEntry(player.getName());
        }
    }

    private static String getInviteKey(UUID inviter, UUID target) { return target.toString() + "_" + inviter.toString(); }
    public static void addInvite(Player inviter, Player target, int taskId) { activeInvites.put(getInviteKey(inviter.getUniqueId(), target.getUniqueId()), taskId); }
    public static boolean hasInvite(Player inviter, Player target) { return activeInvites.containsKey(getInviteKey(inviter.getUniqueId(), target.getUniqueId())); }

    public static void removeInvite(Player inviter, Player target) {
        Integer taskId = activeInvites.remove(getInviteKey(inviter.getUniqueId(), target.getUniqueId()));
        if (taskId != null) Bukkit.getScheduler().cancelTask(taskId);
    }

    public static String getTeamLeader(Team team) {
        String leader = teamLeaders.get(team.getName());
        if (leader == null && !team.getEntries().isEmpty()) {
            leader = team.getEntries().iterator().next();
            teamLeaders.put(team.getName(), leader);
        }
        return leader;
    }

    public static void setTeamLeader(Team team, String playerName) { teamLeaders.put(team.getName(), playerName); }

    public static void resetAllTeams() {
        Scoreboard board = getMainScoreboard();
        for (Team team : new HashSet<>(board.getTeams())) {
            String internalTeamName = team.getName();
            for (String entry : team.getEntries()) LuckPermsUtils.removePlayerFromGroup(entry, internalTeamName);
            LuckPermsUtils.deleteTeamGroup(internalTeamName);
            team.unregister();
        }
        teamLeaders.clear();
        for (int taskId : activeInvites.values()) Bukkit.getScheduler().cancelTask(taskId);
        activeInvites.clear();
    }

    public static boolean hasSpyEnabled(Player player) { return spyPlayers.contains(player.getUniqueId()); }
    public static void toggleSpy(Player player) {
        if (hasSpyEnabled(player)) spyPlayers.remove(player.getUniqueId());
        else spyPlayers.add(player.getUniqueId());
    }

    public static void sendTeamChatMessage(Player sender, String message, LanguageManager languageManager) {
        Team team = getTeam(sender);
        if (team == null) return;

        String rawPrefix = LuckPermsUtils.getPrefix(sender);
        String rawSuffix = LuckPermsUtils.getSuffix(sender);
        String cleanPrefix = rawPrefix != null ? LuckPermsUtils.cleanTeamTag(rawPrefix, team) : "";
        String cleanSuffix = rawSuffix != null ? LuckPermsUtils.cleanTeamTag(rawSuffix, team) : "";

        String configFormat = teamChatFormat;

        for (String entry : team.getEntries()) {
            Player member = Bukkit.getPlayerExact(entry);
            if (member != null && member.isOnline()) {
                String format = configFormat
                        .replace("%player_prefix%", cleanPrefix)
                        .replace("%player_suffix%", cleanSuffix)
                        .replace("%player%", sender.getName())
                        .replace("%message%", message);
                member.sendMessage(ColorUtils.format(format));
            }
        }

        String consoleSpyFormat = languageManager.getMsg(Bukkit.getConsoleSender(), "teams.staff.spy-format");
        String finalConsoleMessage = ColorUtils.format(consoleSpyFormat
                .replace("&8[&cSPY&8] ", "")
                .replace("%team_name%", team.getDisplayName())
                .replace("%player_prefix%", cleanPrefix)
                .replace("%player_suffix%", cleanSuffix)
                .replace("%player%", sender.getName())
                .replace("%message%", message));

        Bukkit.getConsoleSender().sendMessage(finalConsoleMessage);

        for (Player onlinePlayer : Bukkit.getOnlinePlayers()) {
            if (hasSpyEnabled(onlinePlayer) && !team.hasEntry(onlinePlayer.getName())) {
                String spyFormatRaw = languageManager.getMsg(onlinePlayer, "teams.staff.spy-format");
                String finalSpyMessage = ColorUtils.format(spyFormatRaw
                        .replace("%team_name%", team.getDisplayName())
                        .replace("%player_prefix%", cleanPrefix)
                        .replace("%player_suffix%", cleanSuffix)
                        .replace("%player%", sender.getName())
                        .replace("%message%", message));
                onlinePlayer.sendMessage(finalSpyMessage);
            }
        }
    }

    public static void saveData(PermaPiola plugin) {
        File dataFolder = new File(plugin.getDataFolder(), "data");
        if (!dataFolder.exists()) {
            dataFolder.mkdirs();
        }

        File dataFile = new File(dataFolder, "team_spy_data.yml");

        List<String> spySnapshot = new ArrayList<>();
        for (UUID uuid : spyPlayers) {
            spySnapshot.add(uuid.toString());
        }

        org.bukkit.configuration.file.YamlConfiguration config = new org.bukkit.configuration.file.YamlConfiguration();
        config.set("spy-players", spySnapshot);

        try {
            config.save(dataFile);
        } catch (Exception ignored) {}
    }

    public static void loadData(PermaPiola plugin) {
        File dataFolder = new File(plugin.getDataFolder(), "data");
        File dataFile = new File(dataFolder, "team_spy_data.yml");
        if (!dataFile.exists()) return;

        org.bukkit.configuration.file.YamlConfiguration config = org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(dataFile);
        spyPlayers.clear();
        for (String uuidStr : config.getStringList("spy-players")) {
            try { spyPlayers.add(UUID.fromString(uuidStr)); } catch (Exception ignored) {}
        }
    }

    public static boolean hasGlowEnabled(Player player) { return glowPlayers.contains(player.getUniqueId()); }
    public static void toggleGlow(Player player) {
        if (hasGlowEnabled(player)) glowPlayers.remove(player.getUniqueId());
        else glowPlayers.add(player.getUniqueId());
    }
}