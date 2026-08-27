package pp.sheero.permapiola.chat;

import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import pp.sheero.permapiola.core.LanguageManager;
import pp.sheero.permapiola.utils.ColorUtils;

public class EmoteGUIListener implements Listener {

    private final LanguageManager lang;

    public EmoteGUIListener(LanguageManager lang) {
        this.lang = lang;
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (e.getView().title() != null && e.getWhoClicked() instanceof Player) {
            Player player = (Player) e.getWhoClicked();

            String currentTitle = PlainTextComponentSerializer.plainText().serialize(e.getView().title());

            String rawLangTitle = lang.getMsg(player, "commands.emotes.gui-title");
            String expectedTitle = ColorUtils.stripColors(ColorUtils.format(rawLangTitle));

            if (currentTitle.equals(expectedTitle)) {
                e.setCancelled(true);
            }
        }
    }
}