package pp.sheero.permapiola.teams;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import pp.sheero.permapiola.utils.ColorUtils;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public class PiolaReTeam {

    private final UUID reteamId;
    private String name;
    private String displayName;
    private String tag;
    private UUID leader;
    private final Set<UUID> members;
    private final List<String> originalTeams;

    public PiolaReTeam(UUID leader, String name, String displayName, String tag, List<String> originalTeams) {
        this.reteamId = UUID.randomUUID();
        this.leader = leader;
        this.name = name.toLowerCase();
        this.displayName = displayName;
        this.tag = tag;
        this.originalTeams = originalTeams;

        this.members = new HashSet<>();
        this.members.add(leader);
    }

    public PiolaReTeam(UUID reteamId, UUID leader, String name, String displayName, String tag, Set<UUID> members, List<String> originalTeams) {
        this.reteamId = reteamId;
        this.leader = leader;
        this.name = name;
        this.displayName = displayName;
        this.tag = tag;
        this.members = members;
        this.originalTeams = originalTeams;
    }

    // ==========================================
    // LÓGICA DE MIEMBROS Y MENSAJERÍA
    // ==========================================

    public void addMember(UUID uuid) { this.members.add(uuid); }
    public void removeMember(UUID uuid) { this.members.remove(uuid); }
    public boolean hasMember(UUID uuid) { return this.members.contains(uuid); }
    public boolean isLeader(UUID uuid) { return this.leader.equals(uuid); }

    public void broadcast(String message) {
        String formatted = ColorUtils.format(message);
        for (UUID memberUuid : members) {
            Player p = Bukkit.getPlayer(memberUuid);
            if (p != null && p.isOnline()) {
                p.sendMessage(formatted);
            }
        }
    }

    // ==========================================
    // GETTERS & SETTERS BÁSICOS
    // ==========================================

    public UUID getReteamId() { return reteamId; }
    public String getName() { return name; }

    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }

    public String getTag() { return tag; }
    public void setTag(String tag) { this.tag = tag; }

    public UUID getLeader() { return leader; }
    public void setLeader(UUID leader) { this.leader = leader; }

    public Set<UUID> getMembers() { return members; }
    public List<String> getOriginalTeams() { return originalTeams; }
}