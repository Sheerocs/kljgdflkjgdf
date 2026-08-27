package pp.sheero.permapiola.chat.commands;

import com.mojang.brigadier.Command;
import io.papermc.paper.command.brigadier.Commands;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import pp.sheero.permapiola.chat.EmoteManager;
import pp.sheero.permapiola.core.LanguageManager;
import pp.sheero.permapiola.utils.ColorUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class EmotesCommand {

    public static void register(Commands commands, EmoteManager emoteManager, LanguageManager lang) {

        var emotesNode = Commands.literal("emotes")
                .executes(context -> {
                    CommandSender sender = context.getSource().getSender();

                    if (!(sender instanceof Player)) {
                        sender.sendMessage(Component.translatable("permissions.requires.player").color(NamedTextColor.RED));
                        return Command.SINGLE_SUCCESS;
                    }

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
                    return Command.SINGLE_SUCCESS;
                });

        commands.register(emotesNode.build(), "Abre la lista de emotes disponibles");
    }
}