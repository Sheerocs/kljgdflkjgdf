package pp.sheero.permapiola.hurricane;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;
import pp.sheero.permapiola.PermaPiola;
import pp.sheero.permapiola.core.LanguageManager;
import pp.sheero.permapiola.utils.ColorUtils;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

public class HurricaneDeathListener implements Listener {

    private final PermaPiola plugin;
    private final LanguageManager lang;
    private final ZoneId argentinaZone = ZoneId.of("America/Argentina/Buenos_Aires");

    private String headNameCache;
    private List<String> headLoreCache;
    private double extraSpinChanceOtherCache;
    private double extraSpinChanceOverworldCache;

    public HurricaneDeathListener(PermaPiola plugin, LanguageManager lang) {
        this.plugin = plugin;
        this.lang = lang;
        loadConfigCache();
    }

    public void loadConfigCache() {
        org.bukkit.configuration.file.FileConfiguration config = plugin.getConfig();
        this.headNameCache = config.getString("hurricane.head-name", "&c&l%victim%");
        this.headLoreCache = config.getStringList("hurricane.head-lore");
        this.extraSpinChanceOtherCache = config.getDouble("demential-wheel.settings.extra-spin-chance-other", 1.0);
        this.extraSpinChanceOverworldCache = config.getDouble("demential-wheel.settings.extra-spin-chance-overworld", 0.50);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onHurricaneDeath(PlayerDeathEvent event) {
        Player victim = event.getEntity();

        String customCauseKey = null;
        if (victim.hasMetadata("DementialDamage")) {
            String customCause = victim.getMetadata("DementialDamage").get(0).asString();
            if (customCause.equals("Acid Rain")) {
                customCauseKey = "demential-wheel.death-messages.acid-rain";
            } else if (customCause.equals("Inactivity")) {
                customCauseKey = "inactivity.death-message";
            }
        }

        if (DeathStateManager.isDead(victim.getUniqueId())) {
            String vanillaDeathMsg = event.getDeathMessage();
            event.setDeathMessage(null);

            if (vanillaDeathMsg != null) {
                String consoleMsg = lang.getMsg(Bukkit.getConsoleSender(), "hurricane.death-event.staff-chat-death")
                        .replace("%cause%", vanillaDeathMsg);
                Bukkit.getConsoleSender().sendMessage(ColorUtils.format(consoleMsg));

                for (Player p : Bukkit.getOnlinePlayers()) {
                    if (p.hasPermission("permapiola.admin")) {
                        String staffMsg = lang.getMsg(p, "hurricane.death-event.staff-chat-death")
                                .replace("%cause%", vanillaDeathMsg);
                        p.sendMessage(ColorUtils.format(staffMsg));
                    }
                }
            }
            return;
        }

        String vanillaDeathMsg = event.getDeathMessage();
        event.setDeathMessage(null);

        String serverCause;
        if (customCauseKey != null) {
            serverCause = lang.getMsg(Bukkit.getConsoleSender(), customCauseKey).replace("%player%", victim.getName());
        } else {
            serverCause = vanillaDeathMsg;
        }

        Location deathLocation = victim.getLocation();
        int currentDeathNumber = DeathStateManager.incrementAndGetTotalDeaths();
        int currentDay = plugin.getDayManager().getCurrentDay();

        plugin.getDiscordManager().sendDeathEmbed(victim, serverCause, currentDeathNumber, currentDay);

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            victim.spigot().respawn();
            victim.teleport(deathLocation);
            victim.setGameMode(GameMode.SPECTATOR);
        }, 1L);

        LocalDateTime now = LocalDateTime.now(argentinaZone);
        String dateStr = now.format(DateTimeFormatter.ofPattern("dd-MM-yyyy"));
        String timeStr = now.format(DateTimeFormatter.ofPattern("HH:mm:ss"));
        int ping = victim.getPing();

        double currentTps = Bukkit.getTPS()[0];
        String tps = String.valueOf(Math.round(currentTps * 100.0) / 100.0);

        addPlayerHeadToDrops(event, victim, dateStr, timeStr, serverCause);

        boolean wasActive = plugin.getHurricaneManager().isActive();
        long addedSeconds = plugin.getHurricaneManager().getDurationSecondsCache();

