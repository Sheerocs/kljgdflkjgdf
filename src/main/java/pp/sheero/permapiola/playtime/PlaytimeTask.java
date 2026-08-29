package pp.sheero.permapiola.playtime;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import pp.sheero.permapiola.PermaPiola;
import pp.sheero.permapiola.inactivity.AFKManager;
import pp.sheero.permapiola.hurricane.DeathStateManager;
import pp.sheero.permapiola.teams.PiolaTeam;
import pp.sheero.permapiola.teams.TeamManager;

import java.util.HashSet;
import java.util.Set;

public class PlaytimeTask extends BukkitRunnable {

    private final PermaPiola plugin;
    private final AFKManager afkManager;
    private final PlaytimeManager playtimeManager;
    private long lastRunTime;

    public PlaytimeTask(PermaPiola plugin, AFKManager afkManager, PlaytimeManager playtimeManager) {
        this.plugin = plugin;
        this.afkManager = afkManager;
        this.playtimeManager = playtimeManager;
        this.lastRunTime = System.currentTimeMillis();
    }

    @Override
    public void run() {
        long now = System.currentTimeMillis();
        long deltaMillis = now - lastRunTime;
        long deltaSeconds = deltaMillis / 1000;

        if (deltaSeconds > 0) {

            Set<PiolaTeam> activeTeams = new HashSet<>();

            for (Player player : Bukkit.getOnlinePlayers()) {
                if (DeathStateManager.isDead(player.getUniqueId())) continue;
                if (afkManager.isAFK(player)) continue;
                GameMode gm = player.getGameMode();
                if (gm != GameMode.SURVIVAL && gm != GameMode.ADVENTURE) continue;

                playtimeManager.addPlaytime(player.getUniqueId(), deltaSeconds);

                PiolaTeam team = TeamManager.getTeam(player);
                if (team != null) {
                    activeTeams.add(team);
                }
            }

            for (PiolaTeam team : activeTeams) {
                team.addPlaytime(deltaSeconds);
            }

            lastRunTime = now - (deltaMillis % 1000);
        }
    }
}