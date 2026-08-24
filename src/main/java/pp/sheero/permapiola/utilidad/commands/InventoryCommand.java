package pp.sheero.permapiola.utilidad.commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.command.brigadier.argument.ArgumentTypes;
import io.papermc.paper.command.brigadier.argument.resolvers.selector.PlayerSelectorArgumentResolver;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import pp.sheero.permapiola.managers.LanguageManager;
import pp.sheero.permapiola.utilidad.listeners.DeathInventoryEditListener;
import pp.sheero.permapiola.utils.ColorUtils;
import pp.sheero.permapiola.utils.DeathInventoryManager;
import pp.sheero.permapiola.utils.DeathStateManager;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

public class InventoryCommand {

    public static void register(Commands commands, LanguageManager lang) {

        var invNode = Commands.literal("inv")
                .requires(source -> source.getSender().hasPermission("permapiola.admin.inventory"))

                .then(Commands.literal("see")
                        .requires(source -> source.getSender().hasPermission("permapiola.admin.inventory.see"))
                        .then(Commands.argument("target", ArgumentTypes.player())

                                .suggests((context, builder) -> {
                                    String input = builder.getRemaining().toLowerCase();
                                    for (Player p : Bukkit.getOnlinePlayers()) {
                                        if (p.getName().toLowerCase().startsWith(input)) {
                                            builder.suggest(p.getName());
                                        }
                                    }
                                    return builder.buildFuture();
                                })

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
                                            pSender.sendMessage(ColorUtils.format(lang.getMsg(pSender, "commands.inventory.see-self")));
                                            return Command.SINGLE_SUCCESS;
                                        }

                                        String guiTitle = lang.getMsg(pSender, "commands.inventory.gui-title").replace("%player%", target.getName());
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

                                    } catch (CommandSyntaxException e) {
                                        sender.sendMessage(Component.translatable("argument.entity.notfound.player").color(NamedTextColor.RED));
                                    }
                                    return Command.SINGLE_SUCCESS;
                                })
                        )
                )

                .then(Commands.literal("grave")
                        .requires(source -> source.getSender().hasPermission("permapiola.admin.inventory.death"))
                        .then(Commands.argument("target", StringArgumentType.word())

                                .suggests((context, builder) -> {
                                    String input = builder.getRemaining().toLowerCase();
                                    for (String name : DeathStateManager.getDeadPlayerNames().values()) {
                                        if (name.toLowerCase().startsWith(input)) {
                                            builder.suggest(name);
                                        }
                                    }
                                    return builder.buildFuture();
                                })
                                .executes(context -> {
                                    CommandSender sender = context.getSource().getSender();

                                    if (!(sender instanceof Player)) {
                                        sender.sendMessage(Component.translatable("permissions.requires.player").color(NamedTextColor.RED));
                                        return Command.SINGLE_SUCCESS;
                                    }

                                    Player pSender = (Player) sender;
                                    String targetName = StringArgumentType.getString(context, "target");

                                    Player onlineTarget = Bukkit.getPlayerExact(targetName);
                                    UUID targetUUID;
                                    String finalTargetName;

                                    if (onlineTarget != null) {
                                        targetUUID = onlineTarget.getUniqueId();
                                        finalTargetName = onlineTarget.getName();
                                    } else {
                                        @SuppressWarnings("deprecation")
                                        OfflinePlayer offlineTarget = Bukkit.getOfflinePlayer(targetName);
                                        targetUUID = offlineTarget.getUniqueId();
                                        finalTargetName = offlineTarget.getName() != null ? offlineTarget.getName() : targetName;
                                    }

                                    if (!DeathStateManager.isDead(targetUUID)) {
                                        pSender.sendMessage(ColorUtils.format(lang.getMsg(pSender, "commands.inventory.death-not-dead")));
                                        return Command.SINGLE_SUCCESS;
                                    }

                                    ItemStack[] savedInv = DeathInventoryManager.getSavedContents(targetUUID);
                                    if (savedInv == null) {
                                        pSender.sendMessage(ColorUtils.format(lang.getMsg(pSender, "commands.inventory.give-no-inventory")));
                                        return Command.SINGLE_SUCCESS;
                                    }

                                    if (DeathInventoryEditListener.isLocked(targetUUID)) {
                                        pSender.sendMessage(ColorUtils.format(lang.getMsg(pSender, "commands.inventory.death-locked")));
                                        return Command.SINGLE_SUCCESS;
                                    }

                                    String guiTitle = lang.getMsg(pSender, "commands.inventory.death-gui-title").replace("%player%", finalTargetName);
                                    org.bukkit.inventory.Inventory gui = Bukkit.createInventory(null, 45, ColorUtils.format(guiTitle));

                                    for (int i = 0; i < 41 && i < savedInv.length; i++) {
                                        if (savedInv[i] != null) {
                                            gui.setItem(i, savedInv[i].clone());
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

                                    DeathInventoryEditListener.setLocked(pSender.getUniqueId(), targetUUID);
                                    pSender.openInventory(gui);

                                    return Command.SINGLE_SUCCESS;
                                })
                        )
                );

        for (String alias : Arrays.asList("inv", "inventory")) {
            commands.register(Commands.literal(alias).redirect(invNode.build()).build(), "Manage player inventories");
        }
    }
}