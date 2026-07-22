package pp.sheero.permapiola.utilidad.listeners;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryCloseEvent;
import pp.sheero.permapiola.managers.LanguageManager;
import pp.sheero.permapiola.utils.ColorUtils;
import pp.sheero.permapiola.utils.DeathInventoryManager;

import java.util.*;

public class DeathInventoryEditListener implements Listener {

    private final LanguageManager lang;

    private static final Map<UUID, UUID> viewers = new HashMap<>();
    private static final Set<UUID> lockedInventories = new HashSet<>();

    public DeathInventoryEditListener(LanguageManager lang) {
        this.lang = lang;
    }

    public static boolean isLocked(UUID targetUuid) {
        return lockedInventories.contains(targetUuid);
    }

    public static void setLocked(UUID adminUuid, UUID targetUuid) {
        lockedInventories.add(targetUuid);
        viewers.put(adminUuid, targetUuid);
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        Player admin = (Player) event.getPlayer();
        UUID adminUuid = admin.getUniqueId();

        if (viewers.containsKey(adminUuid)) {
            UUID targetUuid = viewers.remove(adminUuid);
            lockedInventories.remove(targetUuid);

            org.bukkit.inventory.ItemStack[] updatedContents = new org.bukkit.inventory.ItemStack[41];
            org.bukkit.inventory.Inventory gui = event.getInventory();

            for (int i = 0; i < 36; i++) {
                updatedContents[i] = gui.getItem(i);
            }
            updatedContents[36] = gui.getItem(36);
            updatedContents[37] = gui.getItem(37);
            updatedContents[38] = gui.getItem(38);
            updatedContents[39] = gui.getItem(39);
            updatedContents[40] = gui.getItem(40);

            DeathInventoryManager.updateSavedInventory(targetUuid, updatedContents);

            admin.sendMessage(ColorUtils.format(lang.getMsg(admin, "commands.inventory.death-saved")));
        }
    }
}