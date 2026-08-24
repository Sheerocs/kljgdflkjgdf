package pp.sheero.permapiola.managers;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.scheduler.BukkitTask;
import pp.sheero.permapiola.PermaPiola;
import pp.sheero.permapiola.utils.ColorUtils;
import pp.sheero.permapiola.utils.DeathStateManager;
import pp.sheero.permapiola.utils.TimeUtils;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class InactivityManager implements Listener {

    private final PermaPiola plugin;
    private final File dataFile;

    private final Map<UUID, Long> cache = new ConcurrentHashMap<>();
    private final Map<UUID, String> nameCache = new ConcurrentHashMap<>();
    private final Map<UUID, SessionData> activeSessions = new ConcurrentHashMap<>();

    private final long maxOfflineTimeMs;
    private final long requiredPlaytimeMs;
    private final long gracePeriodMs;

    private static class SessionData {
        long accumulatedTime = 0;
        long lastDisconnectTime = 0;
        long loginTime = 0;
        boolean isSafe = false;
        long originalLastSeen = 0;
        BukkitTask pendingTask = null;
    }

    public InactivityManager(PermaPiola plugin) {
        this.plugin = plugin;

        String timeString = plugin.getConfig().getString("inactivity.max-offline-time", "48h");
        this.maxOfflineTimeMs = TimeUtils.parseTimeString(timeString) * 1000L;

        String playtimeString = plugin.getConfig().getString("inactivity.required-playtime", "30m");
        this.requiredPlaytimeMs = TimeUtils.parseTimeString(playtimeString) * 1000L;

        String graceString = plugin.getConfig().getString("inactivity.grace-period", "5m");
        this.gracePeriodMs = TimeUtils.parseTimeString(graceString) * 1000L;

        File dataFolder = new File(plugin.getDataFolder(), "data");
        if (!dataFolder.exists()) {
            dataFolder.mkdirs();
        }

        this.dataFile = new File(dataFolder, "offline_afk_data.yml");
        loadData();
        startAutoSave();
    }

    private void loadData() {
        if (!dataFile.exists()) {
            try { dataFile.createNewFile(); } catch (IOException e) { e.printStackTrace(); }
        }
        YamlConfiguration dataConfig = YamlConfiguration.loadConfiguration(dataFile);
        boolean needsMigration = false;

        if (dataConfig.contains("players")) {
            for (String uuidStr : dataConfig.getConfigurationSection("players").getKeys(false)) {
                try {
                    UUID uuid = UUID.fromString(uuidStr);
                    String basePath = "players." + uuidStr;

                    long lastSeen = dataConfig.getLong(basePath + ".last-seen", System.currentTimeMillis());
                    String name = dataConfig.getString(basePath + ".name", "Desconocido");

                    if (name.equals("Desconocido")) {
                        @SuppressWarnings("deprecation")
                        OfflinePlayer op = Bukkit.getOfflinePlayer(uuid);
                        if (op.getName() != null) {
                            name = op.getName();
                            needsMigration = true;
                        }
                    }

                    cache.put(uuid, lastSeen);
                    nameCache.put(uuid, name);
                } catch (IllegalArgumentException ignored) {}
            }
        }

        if (needsMigration) {
            Bukkit.getScheduler().runTaskAsynchronously(plugin, this::saveData);
        }
    }

    public void saveData() {
        Map<UUID, Long> snapshot = new HashMap<>(cache);
        Map<UUID, String> nameSnapshot = new HashMap<>(nameCache);

        for (Player p : Bukkit.getOnlinePlayers()) {
            SessionData session = activeSessions.get(p.getUniqueId());
            if (session != null && session.originalLastSeen > 0) {
                snapshot.put(p.getUniqueId(), session.originalLastSeen);
                nameSnapshot.put(p.getUniqueId(), p.getName());
            }
        }

        YamlConfiguration config = new YamlConfiguration();

        java.time.format.DateTimeFormatter formatter = java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");
        java.time.ZoneId zone = java.time.ZoneId.of("America/Argentina/Buenos_Aires");

        for (Map.Entry<UUID, Long> entry : snapshot.entrySet()) {
            String path = "players." + entry.getKey().toString();
            long timestamp = entry.getValue();

            java.time.Instant instant = java.time.Instant.ofEpochMilli(timestamp);
            java.time.LocalDateTime date = java.time.LocalDateTime.ofInstant(instant, zone);

            config.set(path + ".name", nameSnapshot.getOrDefault(entry.getKey(), "Desconocido"));
            config.set(path + ".last-seen", timestamp);
            config.set(path + ".date", date.format(formatter));
        }

        try { config.save(dataFile); } catch (IOException e) { e.printStackTrace(); }
    }

    private void startAutoSave() {
        Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, this::saveData, 6000L, 6000L);
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();
        long now = System.currentTimeMillis();

        nameCache.put(uuid, player.getName());

        if (DeathStateManager.isDead(uuid)) {
            cache.remove(uuid);
            activeSessions.remove(uuid);
            return;
        }

        long lastSeen = now;
        if (cache.containsKey(uuid)) {
            lastSeen = cache.get(uuid);
            long timePassed = now - lastSeen;

            if (timePassed > maxOfflineTimeMs) {
                Bukkit.getScheduler().runTask(plugin, () -> {
                    player.setMetadata("DementialDamage", new FixedMetadataValue(plugin, "Inactivity"));
                    try {
                        player.setHealth(0.0);
                    } finally {
                        player.removeMetadata("DementialDamage", plugin);
                    }
                });

                cache.remove(uuid);
                activeSessions.remove(uuid);
                return;
            }
        }

        cache.remove(uuid);

        SessionData session = activeSessions.computeIfAbsent(uuid, k -> new SessionData());

        if (session.originalLastSeen == 0) {
            session.originalLastSeen = lastSeen;
        }

        if (session.lastDisconnectTime > 0 && (now - session.lastDisconnectTime) > gracePeriodMs) {
            session.accumulatedTime = 0;
            session.isSafe = false;
        }

        session.loginTime = now;

        if (!session.isSafe) {
            long timeRemaining = requiredPlaytimeMs - session.accumulatedTime;

            if (timeRemaining <= 0) {
                markAsSafe(player, session);
            } else {
                long ticksRemaining = timeRemaining / 50L;
                session.pendingTask = Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    markAsSafe(player, session);
                }, ticksRemaining);
            }
        }
    }

    private void markAsSafe(Player player, SessionData session) {
        session.isSafe = true;
        session.accumulatedTime = 0;
        session.originalLastSeen = System.currentTimeMillis();

        cache.put(player.getUniqueId(), session.originalLastSeen);

        String rawMessage = plugin.getLanguageManager().getMsg(player, "inactivity.safe-message");
        player.sendMessage(ColorUtils.format(rawMessage));
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();
        long now = System.currentTimeMillis();

        nameCache.put(uuid, player.getName());

        if (DeathStateManager.isDead(uuid)) {
            activeSessions.remove(uuid);
            return;
        }

        SessionData session = activeSessions.get(uuid);
        if (session != null) {
            if (session.pendingTask != null) {
                session.pendingTask.cancel();
            }

            if (!session.isSafe && session.loginTime > 0) {
                session.accumulatedTime += (now - session.loginTime);
            }

            session.lastDisconnectTime = now;
            session.loginTime = 0;

            if (session.isSafe) {
                cache.put(uuid, now);
                session.originalLastSeen = now;
            } else {
                cache.put(uuid, session.originalLastSeen);
            }
        } else {
            cache.put(uuid, now);
        }
    }
}