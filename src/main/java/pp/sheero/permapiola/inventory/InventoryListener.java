package pp.sheero.permapiola.inventory;

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
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import pp.sheero.permapiola.PermaPiola;
import pp.sheero.permapiola.core.LanguageManager;
import pp.sheero.permapiola.utils.ColorUtils;

import java.util.*;

public class InventoryListener implements Listener {

    private final PermaPiola plugin;
    private final LanguageManager lang;

    private final Map<UUID, Long> interactionLocks = new HashMap<>();
    private final Map<UUID, ItemStack[]> snapshots = new HashMap<>(); // Memoria para la auditoría

    public InventoryListener(PermaPiola plugin, LanguageManager lang) {
        this.plugin = plugin;
        this.lang = lang;
        startLiveUpdater();
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

    private String extractPlayerName(String cleanTitle, String formatMsg) {
        if (formatMsg == null || formatMsg.isEmpty()) return null;

        String cleanFormat = org.bukkit.ChatColor.stripColor(ColorUtils.format(formatMsg));

        int placeholderIndex = cleanFormat.indexOf("%player%");
        if (placeholderIndex == -1) return null;

        String prefix = cleanFormat.substring(0, placeholderIndex);
        String suffix = cleanFormat.substring(placeholderIndex + "%player%".length());

        if (cleanTitle.startsWith(prefix) && cleanTitle.endsWith(suffix)) {
            int endIndex = cleanTitle.length() - suffix.length();
            if (prefix.length() <= endIndex) {
                String extractedName = cleanTitle.substring(prefix.length(), endIndex);
                if (!extractedName.isEmpty() && !extractedName.contains(" ")) {
                    return extractedName;
                }
            }
        }
        return null;
    }

    private void startLiveUpdater() {
        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            for (Player admin : Bukkit.getOnlinePlayers()) {
                if (interactionLocks.containsKey(admin.getUniqueId())) {
                    if (System.currentTimeMillis() < interactionLocks.get(admin.getUniqueId())) {
                        continue;
                    } else {
                        interactionLocks.remove(admin.getUniqueId());
                    }
                }

                String title = admin.getOpenInventory().getTitle();
                String cleanTitle = org.bukkit.ChatColor.stripColor(title);

                String invFormat = lang.getMsg(admin, "commands.inventory.gui-title");
                String echestFormat = lang.getMsg(admin, "commands.echest.gui-title");
                String echestSelfFormat = lang.getMsg(admin, "commands.echest.gui-title-self");

                String targetNameInv = extractPlayerName(cleanTitle, invFormat);
                String targetNameEchest = extractPlayerName(cleanTitle, echestFormat);
                boolean isSelfEchest = cleanTitle.equals(org.bukkit.ChatColor.stripColor(ColorUtils.format(echestSelfFormat)));

                if (targetNameInv != null) {
                    Player target = Bukkit.getPlayerExact(targetNameInv);

                    if (target != null && target.isOnline()) {
                        Inventory gui = admin.getOpenInventory().getTopInventory();
                        for (int i = 0; i < 36; i++) {
                            gui.setItem(i, target.getInventory().getItem(i));
                        }
                        gui.setItem(36, target.getInventory().getBoots());
                        gui.setItem(37, target.getInventory().getLeggings());
                        gui.setItem(38, target.getInventory().getChestplate());
                        gui.setItem(39, target.getInventory().getHelmet());
                        gui.setItem(40, target.getInventory().getItemInOffHand());
                    }
                }
                else if (targetNameEchest != null) {
                    Player target = Bukkit.getPlayerExact(targetNameEchest);

                    if (target != null && target.isOnline()) {
                        Inventory gui = admin.getOpenInventory().getTopInventory();
                        gui.setContents(target.getEnderChest().getContents());
                    }
                }
                else if (isSelfEchest) {
                    Inventory gui = admin.getOpenInventory().getTopInventory();
                    gui.setContents(admin.getEnderChest().getContents());
                }
            }
        }, 0L, 2L);
    }

    private void syncInventory(String targetName, Inventory inv) {
        Player target = Bukkit.getPlayerExact(targetName);

        if (target != null && target.isOnline()) {
            for (int i = 0; i < 36; i++) {
                target.getInventory().setItem(i, inv.getItem(i));
            }
            target.getInventory().setBoots(inv.getItem(36));
            target.getInventory().setLeggings(inv.getItem(37));
            target.getInventory().setChestplate(inv.getItem(38));
            target.getInventory().setHelmet(inv.getItem(39));
            target.getInventory().setItemInOffHand(inv.getItem(40));
            target.updateInventory();
        }
    }

    private void syncEnderChest(String targetName, Inventory inv) {
        Player target = Bukkit.getPlayerExact(targetName);

        if (target != null && target.isOnline()) {
            target.getEnderChest().setContents(inv.getContents());
        }
    }

    @EventHandler
    public void onInventoryOpen(InventoryOpenEvent event) {
        Player admin = (Player) event.getPlayer();
        String cleanTitle = org.bukkit.ChatColor.stripColor(event.getView().getTitle());

        String invFormat = lang.getMsg(admin, "commands.inventory.gui-title");
        String targetNameInv = extractPlayerName(cleanTitle, invFormat);

        if (targetNameInv != null) {
            ItemStack[] copy = new ItemStack[41];
            for (int i = 0; i <= 40; i++) {
                ItemStack item = event.getInventory().getItem(i);
                copy[i] = item != null ? item.clone() : null;
            }
            snapshots.put(admin.getUniqueId(), copy);
        }
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        Player admin = (Player) event.getWhoClicked();
        String title = event.getView().getTitle();
        String cleanTitle = org.bukkit.ChatColor.stripColor(title);

        String invFormat = lang.getMsg(admin, "commands.inventory.gui-title");
        String echestFormat = lang.getMsg(admin, "commands.echest.gui-title");
        String echestSelfFormat = lang.getMsg(admin, "commands.echest.gui-title-self");

        String targetNameInv = extractPlayerName(cleanTitle, invFormat);
        String targetNameEchest = extractPlayerName(cleanTitle, echestFormat);
        boolean isSelfEchest = cleanTitle.equals(org.bukkit.ChatColor.stripColor(ColorUtils.format(echestSelfFormat)));

        if (targetNameInv != null || targetNameEchest != null || isSelfEchest) {
            interactionLocks.put(admin.getUniqueId(), System.currentTimeMillis() + 100);

            if (targetNameInv != null && event.getRawSlot() >= 41 && event.getRawSlot() <= 44) {
                event.setCancelled(true);
                return;
            }

            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (targetNameInv != null) syncInventory(targetNameInv, event.getInventory());
                else if (targetNameEchest != null) syncEnderChest(targetNameEchest, event.getInventory());
                else if (isSelfEchest) syncEnderChest(admin.getName(), event.getInventory());
            }, 1L);
        }
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        Player admin = (Player) event.getWhoClicked();
        String title = event.getView().getTitle();
        String cleanTitle = org.bukkit.ChatColor.stripColor(title);

        String invFormat = lang.getMsg(admin, "commands.inventory.gui-title");
        String echestFormat = lang.getMsg(admin, "commands.echest.gui-title");
        String echestSelfFormat = lang.getMsg(admin, "commands.echest.gui-title-self");

        String targetNameInv = extractPlayerName(cleanTitle, invFormat);
        String targetNameEchest = extractPlayerName(cleanTitle, echestFormat);
        boolean isSelfEchest = cleanTitle.equals(org.bukkit.ChatColor.stripColor(ColorUtils.format(echestSelfFormat)));

        if (targetNameInv != null || targetNameEchest != null || isSelfEchest) {
            interactionLocks.put(admin.getUniqueId(), System.currentTimeMillis() + 100);

            if (targetNameInv != null) {
                for (int slot : event.getRawSlots()) {
                    if (slot >= 41 && slot <= 44) {
                        event.setCancelled(true);
                        return;
                    }
                }
            }

            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (targetNameInv != null) syncInventory(targetNameInv, event.getInventory());
                else if (targetNameEchest != null) syncEnderChest(targetNameEchest, event.getInventory());
                else if (isSelfEchest) syncEnderChest(admin.getName(), event.getInventory());
            }, 1L);
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        Player admin = (Player) event.getPlayer();
        String title = event.getView().getTitle();
        String cleanTitle = org.bukkit.ChatColor.stripColor(title);

        String invFormat = lang.getMsg(admin, "commands.inventory.gui-title");
        String echestFormat = lang.getMsg(admin, "commands.echest.gui-title");
        String echestSelfFormat = lang.getMsg(admin, "commands.echest.gui-title-self");

        String targetNameInv = extractPlayerName(cleanTitle, invFormat);
        String targetNameEchest = extractPlayerName(cleanTitle, echestFormat);
        boolean isSelfEchest = cleanTitle.equals(org.bukkit.ChatColor.stripColor(ColorUtils.format(echestSelfFormat)));

        if (targetNameInv != null || targetNameEchest != null || isSelfEchest) {
            if (targetNameInv != null) syncInventory(targetNameInv, event.getInventory());
            else if (targetNameEchest != null) syncEnderChest(targetNameEchest, event.getInventory());
            else if (isSelfEchest) syncEnderChest(admin.getName(), event.getInventory());

            admin.updateInventory();
            interactionLocks.remove(admin.getUniqueId());

            if (targetNameEchest != null || isSelfEchest) {
                admin.playSound(admin.getLocation(), org.bukkit.Sound.BLOCK_ENDER_CHEST_CLOSE, 1.0f, 1.0f);
            }

            if (targetNameInv != null && snapshots.containsKey(admin.getUniqueId())) {
                ItemStack[] oldContents = snapshots.remove(admin.getUniqueId());
                ItemStack[] newContents = new ItemStack[41];
                Inventory gui = event.getInventory();

                List<Component> finalReport = new ArrayList<>();
                List<SlotDiff> added = new ArrayList<>();
                List<SlotDiff> removed = new ArrayList<>();
                List<SlotDiff> replaced = new ArrayList<>();
                List<SlotDiff> amountChanged = new ArrayList<>();

                for (int i = 0; i <= 40; i++) {
                    newContents[i] = gui.getItem(i);
                    ItemStack oldI = oldContents[i];
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

                if (!finalReport.isEmpty()) {
                    Component header = LegacyComponentSerializer.legacySection().deserialize(ColorUtils.format(lang.getMsg(admin, "commands.inventory.audit.header")
                            .replace("%admin%", admin.getName())
                            .replace("%target%", targetNameInv)));

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
}