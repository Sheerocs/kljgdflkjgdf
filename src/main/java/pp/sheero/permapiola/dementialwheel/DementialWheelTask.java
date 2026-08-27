package pp.sheero.permapiola.dementialwheel;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import pp.sheero.permapiola.PermaPiola;
import pp.sheero.permapiola.hurricane.DeathStateManager;

public class DementialWheelTask implements Runnable {

    private final PermaPiola plugin;

    public DementialWheelTask(PermaPiola plugin) {
        this.plugin = plugin;
    }

    @Override
    public void run() {
        DementialWheelManager manager = plugin.getDementialWheelManager();
        if (!manager.isActive()) return;

        for (Player p : Bukkit.getOnlinePlayers()) {
            if (p.getGameMode() == GameMode.SPECTATOR || p.getGameMode() == GameMode.CREATIVE || DeathStateManager.isDead(p.getUniqueId())) continue;

            // 2. EVENTO: PUTRIFIED WATER
            if (manager.hasEvent(DementialEventType.PUTRIFIED_WATER)) {
                if (p.isInWater()) {
                    int duration = manager.getPutrifiedWaterDuration();
                    int amplifier = manager.getPutrifiedWaterAmplifier();
                    p.addPotionEffect(new PotionEffect(PotionEffectType.WITHER, duration, amplifier, false, false, true));
                }
            }

            // 3. EVENTO: ACID RAIN
            if (manager.hasEvent(DementialEventType.ACID_RAIN)) {
                boolean inWater = p.isInWater();
                boolean exposedToRain = false;

                if (p.getWorld().hasStorm()) {
                    int highestBlockY = p.getWorld().getHighestBlockYAt(p.getLocation());
                    if (p.getLocation().getY() >= highestBlockY) {
                        exposedToRain = true;
                    }
                }

                if (inWater || exposedToRain) {
                    double damage = manager.getAcidRainDamage();

                    p.setMetadata("DementialDamage", new FixedMetadataValue(plugin, "Acid Rain"));
                    try {
                        p.damage(damage);
                    } finally {
                        p.removeMetadata("DementialDamage", plugin);
                    }
                }
            }
        }
    }
}