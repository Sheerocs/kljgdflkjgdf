package pp.sheero.permapiola.utilidad.listeners;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import pp.sheero.permapiola.PermaPiola;
import pp.sheero.permapiola.managers.LanguageManager;
import pp.sheero.permapiola.utils.ColorUtils;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class InventoryListener implements Listener {

    private final PermaPiola plugin;
    private final LanguageManager lang;

    private final Map<UUID, Long> interactionLocks = new HashMap<>();

    public InventoryListener(PermaPiola plugin, LanguageManager lang) {
        this.plugin = plugin;
        this.lang = lang;
        startLiveUpdater();
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
        }
    }
}