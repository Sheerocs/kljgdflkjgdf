package pp.sheero.permapiola.utils;

import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.event.EventBus;
import net.luckperms.api.event.user.UserDataRecalculateEvent;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import pp.sheero.permapiola.PermaPiola;
import pp.sheero.permapiola.teams.TabManager;

public class LuckPermsUpdateListener {

    private final PermaPiola plugin;

    public LuckPermsUpdateListener(PermaPiola plugin) {
        this.plugin = plugin;
    }

    public void register() {
        try {
            LuckPerms api = LuckPermsProvider.get();
            EventBus eventBus = api.getEventBus();

            eventBus.subscribe(plugin, UserDataRecalculateEvent.class, event -> {
                Bukkit.getScheduler().runTask(plugin, () -> {
                    Player player = Bukkit.getPlayer(event.getUser().getUniqueId());
                    if (player != null && player.isOnline()) {
                        TabManager.updatePlayerTab(player, plugin);
                    }
                });
            });
        } catch (Exception ignored) {
            plugin.getLogger().warning("No se pudo registrar el Listener de actualizaciones de LuckPerms.");
        }
    }
}