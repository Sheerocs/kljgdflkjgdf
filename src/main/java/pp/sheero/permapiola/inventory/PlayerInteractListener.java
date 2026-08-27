package pp.sheero.permapiola.inventory;

import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEntityEvent;

public class PlayerInteractListener implements Listener {

    @EventHandler
    public void onPlayerInteractEntity(PlayerInteractEntityEvent event) {
        if (!(event.getRightClicked() instanceof Player)) {
            return;
        }

        Player clicker = event.getPlayer();
        Player clickedTarget = (Player) event.getRightClicked();

        if (clicker.hasPermission("permapiola.admin.inventory") && clicker.getGameMode() == GameMode.SPECTATOR) {

            clicker.performCommand("inv see " + clickedTarget.getName());
        }
    }
}