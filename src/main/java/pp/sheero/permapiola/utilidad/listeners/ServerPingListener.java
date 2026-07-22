package pp.sheero.permapiola.utilidad.listeners;

import com.destroystokyo.paper.event.server.PaperServerListPingEvent;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import pp.sheero.permapiola.PermaPiola;

public class ServerPingListener implements Listener {

    private final PermaPiola plugin;

    public ServerPingListener(PermaPiola plugin) { this.plugin = plugin; }

    @EventHandler
    public void onServerPing(PaperServerListPingEvent event) {
        if (plugin.getConfig().getBoolean("server-list.hide-players", true)) {

            event.getPlayerSample().clear();
        }
    }
}