package pp.sheero.permapiola.utilidad.listeners;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import pp.sheero.permapiola.utils.DeathInventoryManager;
import pp.sheero.permapiola.utils.DeathStateManager;

public class PlayerDeathListener implements Listener {

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();

        DeathInventoryManager.saveInventory(player);

        DeathStateManager.setDead(player.getUniqueId(), true);
    }
}