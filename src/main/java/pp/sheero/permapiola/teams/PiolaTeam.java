package pp.sheero.permapiola.teams;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import pp.sheero.permapiola.core.LanguageManager;
import pp.sheero.permapiola.utils.ColorUtils;
import pp.sheero.permapiola.hurricane.DeathStateManager;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

public class PiolaTeam {

    private final UUID teamId;
    private String name;
    private String displayName;
    private String tag;
    private UUID leader;
    private final Set<UUID> members;
    private int maxSize;
    private long totalPlaytime;
    private int totalTotems;

    public PiolaTeam(UUID leader, String name, String displayName, String tag, int maxSize) {
        this.teamId = UUID.randomUUID();
        this.leader = leader;
        this.name = name.toLowerCase();
        this.displayName = displayName;
        this.tag = tag;
        this.maxSize = maxSize;

        this.members = new HashSet<>();
        this.members.add(leader);

        this.totalPlaytime = 0L;
        this.totalTotems = 0;
    }

    public PiolaTeam(UUID teamId, UUID leader, String name, String displayName, String tag, Set<UUID> members, int maxSize, long totalPlaytime, int totalTotems) {
        this.teamId = teamId;
        this.leader = leader;
        this.name = name;
        this.displayName = displayName;
        this.tag = tag;
        this.members = members;
        this.maxSize = maxSize;
        this.totalPlaytime = totalPlaytime;
        this.totalTotems = totalTotems;
    }

    // ==========================================
    // LÓGICA DE MIEMBROS Y MENSAJERÍA
    // ==========================================

    public void addMember(UUID uuid) { this.members.add(uuid); }
    public void removeMember(UUID uuid) { this.members.remove(uuid); }
    public boolean hasMember(UUID uuid) { return this.members.contains(uuid); }
    public boolean isLeader(UUID uuid) { return this.leader.equals(uuid); }
    public boolean isFull() { return this.members.size() >= this.maxSize; }

    public void broadcast(String message) {
        for (UUID memberUuid : members) {
            Player p = Bukkit.getPlayer(memberUuid);
            if (p != null && p.isOnline()) {
                p.sendMessage(message);
            }
        }
    }

    // ==========================================
    // LÓGICA DE SUCESIÓN (Permadeath)
    // ==========================================

    public void electNewLeader(LanguageManager lang) {
        List<UUID> aliveMembers = new ArrayList<>();

        for (UUID member : members) {
            if (!member.equals(this.leader) && !DeathStateManager.isDead(member)) {
                aliveMembers.add(member);
            }
        }

        if (!aliveMembers.isEmpty()) {
            UUID oldLeader = this.leader;

            this.leader = aliveMembers.get(new Random().nextInt(aliveMembers.size()));

            String oldName = getPlayerName(oldLeader);
            String newName = getPlayerName(this.leader);
            String rawMsg = lang.getMsg(Bukkit.getConsoleSender(), "teams.leader-succession")
                    .replace("%old_leader%", oldName)
                    .replace("%new_leader%", newName);

            broadcast(ColorUtils.format(rawMsg));
        }
    }

    private String getPlayerName(UUID uuid) {
        Player p = Bukkit.getPlayer(uuid);
        if (p != null) return p.getName();
        @SuppressWarnings("deprecation")
        OfflinePlayer op = Bukkit.getOfflinePlayer(uuid);
        return op.getName() != null ? op.getName() : "Desconocido";
    }

    // ==========================================
    // GETTERS & SETTERS BÁSICOS
    // ==========================================

    public UUID getTeamId() { return teamId; }
    public String getName() { return name; }

    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }

    public String getTag() { return tag; }
    public void setTag(String tag) { this.tag = tag; }

    public UUID getLeader() { return leader; }
    public void setLeader(UUID leader) { this.leader = leader; }

    public Set<UUID> getMembers() { return members; }

    public int getMaxSize() { return maxSize; }
    public void setMaxSize(int maxSize) { this.maxSize = maxSize; }

    public long getTotalPlaytime() { return totalPlaytime; }
    public void addPlaytime(long seconds) { this.totalPlaytime += seconds; }

    public int getTotalTotems() { return totalTotems; }
    public void addTotem(int amount) { this.totalTotems += amount; }
}