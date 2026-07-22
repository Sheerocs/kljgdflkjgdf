package pp.sheero.permapiola.utilidad.commands;

import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import pp.sheero.permapiola.managers.LanguageManager;
import pp.sheero.permapiola.utils.ColorUtils;

import java.util.ArrayList;
import java.util.List;

public class RenameCommand implements CommandExecutor, TabCompleter {

    private final LanguageManager lang;

    public RenameCommand(LanguageManager lang) {
        this.lang = lang;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(ColorUtils.format(lang.getMsg(sender, "commands.generic.console-only")));
            return true;
        }
        Player pSender = (Player) sender;

        if (!pSender.hasPermission("permapiola.donor.rename")) {
            pSender.sendMessage(ColorUtils.format(lang.getMsg(sender, "commands.generic.no-permission")));
            return true;
        }

        if (args.length == 0) {
            pSender.sendMessage(ColorUtils.format(lang.getMsg(sender, "commands.rename.usage")));
            return true;
        }

        ItemStack itemInHand = pSender.getInventory().getItemInMainHand();

        if (itemInHand.getType() == Material.AIR) {
            pSender.sendMessage(ColorUtils.format(lang.getMsg(sender, "commands.rename.no-item")));
            return true;
        }

        String newName = ColorUtils.format(String.join(" ", args));

        ItemMeta meta = itemInHand.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(newName);
            itemInHand.setItemMeta(meta);
        }

        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        return new ArrayList<>();
    }
}