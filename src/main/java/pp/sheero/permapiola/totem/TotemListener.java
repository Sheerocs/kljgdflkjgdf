package pp.sheero.permapiola.totem;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityResurrectEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.projectiles.ProjectileSource;
import pp.sheero.permapiola.PermaPiola;
import pp.sheero.permapiola.core.LanguageManager;
import pp.sheero.permapiola.teams.TeamManager;
import pp.sheero.permapiola.utils.ColorUtils;
import pp.sheero.permapiola.hurricane.DeathStateManager;
import pp.sheero.permapiola.dementialwheel.DementialEventType;
import pp.sheero.permapiola.dementialwheel.DementialWheelManager;

public class TotemListener implements Listener {

    private final PermaPiola plugin;
    private final TotemManager totemManager;
    private final LanguageManager languageManager;

    private double totemDropChanceCache;

    public TotemListener(PermaPiola plugin, TotemManager totemManager, LanguageManager languageManager) {
        this.plugin = plugin;
        this.totemManager = totemManager;
        this.languageManager = languageManager;
        loadConfigCache();
    }

    public void loadConfigCache() {
        this.totemDropChanceCache = plugin.getConfig().getDouble("demential-wheel.settings.totem-drop.chance", 0.10);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTotemUse(EntityResurrectEvent event) {
        if (!(event.getEntity() instanceof Player)) return;
        Player player = (Player) event.getEntity();

        if (DeathStateManager.isDead(player.getUniqueId())) return;

        boolean doubleTotem = false;
        DementialWheelManager wheelManager = plugin.getDementialWheelManager();

        if (wheelManager.hasEvent(DementialEventType.TOTEM_DROP)) {
            if (Math.random() <= this.totemDropChanceCache) {
                for (ItemStack item : player.getInventory().getContents()) {
                    if (item != null && item.getType() == Material.TOTEM_OF_UNDYING) {
                        item.setAmount(item.getAmount() - 1);
                        doubleTotem = true;
                        break;
                    }
                }
            }
        }

        totemManager.addTotem(player.getUniqueId(), doubleTotem ? 2 : 1);

        int totemsLeft = 0;
        for (ItemStack item : player.getInventory().getContents()) {
            if (item != null && item.getType() == Material.TOTEM_OF_UNDYING) {
                totemsLeft += item.getAmount();
            }
        }
        totemsLeft = Math.max(0, totemsLeft - 1);

        String rawWorldName = player.getWorld().getName();
        String worldDisplay;

        if (rawWorldName.equals("world_permapiola_fallen_memories")) {
            worldDisplay = "Fallen Memories";
        } else if (rawWorldName.endsWith("_nether")) {
            worldDisplay = "Nether";
        } else if (rawWorldName.endsWith("_the_end")) {
            worldDisplay = "End";
        } else {
            worldDisplay = "Over";
        }

        String customCause = null;
        Entity damager = null;
        String vanillaCauseName = null;

        if (player.hasMetadata("DementialDamage")) {
            customCause = player.getMetadata("DementialDamage").get(0).asString();
        } else {
            EntityDamageEvent lastDamage = player.getLastDamageCause();
            if (lastDamage != null) {
                if (lastDamage instanceof EntityDamageByEntityEvent) {
                    damager = ((EntityDamageByEntityEvent) lastDamage).getDamager();
                } else {
                    vanillaCauseName = lastDamage.getCause().name();
                }
            }
        }

        String formattedUserName = player.getName();
        String xStr = String.valueOf(player.getLocation().getBlockX());
        String yStr = String.valueOf(player.getLocation().getBlockY());
        String zStr = String.valueOf(player.getLocation().getBlockZ());
        String totemsStr = String.valueOf(totemsLeft);

        org.bukkit.scoreboard.Team userTeam = TeamManager.getTeam(player);

        CommandSender console = Bukkit.getConsoleSender();
        Component consoleDamageComponent = Component.text("Desconocido");
        if (customCause != null) {
            consoleDamageComponent = Component.text(customCause);
        } else if (damager != null) {
            consoleDamageComponent = getFormattedDamagerComponent(damager, console);
        } else if (vanillaCauseName != null) {
            String translatedCause = languageManager.getMsg(console, "totems.causes." + vanillaCauseName.toLowerCase());
            if (translatedCause.startsWith("Error:") || translatedCause.startsWith("Falta mensaje")) {
                consoleDamageComponent = Component.text(formatVanillaName(vanillaCauseName));
            } else {
                consoleDamageComponent = Component.text(translatedCause);
            }
        }

        String consoleBroadcastRaw = doubleTotem ?
                languageManager.getMsg(console, "totems.broadcast-double").replace("%player%", formattedUserName) :
                languageManager.getMsg(console, "totems.broadcast").replace("%player%", formattedUserName);

        console.sendMessage(ColorUtils.format(consoleBroadcastRaw));

        String consoleHoverRaw = languageManager.getMsg(console, "totems.hover-details")
                .replace("%x%", xStr)
                .replace("%y%", yStr)
                .replace("%z%", zStr)
                .replace("%world%", worldDisplay)
                .replace("%left%", totemsStr)
                .replace("\\n", "\n");

        for (String line : consoleHoverRaw.split("\n")) {
            Component lineComp = LegacyComponentSerializer.legacySection().deserialize(ColorUtils.format(line))
                    .replaceText(net.kyori.adventure.text.TextReplacementConfig.builder()
                            .matchLiteral("%damage%")
                            .replacement(consoleDamageComponent)
                            .build());
            console.sendMessage(lineComp);
        }

        for (Player online : Bukkit.getOnlinePlayers()) {

            Component damageComponent = Component.text("Desconocido");
            if (customCause != null) {
                damageComponent = Component.text(customCause);
            } else if (damager != null) {
                damageComponent = getFormattedDamagerComponent(damager, online);
            } else if (vanillaCauseName != null) {
                String translatedCause = languageManager.getMsg(online, "totems.causes." + vanillaCauseName.toLowerCase());
                if (translatedCause.startsWith("Error:") || translatedCause.startsWith("Falta mensaje")) {
                    damageComponent = Component.text(formatVanillaName(vanillaCauseName));
                } else {
                    damageComponent = Component.text(translatedCause);
                }
            }

            String hoverRaw = languageManager.getMsg(online, "totems.hover-details")
                    .replace("%x%", xStr)
                    .replace("%y%", yStr)
                    .replace("%z%", zStr)
                    .replace("%world%", worldDisplay)
                    .replace("%left%", totemsStr)
                    .replace("\\n", "\n");

            Component hoverComp = LegacyComponentSerializer.legacySection().deserialize(ColorUtils.format(hoverRaw))
                    .replaceText(net.kyori.adventure.text.TextReplacementConfig.builder()
                            .matchLiteral("%damage%")
                            .replacement(damageComponent)
                            .build());

            String broadcastRaw;
            if (doubleTotem) {
                broadcastRaw = languageManager.getMsg(online, "totems.broadcast-double").replace("%player%", formattedUserName);
            } else {
                broadcastRaw = languageManager.getMsg(online, "totems.broadcast").replace("%player%", formattedUserName);
            }

            Component broadcastComp = LegacyComponentSerializer.legacySection().deserialize(ColorUtils.format(broadcastRaw))
                    .hoverEvent(HoverEvent.showText(hoverComp));

            if (online.hasPermission("permapiola.staff") && (online.getGameMode() == GameMode.CREATIVE || online.getGameMode() == GameMode.SPECTATOR)) {
                broadcastComp = broadcastComp.clickEvent(ClickEvent.runCommand("/tp " + xStr + " " + yStr + " " + zStr));
            }

            online.sendMessage(broadcastComp);

            if (online.equals(player)) continue;

            TotemManager.TotemProfile profile = totemManager.getProfile(online.getUniqueId());
            boolean playSound = false;

            if (profile.soundMode.equals("ALL")) {
                playSound = true;
            } else if (profile.soundMode.equals("TEAM")) {
                org.bukkit.scoreboard.Team onlineTeam = TeamManager.getTeam(online);
                if (userTeam != null && userTeam.equals(onlineTeam)) {
                    playSound = true;
                }
            }
            if (playSound) {
                try {
                    online.playSound(online.getLocation(), Sound.valueOf(profile.soundType), 1.0f, 1.0f);
                } catch (IllegalArgumentException e) {
                    online.playSound(online.getLocation(), Sound.ITEM_TOTEM_USE, 1.0f, 1.0f);
                }
            }
        }
    }

    private Component getFormattedDamagerComponent(Entity damager, CommandSender receiver) {
        if (damager == null) return Component.text("Desconocido");

        if (damager.getCustomName() != null) {
            return LegacyComponentSerializer.legacySection().deserialize(damager.getCustomName());
        }

        if (damager instanceof Player) {
            return Component.text(damager.getName());
        }

        if (damager instanceof Projectile) {
            ProjectileSource shooter = ((Projectile) damager).getShooter();
            if (shooter instanceof Entity) {
                return getFormattedDamagerComponent((Entity) shooter, receiver);
            }
            return Component.translatable(damager.getType().translationKey());
        }

        if (damager instanceof TNTPrimed) {
            Entity source = ((TNTPrimed) damager).getSource();
            if (source != null) {
                return getFormattedDamagerComponent(source, receiver);
            }
            return Component.text("TNT");
        }

        if (damager instanceof org.bukkit.entity.AreaEffectCloud) {
            ProjectileSource source = ((org.bukkit.entity.AreaEffectCloud) damager).getSource();

            if (source instanceof org.bukkit.entity.EnderDragon) {
                String translatedCause = languageManager.getMsg(receiver, "totems.causes.dragon_breath");
                return Component.text(!translatedCause.startsWith("Error:") && !translatedCause.startsWith("Falta mensaje") ? translatedCause : "Dragon Breath");
            }

            if (source instanceof Entity) {
                return getFormattedDamagerComponent((Entity) source, receiver);
            }

            if (damager.getWorld().getName().endsWith("_the_end")) {
                String translatedCause = languageManager.getMsg(receiver, "totems.causes.dragon_breath");
                return Component.text(!translatedCause.startsWith("Error:") && !translatedCause.startsWith("Falta mensaje") ? translatedCause : "Dragon Breath");
            }
        }

        return Component.translatable(damager.getType().translationKey());
    }

    private String formatVanillaName(String rawName) {
        if (rawName == null || rawName.isEmpty()) return "Desconocido";
        if (rawName.equalsIgnoreCase("tnt")) return "TNT";

        String formatted = rawName.replace('_', ' ').toLowerCase();
        return formatted.substring(0, 1).toUpperCase() + formatted.substring(1);
    }
}