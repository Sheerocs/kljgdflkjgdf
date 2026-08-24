package pp.sheero.permapiola.utilidad.commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.command.brigadier.argument.ArgumentTypes;
import io.papermc.paper.command.brigadier.argument.resolvers.selector.PlayerSelectorArgumentResolver;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import pp.sheero.permapiola.managers.LanguageManager;
import pp.sheero.permapiola.utils.ColorUtils;

import java.util.Arrays;
import java.util.List;

public class EnderChestCommand {

    public static void register(Commands commands, LanguageManager lang) {

        var echestNode = Commands.literal("enderchest")
                .requires(source -> source.getSender().hasPermission("permapiola.admin.enderchest"))

                .executes(context -> {
                    CommandSender sender = context.getSource().getSender();

                    if (!(sender instanceof Player)) {
                        sender.sendMessage(Component.translatable("permissions.requires.player").color(NamedTextColor.RED));
                        return Command.SINGLE_SUCCESS;
                    }

                    Player pSender = (Player) sender;
                    openSelfEnderChest(pSender, lang);
                    return Command.SINGLE_SUCCESS;
                })

                .then(Commands.argument("target", ArgumentTypes.player())
                        .executes(context -> {
                            CommandSender sender = context.getSource().getSender();

                            if (!(sender instanceof Player)) {
                                sender.sendMessage(Component.translatable("permissions.requires.player").color(NamedTextColor.RED));
                                return Command.SINGLE_SUCCESS;
                            }

                            Player pSender = (Player) sender;
                            PlayerSelectorArgumentResolver resolver = context.getArgument("target", PlayerSelectorArgumentResolver.class);

                            try {
                                List<Player> targets = resolver.resolve(context.getSource());

                                if (targets.isEmpty()) {
                                    sender.sendMessage(Component.translatable("argument.entity.notfound.player").color(NamedTextColor.RED));
                                    return Command.SINGLE_SUCCESS;
                                }

                                Player target = targets.get(0);

                                if (target.equals(pSender)) {
                                    openSelfEnderChest(pSender, lang);
                                    return Command.SINGLE_SUCCESS;
                                }

                                String rawTitle = lang.getMsg(pSender, "commands.echest.gui-title").replace("%player%", target.getName());
                                org.bukkit.inventory.Inventory gui = Bukkit.createInventory(null, 27, ColorUtils.format(rawTitle));

                                gui.setContents(target.getEnderChest().getContents());
                                pSender.openInventory(gui);
                                pSender.playSound(pSender.getLocation(), Sound.BLOCK_ENDER_CHEST_OPEN, 1.0f, 1.0f);

                            } catch (CommandSyntaxException e) {
                                sender.sendMessage(Component.translatable("argument.entity.notfound.player").color(NamedTextColor.RED));
                            }

                            return Command.SINGLE_SUCCESS;
                        })
                );

        for (String alias : Arrays.asList("enderchest", "echest", "ec")) {
            commands.register(Commands.literal(alias).redirect(echestNode.build()).build(), "Open an ender chest");
        }
    }

    private static void openSelfEnderChest(Player player, LanguageManager lang) {
        String rawTitle = lang.getMsg(player, "commands.echest.gui-title-self");
        org.bukkit.inventory.Inventory gui = Bukkit.createInventory(null, 27, ColorUtils.format(rawTitle));

        gui.setContents(player.getEnderChest().getContents());
        player.openInventory(gui);
        player.playSound(player.getLocation(), Sound.BLOCK_ENDER_CHEST_OPEN, 1.0f, 1.0f);
    }
}