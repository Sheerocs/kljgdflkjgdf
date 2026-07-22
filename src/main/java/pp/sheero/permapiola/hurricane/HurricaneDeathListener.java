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
import pp.sheero.permapiola.managers.LanguageManager;
import pp.sheero.permapiola.teams.TeamManager;
import pp.sheero.permapiola.utils.ColorUtils;
import pp.sheero.permapiola.utils.DeathStateManager;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

public class HurricaneDeathListener implements Listener {

    private final PermaPiola plugin;
    private final LanguageManager lang;
    private final ZoneId argentinaZone = ZoneId.of("America/Argentina/Buenos_Aires");

    private long durationHoursCache;
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
        this.durationHoursCache = config.getLong("hurricane.duration-hours", 1);
        this.headNameCache = config.getString("hurricane.head-name", "&c&l%victim%");
        this.headLoreCache = config.getStringList("hurricane.head-lore");
        this.extraSpinChanceOtherCache = config.getDouble("demential-wheel.settings.extra-spin-chance-other", 1.0);
        this.extraSpinChanceOverworldCache = config.getDouble("demential-wheel.settings.extra-spin-chance-overworld", 0.50);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onHurricaneDeath(PlayerDeathEvent event) {
        Player victim = event.getEntity();

        // 1. Identificar la ruta del mensaje en el .yml si es una muerte custom
        String customCauseKey = null;
        if (victim.hasMetadata("DementialDamage")) {
            String customCause = victim.getMetadata("DementialDamage").get(0).asString();
            if (customCause.equals("Acid Rain")) {
                customCauseKey = "demential-wheel.death-messages.acid-rain";
            } else if (customCause.equals("Inactivity")) {
                customCauseKey = "inactivity.death-message";
            }
        }

        // 2. Early return si el jugador ya está registrado como muerto
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

        // 3. Capturar el mensaje base y cancelar el anuncio global de Minecraft
        String vanillaDeathMsg = event.getDeathMessage();
        event.setDeathMessage(null);

        // 4. Preparar el mensaje FORZADO EN ESPAÑOL para Discord y la Cabeza del jugador
        String serverCause;
        if (customCauseKey != null) {
            serverCause = lang.getMsg(Bukkit.getConsoleSender(), customCauseKey).replace("%player%", victim.getName());
        } else {
            serverCause = vanillaDeathMsg;
        }

        Location deathLocation = victim.getLocation();
        int currentDeathNumber = DeathStateManager.incrementAndGetTotalDeaths();
        int currentDay = plugin.getDayManager().getCurrentDay();

        // ¡Se envía el embed a Discord usando el mensaje en español (serverCause)!
        plugin.getDiscordManager().sendDeathEmbed(victim, serverCause, currentDeathNumber, currentDay);

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            victim.spigot().respawn();
            victim.teleport(deathLocation);
            victim.setGameMode(GameMode.SPECTATOR);
        }, 1L);

        LocalDateTime now = LocalDateTime.now(argentinaZone);
        String dateStr = now.format(DateTimeFormatter.ofPattern("dd-MM-yyyy"));
        String timeStr = now.format(DateTimeFormatter.ofPattern("HH:mm:ss"));
        String coordsStr = deathLocation.getBlockX() + " " + deathLocation.getBlockY() + " " + deathLocation.getBlockZ();
        int ping = victim.getPing();

        double currentTps = Bukkit.getTPS()[0];
        String tps = String.valueOf(Math.round(currentTps * 100.0) / 100.0);

        // Usamos la causa en español para el Lore de la cabeza
        dropPlayerHead(victim, dateStr, timeStr, serverCause);

        boolean wasActive = plugin.getHurricaneManager().isActive();
        long addedHours = this.durationHoursCache;

        victim.getWorld().strikeLightningEffect(deathLocation);

        // 5. Broadcast in-game (Ahora le pasamos también la llave "customCauseKey")
        broadcastAndPlayEffects(victim, vanillaDeathMsg, customCauseKey, coordsStr, ping, tps, wasActive, addedHours);

        plugin.getHurricaneManager().addHurricaneTime();

        if (!wasActive) {
            plugin.getDementialWheelManager().startSequence();
        } else {
            String worldName = victim.getWorld().getName();
            boolean isNetherOrEnd = worldName.endsWith("_nether") || worldName.endsWith("_the_end");

            double chance = isNetherOrEnd ? this.extraSpinChanceOtherCache : this.extraSpinChanceOverworldCache;

            if (Math.random() <= chance) {
                long totalDurationSeconds = addedHours * 3600;
                long eventDuration = isNetherOrEnd ? totalDurationSeconds : (totalDurationSeconds / 2);

                plugin.getDementialWheelManager().startExtraSequence(eventDuration);
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
            } else {
                String specTitle = lang.getMsg(victim, "hurricane.death-event.spectator-title");
                String specSub = lang.getMsg(victim, "hurricane.death-event.spectator-subtitle");
                victim.sendTitle(ColorUtils.format(specTitle), ColorUtils.format(specSub), 10, 40, 10);
            }
        }, 140L);
    }

    private void dropPlayerHead(Player victim, String date, String time, String cause) {
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
        victim.getWorld().dropItemNaturally(victim.getLocation(), head);
    }

    private void broadcastAndPlayEffects(Player victim, String vanillaMsg, String customCauseKey, String coords, int ping, String tps, boolean wasActive, long addedHours) {
        String rawWorldName = victim.getWorld().getName();
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

        for (Player p : Bukkit.getOnlinePlayers()) {

            String playerCause;
            if (customCauseKey != null) {
                playerCause = lang.getMsg(p, customCauseKey).replace("%player%", victim.getName());
            } else {
                playerCause = vanillaMsg;
            }

            String title = lang.getMsg(p, "hurricane.death-event.title");
            String sub = lang.getMsg(p, "hurricane.death-event.subtitle").replace("%victim%", victim.getName());
            p.sendTitle(ColorUtils.format(title), ColorUtils.format(sub), 10, 40, 10);

            p.playSound(p.getLocation(), Sound.ENTITY_ELDER_GUARDIAN_CURSE, 1.0f, 0.5f);
            p.playSound(p.getLocation(), Sound.ENTITY_ELDER_GUARDIAN_DEATH, 1.0f, 0.5f);
            p.playSound(p.getLocation(), Sound.ITEM_TRIDENT_RIPTIDE_3, 1.0f, 0.5f);

            String msgRaw = lang.getMsg(p, "hurricane.death-event.broadcast-message").replace("%victim%", victim.getName());
            String hoverRaw = lang.getMsg(p, "hurricane.death-event.broadcast-hover")
                    .replace("%cause%", playerCause)
                    .replace("%x%", String.valueOf(victim.getLocation().getBlockX()))
                    .replace("%y%", String.valueOf(victim.getLocation().getBlockY()))
                    .replace("%z%", String.valueOf(victim.getLocation().getBlockZ()))
                    .replace("%world%", worldDisplay)
                    .replace("%ping%", String.valueOf(ping))
                    .replace("%tps%", tps)
                    .replace("\\n", "\n");

            String command = TeamManager.hasTeam(p) ? "/tc " + coords : "/msg " + p.getName() + " " + coords;

            Component message = LegacyComponentSerializer.legacySection().deserialize(ColorUtils.format(msgRaw))
                    .hoverEvent(HoverEvent.showText(LegacyComponentSerializer.legacySection().deserialize(ColorUtils.format(hoverRaw))))
                    .clickEvent(ClickEvent.runCommand(command));

            p.sendMessage(message);

            if (wasActive) {
                String addedMsg = lang.getMsg(p, "hurricane.death-event.hurricane-added").replace("%time%", String.valueOf(addedHours));
                p.sendMessage(ColorUtils.format(addedMsg));
            } else {
                p.sendMessage(ColorUtils.format(lang.getMsg(p, "hurricane.death-event.hurricane-start")));
            }
        }
    }
}