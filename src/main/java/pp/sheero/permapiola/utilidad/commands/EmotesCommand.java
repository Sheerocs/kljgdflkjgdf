package pp.sheero.permapiola.utilidad.commands;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import pp.sheero.permapiola.managers.EmoteManager;
import pp.sheero.permapiola.managers.LanguageManager;
import pp.sheero.permapiola.utils.ColorUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class EmotesCommand implements CommandExecutor, TabCompleter {

    private final EmoteManager emoteManager;
    private final LanguageManager lang;

    public EmotesCommand(EmoteManager emoteManager, LanguageManager lang) {
        this.emoteManager = emoteManager;
        this.lang = lang;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) return true;
        Player player = (Player) sender;

        int size = emoteManager.getEmotesMap().size();
        int invSize = (int) Math.ceil(size / 9.0) * 9;
        if (invSize < 9) invSize = 9;
        if (invSize > 54) invSize = 54;

        String rawTitle = lang.getMsg(player, "commands.emotes.gui-title");
        Component title = LegacyComponentSerializer.legacySection().deserialize(ColorUtils.format(rawTitle));

        Inventory inv = Bukkit.createInventory(null, invSize, title);

        for (Map.Entry<String, String[]> entry : emoteManager.getEmotesMap().entrySet()) {
            String trigger = entry.getValue()[0];
            String emote = entry.getValue()[1];

            ItemStack item = new ItemStack(Material.PAPER);
            ItemMeta meta = item.getItemMeta();

            String rawName = lang.getMsg(player, "commands.emotes.item-name").replace("%trigger%", trigger);
            meta.displayName(LegacyComponentSerializer.legacySection().deserialize(ColorUtils.format(rawName)));

            List<Component> lore = new ArrayList<>();
            lore.add(Component.empty());

            String rawResult = lang.getMsg(player, "commands.emotes.lore-result").replace("%emote%", emote);
            lore.add(LegacyComponentSerializer.legacySection().deserialize(ColorUtils.format(rawResult)));

            if (player.hasPermission("permapiola.donor")) {
                String rawAccess = lang.getMsg(player, "commands.emotes.lore-has-access");
                lore.add(LegacyComponentSerializer.legacySection().deserialize(ColorUtils.format(rawAccess)));
            } else {
                String rawNoAccess = lang.getMsg(player, "commands.emotes.lore-no-access");
                lore.add(LegacyComponentSerializer.legacySection().deserialize(ColorUtils.format(rawNoAccess)));
            }

            meta.lore(lore);
            item.setItemMeta(meta);
            inv.addItem(item);
        }
        player.openInventory(inv);
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        return new ArrayList<>();
    }
}