package pp.sheero.permapiola.utilidad.listeners;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextReplacementConfig;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.ItemStack;
import pp.sheero.permapiola.managers.LanguageManager;
import pp.sheero.permapiola.utils.ColorUtils;
import pp.sheero.permapiola.utils.DeathInventoryManager;
import pp.sheero.permapiola.utils.DeathStateManager;

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

    private String getItemName(ItemStack item) {
        if (item == null || item.getType() == Material.AIR) return "Air";
        if (item.hasItemMeta() && item.getItemMeta().hasDisplayName()) {
            return item.getItemMeta().getDisplayName();
        }
        String name = item.getType().name().replace('_', ' ').toLowerCase();
        return name.substring(0, 1).toUpperCase() + name.substring(1);
    }

    private Component getHoverableItem(ItemStack item) {
        if (item == null || item.getType() == Material.AIR) return Component.text("Air");
        String name = getItemName(item);
        return LegacyComponentSerializer.legacySection().deserialize(ColorUtils.format(name))
                .hoverEvent(item.asHoverEvent());
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        Player admin = (Player) event.getWhoClicked();
        if (viewers.containsKey(admin.getUniqueId())) {
            int slot = event.getRawSlot();
            if (slot >= 41 && slot <= 44) {
                event.setCancelled(true);
            }
            if (event.isShiftClick() && slot > 44) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        Player admin = (Player) event.getWhoClicked();
        if (viewers.containsKey(admin.getUniqueId())) {
            for (int slot : event.getRawSlots()) {
                if (slot >= 41 && slot <= 44) {
                    event.setCancelled(true);
                    return;
                }
            }
        }
    }

    private static class SlotDiff {
        int slot;
        ItemStack oldItem;
        ItemStack newItem;
        SlotDiff(int slot, ItemStack oldItem, ItemStack newItem) {
            this.slot = slot;
            this.oldItem = oldItem;
            this.newItem = newItem;
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        Player admin = (Player) event.getPlayer();
        UUID adminUuid = admin.getUniqueId();

        if (viewers.containsKey(adminUuid)) {
            UUID targetUuid = viewers.remove(adminUuid);
            lockedInventories.remove(targetUuid);

            org.bukkit.inventory.Inventory gui = event.getInventory();
            ItemStack[] oldContents = DeathInventoryManager.getSavedContents(targetUuid);
            ItemStack[] newContents = new ItemStack[41];

            List<Component> finalReport = new ArrayList<>();

            List<SlotDiff> added = new ArrayList<>();
            List<SlotDiff> removed = new ArrayList<>();
            List<SlotDiff> replaced = new ArrayList<>();
            List<SlotDiff> amountChanged = new ArrayList<>();

            for (int i = 0; i <= 40; i++) {
                newContents[i] = gui.getItem(i);
                ItemStack oldI = (oldContents != null && i < oldContents.length) ? oldContents[i] : null;
                ItemStack newI = newContents[i];

                boolean oldNull = (oldI == null || oldI.getType() == Material.AIR);
                boolean newNull = (newI == null || newI.getType() == Material.AIR);

                if (oldNull && newNull) continue;

                if (oldNull && !newNull) {
                    added.add(new SlotDiff(i, null, newI));
                } else if (!oldNull && newNull) {
                    removed.add(new SlotDiff(i, oldI, null));
                } else if (oldI.isSimilar(newI) && oldI.getAmount() != newI.getAmount()) {
                    amountChanged.add(new SlotDiff(i, oldI, newI));
                } else if (!oldI.isSimilar(newI)) {
                    replaced.add(new SlotDiff(i, oldI, newI));
                }
            }

            Iterator<SlotDiff> repIt = replaced.iterator();
            while (repIt.hasNext()) {
                SlotDiff rep1 = repIt.next();
                SlotDiff match = null;
                for (SlotDiff rep2 : replaced) {
                    if (rep1 == rep2) continue;
                    if (rep1.oldItem.isSimilar(rep2.newItem) && rep1.newItem.isSimilar(rep2.oldItem) &&
                            rep1.oldItem.getAmount() == rep2.newItem.getAmount() && rep1.newItem.getAmount() == rep2.oldItem.getAmount()) {
                        match = rep2;
                        break;
                    }
                }
                if (match != null) {
                    String raw = lang.getMsg(admin, "commands.inventory.audit.swapped")
                            .replace("%amount1%", String.valueOf(rep1.oldItem.getAmount()))
                            .replace("%slot1%", String.valueOf(rep1.slot))
                            .replace("%amount2%", String.valueOf(match.oldItem.getAmount()))
                            .replace("%slot2%", String.valueOf(match.slot));
                    Component comp = LegacyComponentSerializer.legacySection().deserialize(ColorUtils.format(raw))
                            .replaceText(TextReplacementConfig.builder().matchLiteral("%item1%").replacement(getHoverableItem(rep1.oldItem)).build())
                            .replaceText(TextReplacementConfig.builder().matchLiteral("%item2%").replacement(getHoverableItem(match.oldItem)).build());
                    finalReport.add(comp);
                    repIt.remove();
                    replaced.remove(match);
                }
            }

            Iterator<SlotDiff> remIt = removed.iterator();
            while (remIt.hasNext()) {
                SlotDiff rem = remIt.next();
                SlotDiff match = null;
                for (SlotDiff add : added) {
                    if (rem.oldItem.isSimilar(add.newItem) && rem.oldItem.getAmount() == add.newItem.getAmount()) {
                        match = add;
                        break;
                    }
                }
                if (match != null) {
                    String raw = lang.getMsg(admin, "commands.inventory.audit.moved")
                            .replace("%amount%", String.valueOf(rem.oldItem.getAmount()))
                            .replace("%old_slot%", String.valueOf(rem.slot))
                            .replace("%new_slot%", String.valueOf(match.slot));
                    Component comp = LegacyComponentSerializer.legacySection().deserialize(ColorUtils.format(raw))
                            .replaceText(TextReplacementConfig.builder().matchLiteral("%item%").replacement(getHoverableItem(rem.oldItem)).build());
                    finalReport.add(comp);
                    remIt.remove();
                    added.remove(match);
                }
            }

            for (SlotDiff add : added) {
                String raw = lang.getMsg(admin, "commands.inventory.audit.added").replace("%amount%", String.valueOf(add.newItem.getAmount())).replace("%slot%", String.valueOf(add.slot));
                finalReport.add(LegacyComponentSerializer.legacySection().deserialize(ColorUtils.format(raw)).replaceText(TextReplacementConfig.builder().matchLiteral("%item%").replacement(getHoverableItem(add.newItem)).build()));
            }
            for (SlotDiff rem : removed) {
                String raw = lang.getMsg(admin, "commands.inventory.audit.removed").replace("%amount%", String.valueOf(rem.oldItem.getAmount())).replace("%slot%", String.valueOf(rem.slot));
                finalReport.add(LegacyComponentSerializer.legacySection().deserialize(ColorUtils.format(raw)).replaceText(TextReplacementConfig.builder().matchLiteral("%item%").replacement(getHoverableItem(rem.oldItem)).build()));
            }
            for (SlotDiff rep : replaced) {
                String raw = lang.getMsg(admin, "commands.inventory.audit.replaced").replace("%old_amount%", String.valueOf(rep.oldItem.getAmount())).replace("%amount%", String.valueOf(rep.newItem.getAmount())).replace("%slot%", String.valueOf(rep.slot));
                finalReport.add(LegacyComponentSerializer.legacySection().deserialize(ColorUtils.format(raw)).replaceText(TextReplacementConfig.builder().matchLiteral("%old_item%").replacement(getHoverableItem(rep.oldItem)).build()).replaceText(TextReplacementConfig.builder().matchLiteral("%item%").replacement(getHoverableItem(rep.newItem)).build()));
            }
            for (SlotDiff amt : amountChanged) {
                String raw = lang.getMsg(admin, "commands.inventory.audit.amount_changed").replace("%old_amount%", String.valueOf(amt.oldItem.getAmount())).replace("%amount%", String.valueOf(amt.newItem.getAmount())).replace("%slot%", String.valueOf(amt.slot));
                finalReport.add(LegacyComponentSerializer.legacySection().deserialize(ColorUtils.format(raw)).replaceText(TextReplacementConfig.builder().matchLiteral("%item%").replacement(getHoverableItem(amt.newItem)).build()));
            }

            DeathInventoryManager.updateSavedInventory(targetUuid, newContents);
            admin.sendMessage(ColorUtils.format(lang.getMsg(admin, "commands.inventory.death-saved")));

            if (!finalReport.isEmpty()) {
                String targetName = DeathStateManager.getDeadPlayerNames().get(targetUuid);
                if (targetName == null) targetName = "Desconocido";

                Component header = LegacyComponentSerializer.legacySection().deserialize(ColorUtils.format(lang.getMsg(admin, "commands.inventory.audit.header").replace("%admin%", admin.getName()).replace("%target%", targetName)));

                admin.sendMessage(header);
                Bukkit.getConsoleSender().sendMessage(header);

                for (Component change : finalReport) {
                    admin.sendMessage(change);
                    Bukkit.getConsoleSender().sendMessage(change);
                }
            }
        }
    }
}