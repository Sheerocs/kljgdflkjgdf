package pp.sheero.permapiola.dementialwheel;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityExhaustionEvent;
import org.bukkit.event.entity.EntityRegainHealthEvent;
import org.bukkit.event.player.PlayerItemDamageEvent;
import pp.sheero.permapiola.PermaPiola;
import pp.sheero.permapiola.utils.DeathStateManager;

public class DementialWheelListener implements Listener {

    private final PermaPiola plugin;

    public DementialWheelListener(PermaPiola plugin) {
        this.plugin = plugin;
    }

    // 2.1 EVENTO: TOXIC AIR - BLOQUEAR REGENERACIÓN DE VIDA
    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onPlayerRegen(EntityRegainHealthEvent event) {
        if (!(event.getEntity() instanceof Player)) return;
        Player player = (Player) event.getEntity();

        DementialWheelManager manager = plugin.getDementialWheelManager();
        if (!manager.hasEvent(DementialEventType.TOXIC_AIR)) return;
        if (DeathStateManager.isDead(player.getUniqueId())) return;

        if (event.getRegainReason() == EntityRegainHealthEvent.RegainReason.SATIATED ||
                event.getRegainReason() == EntityRegainHealthEvent.RegainReason.REGEN) {

            int yLayer = manager.getToxicAirLayer();
            if (player.getLocation().getY() >= yLayer) {
                event.setCancelled(true);
            }
        }
    }

    // 2.2 EVENTO: TOXIC AIR - EVITAR QUE LA SATURACIÓN BAJE A LO LOCO
    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onPlayerExhaustion(EntityExhaustionEvent event) {
        if (!(event.getEntity() instanceof Player)) return;
        Player player = (Player) event.getEntity();

        DementialWheelManager manager = plugin.getDementialWheelManager();
        if (!manager.hasEvent(DementialEventType.TOXIC_AIR)) return;
        if (DeathStateManager.isDead(player.getUniqueId())) return;

        if (event.getExhaustionReason() == EntityExhaustionEvent.ExhaustionReason.REGEN) {
            int yLayer = manager.getToxicAirLayer();
            if (player.getLocation().getY() >= yLayer) {
                event.setCancelled(true);
            }
        }
    }

    // 3. EVENTO: BROKEN GEAR
    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onItemDamage(PlayerItemDamageEvent event) {
        Player player = event.getPlayer();
        DementialWheelManager manager = plugin.getDementialWheelManager();

        if (!manager.hasEvent(DementialEventType.BROKEN_GEAR)) return;
        if (DeathStateManager.isDead(player.getUniqueId())) return;

        double chanceToIncrease = manager.getBrokenGearExtraDamageChance();

        int originalDamage = event.getDamage();
        int extraDamage = 0;

        for (int i = 0; i < originalDamage; i++) {
            if (Math.random() <= chanceToIncrease) {
                extraDamage++;
            }
        }

        if (extraDamage > 0) {
            event.setDamage(originalDamage + extraDamage);
        }
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onPlayerJoinDuringWheel(org.bukkit.event.player.PlayerJoinEvent event) {
        DementialWheelManager manager = plugin.getDementialWheelManager();
        manager.applyErosionIfMissing(event.getPlayer());
        manager.checkPendingRestoration(event.getPlayer());
    }
}