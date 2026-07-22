package pp.sheero.permapiola.utilidad.commands;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import pp.sheero.permapiola.PermaPiola;
import pp.sheero.permapiola.managers.LanguageManager;
import pp.sheero.permapiola.utilidad.listeners.DeathInventoryEditListener;
import pp.sheero.permapiola.utils.ColorUtils;
import pp.sheero.permapiola.utils.DeathInventoryManager;
import pp.sheero.permapiola.utils.DeathStateManager;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class InventoryCommand implements CommandExecutor, TabCompleter {

    private final PermaPiola plugin;
    private final LanguageManager lang;

    public InventoryCommand(PermaPiola plugin, LanguageManager lang) {
        this.plugin = plugin;
        this.lang = lang;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(ColorUtils.format(lang.getMsg(sender, "commands.generic.console-only")));
            return true;
        }
        Player pSender = (Player) sender;

        if (!pSender.hasPermission("permapiola.admin.inventory")) {
            pSender.sendMessage(ColorUtils.format(lang.getMsg(sender, "commands.generic.no-permission")));
            return true;
        }

        if (args.length < 2) {
            pSender.sendMessage(ColorUtils.format(lang.getMsg(sender, "commands.inventory.usage")));
            return true;
        }

        String subCommand = args[0].toLowerCase();
        Player target = Bukkit.getPlayerExact(args[1]);

        if (target == null) {
            pSender.sendMessage(ColorUtils.format(lang.getMsg(sender, "commands.generic.player-offline")));
            return true;
        }

        if (subCommand.equals("see")) {
            if (!pSender.hasPermission("permapiola.admin.inventory.see")) {
                pSender.sendMessage(ColorUtils.format(lang.getMsg(sender, "commands.generic.no-permission")));
                return true;
            }

            if (target.equals(pSender)) {
                pSender.sendMessage(ColorUtils.format(lang.getMsg(sender, "commands.inventory.see-self")));
                return true;
            }

            String guiTitle = lang.getMsg(sender, "commands.inventory.gui-title").replace("%player%", target.getName());
            org.bukkit.inventory.Inventory gui = Bukkit.createInventory(pSender, 45, ColorUtils.format(guiTitle));

            for (int i = 0; i < 36; i++) {
                gui.setItem(i, target.getInventory().getItem(i));
            }
            gui.setItem(36, target.getInventory().getBoots());
            gui.setItem(37, target.getInventory().getLeggings());
            gui.setItem(38, target.getInventory().getChestplate());
            gui.setItem(39, target.getInventory().getHelmet());
            gui.setItem(40, target.getInventory().getItemInOffHand());

            pSender.openInventory(gui);
            return true;
        }
        else if (subCommand.equals("death")) {
            if (!pSender.hasPermission("permapiola.admin.inventory.death")) {
                pSender.sendMessage(ColorUtils.format(lang.getMsg(sender, "commands.generic.no-permission")));
                return true;
            }

            if (!DeathStateManager.isDead(target.getUniqueId())) {
                pSender.sendMessage(ColorUtils.format(lang.getMsg(sender, "commands.inventory.death-not-dead")));
                return true;
            }

            if (!DeathInventoryManager.hasDeathInventory(target)) {
                pSender.sendMessage(ColorUtils.format(lang.getMsg(sender, "commands.inventory.give-no-inventory")));
                return true;
            }

            if (DeathInventoryEditListener.isLocked(target.getUniqueId())) {
                pSender.sendMessage(ColorUtils.format(lang.getMsg(sender, "commands.inventory.death-locked")));
                return true;
            }

            String guiTitle = lang.getMsg(sender, "commands.inventory.death-gui-title").replace("%player%", target.getName());
            org.bukkit.inventory.Inventory gui = Bukkit.createInventory(null, 45, ColorUtils.format(guiTitle));
            ItemStack[] savedInv = DeathInventoryManager.getSavedContents(target);

            if (savedInv != null) {
                for (int i = 0; i < 41 && i < savedInv.length; i++) {
                    if (savedInv[i] != null) {
                        gui.setItem(i, savedInv[i].clone());
                    }
                }
            }

            ItemStack filler = new ItemStack(Material.RED_STAINED_GLASS_PANE);
            ItemMeta meta = filler.getItemMeta();
            if (meta != null) {
                meta.setDisplayName(ColorUtils.format("&r "));
                filler.setItemMeta(meta);
            }
            for (int i = 41; i < 45; i++) {
                gui.setItem(i, filler);
            }

            DeathInventoryEditListener.setLocked(pSender.getUniqueId(), target.getUniqueId());
            pSender.openInventory(gui);
            return true;
        }
        else {
            pSender.sendMessage(ColorUtils.format(lang.getMsg(sender, "commands.inventory.invalid-argument")));
            return true;
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();
        if (!sender.hasPermission("permapiola.admin.inventory")) return completions;

        if (args.length == 1) {
            List<String> subs = Arrays.asList("see", "death");
            for (String s : subs) {
                if (s.startsWith(args[0].toLowerCase())) completions.add(s);
            }
        } else if (args.length == 2) {
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (p.getName().toLowerCase().startsWith(args[1].toLowerCase())) {
                    completions.add(p.getName());
                }
            }
        }
        return completions;
    }
}