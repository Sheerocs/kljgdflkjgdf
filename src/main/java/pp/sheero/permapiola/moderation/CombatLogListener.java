package pp.sheero.permapiola.moderation;

import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import pp.sheero.permapiola.PermaPiola;
import pp.sheero.permapiola.core.LanguageManager;
import pp.sheero.permapiola.utils.ColorUtils;
import pp.sheero.permapiola.hurricane.DeathStateManager;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class CombatLogListener implements Listener {

    private final PermaPiola plugin;
    private final LanguageManager lang;

    private static class CombatData {
        final long timestamp;
        final String cause;

        CombatData(long timestamp, String cause) {
            this.timestamp = timestamp;
            this.cause = cause;
        }
    }

    private final Map<UUID, CombatData> combatCache = new ConcurrentHashMap<>();

    private long durationMillis;
    private double detectionRadius;

    public CombatLogListener(PermaPiola plugin, LanguageManager lang) {
        this.plugin = plugin;
        this.lang = lang;
        loadConfigCache();
    }

    public void loadConfigCache() {
        this.durationMillis = plugin.getConfig().getLong("combatlog.duration-seconds", 15) * 1000;
        this.detectionRadius = plugin.getConfig().getDouble("combatlog.detection-radius", 8.0);
    }

    @EventHandler
    public void onEntityDamage(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player)) return;
        Player player = (Player) event.getEntity();

        String causeName = "Desconocido";
        boolean isPvE = false;

        if (event.getDamager() instanceof Projectile) {
            Projectile proj = (Projectile) event.getDamager();
            if (proj.getShooter() != null && !(proj.getShooter() instanceof Player)) {
                isPvE = true;
                causeName = proj.getShooter().getClass().getSimpleName();
            }
        } else if (!(event.getDamager() instanceof Player)) {
            isPvE = true;
            causeName = event.getDamager().getType().name();
        }

        if (isPvE) {
            combatCache.put(player.getUniqueId(), new CombatData(System.currentTimeMillis(), causeName));
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();

        if (player.isDead() || DeathStateManager.isDead(uuid)) {
            combatCache.remove(uuid);
            return;
        }

        CombatData data = combatCache.remove(uuid);

        if (data != null && (System.currentTimeMillis() - data.timestamp) <= this.durationMillis) {

            int nearbymobs = 0;
            for (Entity e : player.getNearbyEntities(this.detectionRadius, this.detectionRadius, this.detectionRadius)) {
                if (e instanceof Monster) {
                    nearbymobs++;
                }
            }

            if (nearbymobs > 0) {
                String health = String.valueOf(Math.round(player.getHealth() * 10.0) / 10.0);
                String cause = data.cause;
                String ping = String.valueOf(player.getPing());

                String reason = traducirRazon(event.getReason());

                sendStaffAlert(player, health, nearbymobs, cause, reason, ping);
            }
        }
    }

    private void sendStaffAlert(Player p, String hp, int mobs, String cause, String reason, String ping) {
        String[] keys = {"header", "mobs", "last-damage", "reason", "ping"};

        for (Player online : Bukkit.getOnlinePlayers()) {
            if (online.hasPermission("permapiola.admin.staffchat")) {
                for (String key : keys) {
                    String msg = lang.getMsg(online, "combatlog.staff." + key)
                            .replace("%player%", p.getName())
                            .replace("%health%", hp)
                            .replace("%n%", String.valueOf(mobs))
                            .replace("%cause%", cause)
                            .replace("%reason%", reason)
                            .replace("%ping%", ping);
                    online.sendMessage(ColorUtils.format(msg));
                }
            }
        }
        Bukkit.getLogger().warning("[CombatLog] " + p.getName() + " se desconecto con " + mobs + " mobs cerca. Razon: " + reason);
    }

    private String traducirRazon(PlayerQuitEvent.QuitReason reason) {
        return switch (reason) {
            case DISCONNECTED -> "Desconexión";
            case TIMED_OUT -> "Tiempo Agotado";
            case KICKED -> "Expulsado por el servidor";
            case ERRONEOUS_STATE -> "Error de conexión (Estado erróneo)";
            default -> reason.name();
        };
    }
}