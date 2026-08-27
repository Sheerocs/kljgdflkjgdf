package pp.sheero.permapiola.inventory;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import pp.sheero.permapiola.hurricane.DeathStateManager;

public class PlayerDeathListener implements Listener {

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();

        DeathInventoryManager.saveInventory(player);

        DeathStateManager.setDead(player.getUniqueId(), true);
    }
}