package pp.sheero.permapiola.utilidad.listeners;

import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;

public class EmoteGUIListener implements Listener {

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (e.getView().title() != null) {
            String title = PlainTextComponentSerializer.plainText().serialize(e.getView().title());
            if (title.equals("Lista de Emotes")) {
                e.setCancelled(true);
            }
        }
    }
}