        victim.getWorld().strikeLightningEffect(deathLocation);

        broadcastAndPlayEffects(victim, vanillaDeathMsg, customCauseKey, deathLocation, ping, tps, wasActive, addedSeconds);

        plugin.getHurricaneManager().addHurricaneTime();

        if (!wasActive) {
            plugin.getDementialWheelManager().startSequence(victim);
        } else {
            String worldName = victim.getWorld().getName();
            boolean isNetherOrEnd = worldName.endsWith("_nether") || worldName.endsWith("_the_end");

            double chance = isNetherOrEnd ? this.extraSpinChanceOtherCache : this.extraSpinChanceOverworldCache;

            if (Math.random() <= chance) {
                long eventDuration = isNetherOrEnd ? addedSeconds : (addedSeconds / 2);
                plugin.getDementialWheelManager().startExtraSequence(victim, eventDuration);
            }
        }

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            for (Player p : Bukkit.getOnlinePlayers()) {
                p.playSound(p.getLocation(), Sound.ENTITY_SKELETON_HORSE_DEATH, 1.0f, 0.5f);
            }
        }, 60L);

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            for (Player p : Bukkit.getOnlinePlayers()) {
                p.playSound(p.getLocation(), Sound.ITEM_TRIDENT_THUNDER, 1.0f, 0.5f);
                p.playSound(p.getLocation(), Sound.ENTITY_ZOMBIFIED_PIGLIN_DEATH, 1.0f, 0.5f);
            }

            if (!victim.hasPermission("permapiola.donor")) {
                victim.kickPlayer(ColorUtils.format(lang.getMsg(victim, "hurricane.death-event.kick-reason")));
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "customwhitelist remove " + victim.getName());
            } else {
                String specTitle = lang.getMsg(victim, "hurricane.death-event.spectator-title");
                String specSub = lang.getMsg(victim, "hurricane.death-event.spectator-subtitle");
                victim.sendTitle(ColorUtils.format(specTitle), ColorUtils.format(specSub), 10, 40, 10);
            }
        }, 140L);
    }

    private void addPlayerHeadToDrops(PlayerDeathEvent event, Player victim, String date, String time, String cause) {
        ItemStack head = new ItemStack(org.bukkit.Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) head.getItemMeta();
        if (meta != null) {
            meta.setOwningPlayer(victim);
            String headName = this.headNameCache;
            meta.setDisplayName(ColorUtils.format(headName.replace("%victim%", victim.getName())));

            List<String> lore = new ArrayList<>();
            for (String line : this.headLoreCache) {
                lore.add(ColorUtils.format(line
                        .replace("%date%", date)
                        .replace("%time%", time)
                        .replace("%cause%", cause)));
            }
            meta.setLore(lore);
            head.setItemMeta(meta);
        }

        event.getDrops().add(head);
    }

    private void broadcastAndPlayEffects(Player victim, String vanillaMsg, String customCauseKey, Location deathLoc, int ping, String tps, boolean wasActive, long addedSeconds) {
        String rawWorldName = deathLoc.getWorld().getName();
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

        String coordsStr = deathLoc.getBlockX() + " " + deathLoc.getBlockY() + " " + deathLoc.getBlockZ();

        String consoleCause = (customCauseKey != null) ? lang.getMsg(Bukkit.getConsoleSender(), customCauseKey).replace("%player%", victim.getName()) : vanillaMsg;
        String consoleMsgRaw = lang.getMsg(Bukkit.getConsoleSender(), "hurricane.death-event.broadcast-message").replace("%victim%", victim.getName());
        Bukkit.getConsoleSender().sendMessage(ColorUtils.format(consoleMsgRaw));

        String consoleHoverRaw = lang.getMsg(Bukkit.getConsoleSender(), "hurricane.death-event.broadcast-hover")
                .replace("%cause%", consoleCause)
                .replace("%x%", String.valueOf(deathLoc.getBlockX()))
                .replace("%y%", String.valueOf(deathLoc.getBlockY()))
                .replace("%z%", String.valueOf(deathLoc.getBlockZ()))
                .replace("%world%", worldDisplay)
                .replace("%ping%", String.valueOf(ping))
                .replace("%tps%", tps)
                .replace("\\n", "\n");

        String[] hoverLines = consoleHoverRaw.split("\n");
        int end = hoverLines.length - 1;
        while (end >= 0 && (hoverLines[end].trim().isEmpty() || hoverLines[end].toLowerCase().contains("click") || hoverLines[end].toLowerCase().contains("clic"))) {
            end--;
        }

        for (int i = 0; i <= end; i++) {
            Bukkit.getConsoleSender().sendMessage(ColorUtils.format(hoverLines[i]));
        }

        String consoleFormattedAdded = plugin.getHurricaneManager().getFormattedDuration(addedSeconds, Bukkit.getConsoleSender());

        if (wasActive) {
            String consoleAddedMsg = lang.getMsg(Bukkit.getConsoleSender(), "hurricane.death-event.hurricane-added").replace("%duration%", consoleFormattedAdded);
            Bukkit.getConsoleSender().sendMessage(ColorUtils.format(consoleAddedMsg));
        } else {
            String consoleStartMsg = lang.getMsg(Bukkit.getConsoleSender(), "hurricane.death-event.hurricane-start").replace("%duration%", consoleFormattedAdded);
            Bukkit.getConsoleSender().sendMessage(ColorUtils.format(consoleStartMsg));
        }

        for (Player p : Bukkit.getOnlinePlayers()) {

            String playerCause = (customCauseKey != null) ? lang.getMsg(p, customCauseKey).replace("%player%", victim.getName()) : vanillaMsg;

            String title = lang.getMsg(p, "hurricane.death-event.title");
            String sub = lang.getMsg(p, "hurricane.death-event.subtitle").replace("%victim%", victim.getName());
            p.sendTitle(ColorUtils.format(title), ColorUtils.format(sub), 10, 40, 10);

            p.playSound(p.getLocation(), Sound.ENTITY_ELDER_GUARDIAN_CURSE, 1.0f, 0.5f);
            p.playSound(p.getLocation(), Sound.ENTITY_ELDER_GUARDIAN_DEATH, 1.0f, 0.5f);
            p.playSound(p.getLocation(), Sound.ITEM_TRIDENT_RIPTIDE_3, 1.0f, 0.5f);

            String msgRaw = lang.getMsg(p, "hurricane.death-event.broadcast-message").replace("%victim%", victim.getName());
            String hoverRaw = lang.getMsg(p, "hurricane.death-event.broadcast-hover")
                    .replace("%cause%", playerCause)
                    .replace("%x%", String.valueOf(deathLoc.getBlockX()))
                    .replace("%y%", String.valueOf(deathLoc.getBlockY()))
                    .replace("%z%", String.valueOf(deathLoc.getBlockZ()))
                    .replace("%world%", worldDisplay)
                    .replace("%ping%", String.valueOf(ping))
                    .replace("%tps%", tps)
                    .replace("\\n", "\n");

            ClickEvent clickEvent = null;

            if (p.getGameMode() == GameMode.SPECTATOR || p.getGameMode() == GameMode.CREATIVE) {
                if (p.hasPermission("permapiola.admin")) {
                    clickEvent = ClickEvent.runCommand("/tp " + coordsStr);
                }
            } else if (p.getGameMode() == GameMode.SURVIVAL || p.getGameMode() == GameMode.ADVENTURE) {
                String clipboardText = coordsStr + " " + worldDisplay;
                clickEvent = ClickEvent.copyToClipboard(clipboardText);
            }

            Component message = LegacyComponentSerializer.legacySection().deserialize(ColorUtils.format(msgRaw))
                    .hoverEvent(HoverEvent.showText(LegacyComponentSerializer.legacySection().deserialize(ColorUtils.format(hoverRaw))));

            if (clickEvent != null) {
                message = message.clickEvent(clickEvent);
            }

            p.sendMessage(message);

            String formattedAdded = plugin.getHurricaneManager().getFormattedDuration(addedSeconds, p);

            if (wasActive) {
                String addedMsg = lang.getMsg(p, "hurricane.death-event.hurricane-added").replace("%duration%", formattedAdded);
                p.sendMessage(ColorUtils.format(addedMsg));
            } else {
                String startMsg = lang.getMsg(p, "hurricane.death-event.hurricane-start").replace("%duration%", formattedAdded);
                p.sendMessage(ColorUtils.format(startMsg));
            }
        }
    }
}