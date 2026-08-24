package pp.sheero.permapiola.utilidad.commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import io.papermc.paper.command.brigadier.Commands;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import pp.sheero.permapiola.managers.LanguageManager;
import pp.sheero.permapiola.utils.ColorUtils;

import java.util.Arrays;

public class RenameCommand {

    public static void register(Commands commands, LanguageManager lang) {

        var renameNode = Commands.literal("rename")
                .requires(source -> source.getSender().hasPermission("permapiola.donor.rename"))

                .then(Commands.argument("name", StringArgumentType.greedyString())
                        .executes(context -> {
                            CommandSender sender = context.getSource().getSender();

                            if (!(sender instanceof Player)) {
                                sender.sendMessage(Component.translatable("permissions.requires.player").color(NamedTextColor.RED));
                                return Command.SINGLE_SUCCESS;
                            }

                            Player pSender = (Player) sender;
                            ItemStack itemInHand = pSender.getInventory().getItemInMainHand();

                            if (itemInHand.getType() == Material.AIR) {
                                pSender.sendMessage(ColorUtils.format(lang.getMsg(pSender, "commands.rename.no-item")));
                                return Command.SINGLE_SUCCESS;
                            }

                            // Extrae todo el texto escrito y le aplica los colores
                            String rawName = StringArgumentType.getString(context, "name");
                            String newName = ColorUtils.format(rawName);

                            ItemMeta meta = itemInHand.getItemMeta();
                            if (meta != null) {
                                meta.setDisplayName(newName);
                                itemInHand.setItemMeta(meta);
                            }

                            return Command.SINGLE_SUCCESS;
                        })
                );

        for (String alias : Arrays.asList("rename", "rn")) {
            commands.register(Commands.literal(alias).redirect(renameNode.build()).build(), "Rename item in hand");
        }
    }
}