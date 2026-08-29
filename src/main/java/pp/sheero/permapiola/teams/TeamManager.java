package pp.sheero.permapiola.teams;

import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import pp.sheero.permapiola.PermaPiola;
import pp.sheero.permapiola.core.LanguageManager;
import pp.sheero.permapiola.utils.ColorUtils;

import java.io.File;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class TeamManager {

    private static final Map<UUID, PiolaTeam> teamsById = new ConcurrentHashMap<>();
    private static final Map<UUID, UUID> playerToTeam = new ConcurrentHashMap<>();
    private static final Map<String, Integer> activeInvites = new ConcurrentHashMap<>();
    private static final Set<UUID> spyPlayers = Collections.newSetFromMap(new ConcurrentHashMap<>());
    private static final Set<UUID> glowPlayers = Collections.newSetFromMap(new ConcurrentHashMap<>());
    private static boolean teamsEnabled;
    private static int defaultMaxSize;
    private static String teamChatFormat;

    public static void loadConfigCache(PermaPiola plugin) {
        org.bukkit.configuration.file.FileConfiguration config = plugin.getConfig();
        teamsEnabled = config.getBoolean("teams.enabled", true);
        defaultMaxSize = config.getInt("teams.max-size", 6);
        teamChatFormat = config.getString("chat.team-format", "&8[&bTeamChat&8] &7%player_format%&f: %message%");
    }

    public static boolean isTeamsEnabled() { return teamsEnabled; }
    public static int getMaxSize() { return defaultMaxSize; }

    // ==========================================
    // LÓGICA PRINCIPAL (POO)
    // ==========================================

    public static PiolaTeam getTeam(Player player) {
        UUID teamId = playerToTeam.get(player.getUniqueId());
        return teamId != null ? teamsById.get(teamId) : null;
    }

    public static PiolaTeam getTeamByName(String name) {
        String cleanQuery = name.toLowerCase();
        for (PiolaTeam team : teamsById.values()) {
            if (team.getName().equals(cleanQuery) || team.getDisplayName().equalsIgnoreCase(cleanQuery)) {
                return team;
            }
        }
        return null;
    }

    public static boolean hasTeam(Player player) {
        return playerToTeam.containsKey(player.getUniqueId());
    }

    public static boolean isTeamNameTaken(String cleanName) {
        return getTeamByName(cleanName) != null;
    }

    public static PiolaTeam createTeam(Player creator, String name, String displayName, String tag) {
        if (hasTeam(creator)) return null;

        PiolaTeam newTeam = new PiolaTeam(creator.getUniqueId(), name, displayName, tag, defaultMaxSize);

        teamsById.put(newTeam.getTeamId(), newTeam);
        playerToTeam.put(creator.getUniqueId(), newTeam.getTeamId());

        return newTeam;
    }

    public static void addPlayerToTeam(Player player, PiolaTeam team) {
        team.addMember(player.getUniqueId());
        playerToTeam.put(player.getUniqueId(), team.getTeamId());
    }

    public static void removePlayerFromTeam(Player player) {
        PiolaTeam team = getTeam(player);
        if (team == null) return;

        if (team.isLeader(player.getUniqueId())) {
            for (UUID memberId : team.getMembers()) {
                playerToTeam.remove(memberId);
            }
            teamsById.remove(team.getTeamId());
        } else {
            team.removeMember(player.getUniqueId());
            playerToTeam.remove(player.getUniqueId());
        }
    }

    public static void deleteTeamForcefully(PiolaTeam team) {
        if (team == null) return;
        for (UUID memberId : team.getMembers()) {
            playerToTeam.remove(memberId);
        }
        teamsById.remove(team.getTeamId());
    }

    public static void resetAllTeams() {
        teamsById.clear();
        playerToTeam.clear();
        for (int taskId : activeInvites.values()) Bukkit.getScheduler().cancelTask(taskId);
        activeInvites.clear();
    }

    public static Collection<PiolaTeam> getAllTeams() {
        return teamsById.values();
    }

    // ==========================================
    // SISTEMA DE INVITACIONES
    // ==========================================

    private static String getInviteKey(UUID inviter, UUID target) { return target.toString() + "_" + inviter.toString(); }

    public static void addInvite(Player inviter, Player target, int taskId) {
        activeInvites.put(getInviteKey(inviter.getUniqueId(), target.getUniqueId()), taskId);
    }

    public static boolean hasInvite(Player inviter, Player target) {
        return activeInvites.containsKey(getInviteKey(inviter.getUniqueId(), target.getUniqueId()));
    }

    public static void removeInvite(Player inviter, Player target) {
        Integer taskId = activeInvites.remove(getInviteKey(inviter.getUniqueId(), target.getUniqueId()));
        if (taskId != null) Bukkit.getScheduler().cancelTask(taskId);
    }

    // ==========================================
    // SISTEMAS SECUNDARIOS (SPY, GLOW, CHAT)
    // ==========================================

    public static boolean hasSpyEnabled(Player player) { return spyPlayers.contains(player.getUniqueId()); }
    public static void toggleSpy(Player player) {
        if (hasSpyEnabled(player)) spyPlayers.remove(player.getUniqueId());
        else spyPlayers.add(player.getUniqueId());
    }

    public static boolean hasGlowEnabled(Player player) { return glowPlayers.contains(player.getUniqueId()); }
    public static void toggleGlow(Player player) {
        if (hasGlowEnabled(player)) glowPlayers.remove(player.getUniqueId());
        else glowPlayers.add(player.getUniqueId());
    }

    public static void sendTeamChatMessage(Player sender, String message, LanguageManager languageManager) {
        PiolaTeam team = getTeam(sender);
        if (team == null) return;

        String format = teamChatFormat
                .replace("%player_format%", sender.getName())
                .replace("%message%", message);

        team.broadcast(format);

        String spyFormatRaw = languageManager.getMsg(Bukkit.getConsoleSender(), "teams.staff.spy-format");
        String spyMessage = ColorUtils.format(spyFormatRaw
                .replace("%team_name%", team.getDisplayName())
                .replace("%player_format%", sender.getName())
                .replace("%message%", message));

        Bukkit.getConsoleSender().sendMessage(spyMessage.replace("&8[&cSPY&8] ", ""));

        for (Player onlinePlayer : Bukkit.getOnlinePlayers()) {
            if (hasSpyEnabled(onlinePlayer) && !team.hasMember(onlinePlayer.getUniqueId())) {
                onlinePlayer.sendMessage(spyMessage);
            }
        }
    }

    // ==========================================
    // ALMACENAMIENTO DE DATOS (YML)
    // ==========================================

    public static void saveData(PermaPiola plugin) {
        File dataFolder = new File(plugin.getDataFolder(), "data");
        if (!dataFolder.exists()) dataFolder.mkdirs();

        File spyFile = new File(dataFolder, "team_spy_data.yml");
        YamlConfiguration spyConfig = new YamlConfiguration();
        List<String> spySnapshot = new ArrayList<>();
        for (UUID uuid : spyPlayers) spySnapshot.add(uuid.toString());
        spyConfig.set("spy-players", spySnapshot);
        try { spyConfig.save(spyFile); } catch (Exception ignored) {}

        File teamsFile = new File(dataFolder, "teams.yml");
        YamlConfiguration teamsConfig = new YamlConfiguration();

        for (PiolaTeam team : teamsById.values()) {
            String path = "teams." + team.getTeamId().toString();
            teamsConfig.set(path + ".name", team.getName());
            teamsConfig.set(path + ".displayName", team.getDisplayName());
            teamsConfig.set(path + ".tag", team.getTag());
            teamsConfig.set(path + ".leader", team.getLeader().toString());
            teamsConfig.set(path + ".maxSize", team.getMaxSize());
            teamsConfig.set(path + ".totalPlaytime", team.getTotalPlaytime());
            teamsConfig.set(path + ".totalTotems", team.getTotalTotems());

            for (UUID memberUuid : team.getMembers()) {
                String memberName = "Desconocido";
                Player p = Bukkit.getPlayer(memberUuid);

                if (p != null) {
                    memberName = p.getName();
                } else {
                    @SuppressWarnings("deprecation")
                    org.bukkit.OfflinePlayer op = Bukkit.getOfflinePlayer(memberUuid);
                    if (op.getName() != null) memberName = op.getName();
                }
                teamsConfig.set(path + ".members." + memberUuid.toString(), memberName);
            }
        }
        try { teamsConfig.save(teamsFile); } catch (Exception ignored) {}
    }

    public static void loadData(PermaPiola plugin) {
        File dataFolder = new File(plugin.getDataFolder(), "data");

        File spyFile = new File(dataFolder, "team_spy_data.yml");
        if (spyFile.exists()) {
            YamlConfiguration spyConfig = YamlConfiguration.loadConfiguration(spyFile);
            spyPlayers.clear();
            for (String uuidStr : spyConfig.getStringList("spy-players")) {
                try { spyPlayers.add(UUID.fromString(uuidStr)); } catch (Exception ignored) {}
            }
        }

        File teamsFile = new File(dataFolder, "teams.yml");
        if (teamsFile.exists()) {
            YamlConfiguration teamsConfig = YamlConfiguration.loadConfiguration(teamsFile);
            teamsById.clear();
            playerToTeam.clear();

            if (teamsConfig.contains("teams")) {
                for (String key : teamsConfig.getConfigurationSection("teams").getKeys(false)) {
                    try {
                        UUID teamId = UUID.fromString(key);
                        String path = "teams." + key;

                        String name = teamsConfig.getString(path + ".name");
                        String displayName = teamsConfig.getString(path + ".displayName");
                        String tag = teamsConfig.getString(path + ".tag");
                        UUID leader = UUID.fromString(teamsConfig.getString(path + ".leader"));
                        int maxSize = teamsConfig.getInt(path + ".maxSize", defaultMaxSize);
                        long totalPlaytime = teamsConfig.getLong(path + ".totalPlaytime", 0L);
                        int totalTotems = teamsConfig.getInt(path + ".totalTotems", 0);

                        Set<UUID> members = new HashSet<>();
                        if (teamsConfig.contains(path + ".members")) {
                            for (String memberUuidStr : teamsConfig.getConfigurationSection(path + ".members").getKeys(false)) {
                                members.add(UUID.fromString(memberUuidStr));
                            }
                        }

                        PiolaTeam loadedTeam = new PiolaTeam(teamId, leader, name, displayName, tag, members, maxSize, totalPlaytime, totalTotems);
                        teamsById.put(teamId, loadedTeam);

                        for (UUID member : members) {
                            playerToTeam.put(member, teamId);
                        }
                    } catch (Exception e) {
                        plugin.getLogger().severe("Error cargando el equipo con ID: " + key);
                    }
                }
            }
        }
    }
}