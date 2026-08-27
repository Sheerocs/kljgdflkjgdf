package pp.sheero.permapiola.playtime;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import pp.sheero.permapiola.PermaPiola;
import pp.sheero.permapiola.inactivity.AFKManager;
import pp.sheero.permapiola.hurricane.DeathStateManager;

public class PlaytimeTask extends BukkitRunnable {

    private final PermaPiola plugin;
    private final AFKManager afkManager;
    private final PlaytimeManager playtimeManager;

    public PlaytimeTask(PermaPiola plugin, AFKManager afkManager, PlaytimeManager playtimeManager) {
        this.plugin = plugin;
        this.afkManager = afkManager;
        this.playtimeManager = playtimeManager;
    }

    @Override
    public void run() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (DeathStateManager.isDead(player.getUniqueId())) continue;
            if (afkManager.isAFK(player)) continue;
            GameMode gm = player.getGameMode();
            if (gm != GameMode.SURVIVAL && gm != GameMode.ADVENTURE) continue;

            playtimeManager.addPlaytime(player.getUniqueId(), 1);
        }
    }
}