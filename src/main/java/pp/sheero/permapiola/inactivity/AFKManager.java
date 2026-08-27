package pp.sheero.permapiola.inactivity;

import io.papermc.paper.event.player.AsyncChatEvent;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import pp.sheero.permapiola.PermaPiola;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class AFKManager implements Listener {

    private final PermaPiola plugin;

    private final Map<UUID, Long> lastActivity = new ConcurrentHashMap<>();

    private long afkTimeoutMillis;

    public AFKManager(PermaPiola plugin) {
        this.plugin = plugin;
        loadConfigCache();
    }

    public void loadConfigCache() {
        long minutes = plugin.getConfig().getLong("playtime.afk-timeout-minutes", 5);
        this.afkTimeoutMillis = minutes * 60 * 1000;
    }

    public void updateActivity(Player player) {
        lastActivity.put(player.getUniqueId(), System.currentTimeMillis());
    }

    public boolean isAFK(Player player) {
        long lastTime = lastActivity.getOrDefault(player.getUniqueId(), System.currentTimeMillis());
        return (System.currentTimeMillis() - lastTime) >= this.afkTimeoutMillis;
    }

    @EventHandler
    public void onMove(PlayerMoveEvent e) {
        if (e.getFrom().getYaw() != e.getTo().getYaw() || e.getFrom().getPitch() != e.getTo().getPitch()) {
            updateActivity(e.getPlayer());
        }
    }

    @EventHandler
    public void onChat(AsyncChatEvent e) {
        updateActivity(e.getPlayer());
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent e) {
        Action action = e.getAction();

        if (action == Action.PHYSICAL) return;

        if (action == Action.LEFT_CLICK_AIR || action == Action.RIGHT_CLICK_AIR) {
            return;
        }

        if (e.getClickedBlock() != null) {
            Material type = e.getClickedBlock().getType();
            String name = type.name();

            if (type == Material.NOTE_BLOCK ||
                    type == Material.LEVER ||
                    type == Material.BELL ||
                    name.contains("TRAPDOOR") ||
                    name.contains("DOOR") ||
                    name.contains("BUTTON") ||
                    name.contains("FENCE_GATE")) {
                return;
            }
        }

        if (e.getItem() != null) {
            if (e.getItem().getType() == Material.FISHING_ROD) {
                return;
            }
        }

        updateActivity(e.getPlayer());
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        updateActivity(e.getPlayer());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        lastActivity.remove(e.getPlayer().getUniqueId());
    }
}