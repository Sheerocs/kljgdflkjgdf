package pp.sheero.permapiola.utilidad.commands;

import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import pp.sheero.permapiola.managers.LanguageManager;
import pp.sheero.permapiola.utils.ColorUtils;

import java.util.ArrayList;
import java.util.List;

public class EnderChestCommand implements CommandExecutor, TabCompleter {

    private final LanguageManager lang;

    public EnderChestCommand(LanguageManager lang) {
        this.lang = lang;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {

        if (!(sender instanceof Player)) {
            sender.sendMessage(ColorUtils.format(lang.getMsg(sender, "commands.generic.console-only")));
            return true;
        }
        Player pSender = (Player) sender;

        if (!pSender.hasPermission("permapiola.admin.enderchest")) {
            pSender.sendMessage(ColorUtils.format(lang.getMsg(sender, "commands.generic.no-permission")));
            return true;
        }

        if (args.length == 0) {
            openSelfEnderChest(pSender);
            return true;
        }

        Player target = Bukkit.getPlayerExact(args[0]);
        if (target == null) {
            sender.sendMessage(ColorUtils.format(lang.getMsg(sender, "commands.generic.player-offline")));
            return true;
        }

        if (target.equals(pSender)) {
            openSelfEnderChest(pSender);
            return true;
        }

        String rawTitle = lang.getMsg(sender, "commands.echest.gui-title").replace("%player%", target.getName());
        org.bukkit.inventory.Inventory gui = Bukkit.createInventory(null, 27, ColorUtils.format(rawTitle));

        gui.setContents(target.getEnderChest().getContents());
        pSender.openInventory(gui);
        pSender.playSound(pSender.getLocation(), Sound.BLOCK_ENDER_CHEST_OPEN, 1.0f, 1.0f);

        return true;
    }

    private void openSelfEnderChest(Player player) {
        String rawTitle = lang.getMsg(player, "commands.echest.gui-title-self");
        org.bukkit.inventory.Inventory gui = Bukkit.createInventory(null, 27, ColorUtils.format(rawTitle));

        gui.setContents(player.getEnderChest().getContents());
        player.openInventory(gui);
        player.playSound(player.getLocation(), Sound.BLOCK_ENDER_CHEST_OPEN, 1.0f, 1.0f);
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();
        if (!sender.hasPermission("permapiola.admin.enderchest")) return completions;

        if (args.length == 1) {
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (p.getName().toLowerCase().startsWith(args[0].toLowerCase())) {
                    completions.add(p.getName());
                }
            }
        }
        return completions;
    }
}