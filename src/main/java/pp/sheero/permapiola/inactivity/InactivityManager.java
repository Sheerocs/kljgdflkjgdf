package pp.sheero.permapiola.inactivity;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.Sound;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scheduler.BukkitTask;
import pp.sheero.permapiola.PermaPiola;
import pp.sheero.permapiola.hurricane.DeathStateManager;
import pp.sheero.permapiola.inventory.DeathInventoryManager;
import pp.sheero.permapiola.utils.ColorUtils;
import pp.sheero.permapiola.utils.TimeUtils;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class InactivityManager implements Listener {

    private final PermaPiola plugin;
    private final File dataFile;
    private final File historyFile;
    private YamlConfiguration historyConfig;

    private final Map<UUID, Long> cache = new ConcurrentHashMap<>();
    private final Map<UUID, String> nameCache = new ConcurrentHashMap<>();
    private final Map<UUID, SessionData> activeSessions = new ConcurrentHashMap<>();
    private final Set<UUID> deadByInactivity = ConcurrentHashMap.newKeySet();

    private long maxOfflineTimeMs;
    private long requiredPlaytimeMs;
    private long gracePeriodMs;

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

        File dataFolder = new File(plugin.getDataFolder(), "data");
        if (!dataFolder.exists()) dataFolder.mkdirs();

        this.dataFile = new File(dataFolder, "inactivity.yml");
        this.historyFile = new File(dataFolder, "inactivity_history.yml");

        loadConfigCache();
        loadData();
        loadHistory();

        startAutoSave();
        startOfflineChecker();
    }

    public void loadConfigCache() {
        this.maxOfflineTimeMs = TimeUtils.parseTimeString(plugin.getConfig().getString("inactivity.max-offline-time", "72h")) * 1000L;
        this.requiredPlaytimeMs = TimeUtils.parseTimeString(plugin.getConfig().getString("inactivity.required-playtime", "30m")) * 1000L;
        this.gracePeriodMs = TimeUtils.parseTimeString(plugin.getConfig().getString("inactivity.grace-period", "5m")) * 1000L;
    }

    private void loadHistory() {
        if (!historyFile.exists()) {
            try { historyFile.createNewFile(); } catch (IOException e) { e.printStackTrace(); }
        }
        historyConfig = YamlConfiguration.loadConfiguration(historyFile);
    }

    private void saveHistory() {
        try { historyConfig.save(historyFile); } catch (IOException ignored) {}
    }

    private void loadData() {
        if (!dataFile.exists()) {
            try { dataFile.createNewFile(); } catch (IOException e) { e.printStackTrace(); }
        }
        YamlConfiguration dataConfig = YamlConfiguration.loadConfiguration(dataFile);

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
                        if (op.getName() != null) name = op.getName();
                    }
                    cache.put(uuid, lastSeen);
                    nameCache.put(uuid, name);
                } catch (IllegalArgumentException ignored) {}
            }
        }

        if (dataConfig.contains("dead-by-inactivity")) {
            for (String uuidStr : dataConfig.getConfigurationSection("dead-by-inactivity").getKeys(false)) {
                try { deadByInactivity.add(UUID.fromString(uuidStr)); } catch (IllegalArgumentException ignored) {}
            }
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

        for (UUID u : deadByInactivity) {
            String name = DeathStateManager.getDeadPlayerNames().get(u);
            if (name == null) {
                name = nameSnapshot.getOrDefault(u, "Desconocido");
            }
            config.set("dead-by-inactivity." + u.toString(), name);
        }

        try { config.save(dataFile); } catch (IOException e) { e.printStackTrace(); }
    }

    private void startAutoSave() {
        Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, this::saveData, 6000L, 6000L);
    }

    // ==============================================
    // LÓGICA DE MUERTE OFFLINE (Baneo en Segundo Plano)
    // ==============================================
    private void startOfflineChecker() {
        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            long now = System.currentTimeMillis();
            for (Map.Entry<UUID, Long> entry : cache.entrySet()) {
                UUID uuid = entry.getKey();

                if (activeSessions.containsKey(uuid) && activeSessions.get(uuid).loginTime > 0) continue;

                if (now - entry.getValue() > maxOfflineTimeMs) {
                    executeOfflineDeath(uuid);
                }
            }
        }, 1200L, 1200L);
    }

    private void executeOfflineDeath(UUID uuid) {
        if (DeathStateManager.isDead(uuid)) {
            cache.remove(uuid);
            return;
        }

        cache.remove(uuid);
        deadByInactivity.add(uuid);
        DeathStateManager.setDead(uuid, true);
        int deathNum = DeathStateManager.incrementAndGetTotalDeaths();
        int day = plugin.getDayManager().getCurrentDay();

        String name = nameCache.getOrDefault(uuid, "Desconocido");

        String cause = plugin.getLanguageManager().getMsg(Bukkit.getConsoleSender(), "inactivity.death-message").replace("%player%", name);
        String broadcastRaw = plugin.getLanguageManager().getMsg(Bukkit.getConsoleSender(), "hurricane.death-event.broadcast-message").replace("%victim%", name);

        String hoverFormat = plugin.getLanguageManager().getMsg(Bukkit.getConsoleSender(), "inactivity.death-hover");

        String hoverRaw = hoverFormat.replace("%cause%", cause).replace("\\n", "\n");

        Bukkit.getConsoleSender().sendMessage(ColorUtils.format(broadcastRaw));

        String[] hoverLines = hoverRaw.split("\n");
        for (String line : hoverLines) {
            Bukkit.getConsoleSender().sendMessage(ColorUtils.format(line));
        }

        Component hoverComp = LegacyComponentSerializer.legacySection().deserialize(ColorUtils.format(hoverRaw));
        Component broadcastComp = LegacyComponentSerializer.legacySection().deserialize(ColorUtils.format(broadcastRaw))
                .hoverEvent(HoverEvent.showText(hoverComp));

        for (Player p : Bukkit.getOnlinePlayers()) {
            p.sendMessage(broadcastComp);

            String title = plugin.getLanguageManager().getMsg(p, "hurricane.death-event.title");
            String sub = plugin.getLanguageManager().getMsg(p, "hurricane.death-event.subtitle").replace("%victim%", name);
            p.sendTitle(ColorUtils.format(title), ColorUtils.format(sub), 10, 40, 10);

            p.playSound(p.getLocation(), Sound.ENTITY_ELDER_GUARDIAN_CURSE, 1.0f, 0.5f);
            p.playSound(p.getLocation(), Sound.ENTITY_ELDER_GUARDIAN_DEATH, 1.0f, 0.5f);
            p.playSound(p.getLocation(), Sound.ITEM_TRIDENT_RIPTIDE_3, 1.0f, 0.5f);
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
        }, 140L);

        if (plugin.getDeathMessageManager().hasMessage(uuid)) {
            String customMsg = plugin.getDeathMessageManager().getMessage(uuid);
            String consoleMsgRaw = plugin.getLanguageManager().getMsg(Bukkit.getConsoleSender(), "hurricane.death-message.broadcast")
                    .replace("%player%", name).replace("%message%", customMsg);
            Bukkit.getConsoleSender().sendMessage(ColorUtils.format(consoleMsgRaw));

            for (Player p : Bukkit.getOnlinePlayers()) {
                String pMsg = plugin.getLanguageManager().getMsg(p, "hurricane.death-message.broadcast")
                        .replace("%player%", name).replace("%message%", customMsg);
                p.sendMessage(ColorUtils.format(pMsg));
            }
        }

        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "customwhitelist remove " + name);
        plugin.getDiscordManager().sendOfflineDeathEmbed(uuid, name, cause, deathNum, day);

        saveData();
    }

    // ==============================================
    // LÓGICA DE HISTORIAL (LOG)
    // ==============================================
    public void logIncompleteSession(UUID uuid, String name, long loginMs, long logoutMs) {
        String path = "history." + uuid.toString();
        int nextIndex = 1;
        if (historyConfig.contains(path + ".sessions")) {
            nextIndex = historyConfig.getConfigurationSection(path + ".sessions").getKeys(false).size() + 1;
        }

        java.time.format.DateTimeFormatter formatter = java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
        java.time.ZoneId zone = java.time.ZoneId.of("America/Argentina/Buenos_Aires");

        String loginStr = java.time.LocalDateTime.ofInstant(java.time.Instant.ofEpochMilli(loginMs), zone).format(formatter);
        String logoutStr = java.time.LocalDateTime.ofInstant(java.time.Instant.ofEpochMilli(logoutMs), zone).format(formatter);

        long durationSecs = (logoutMs - loginMs) / 1000L;
        String durStr = pp.sheero.permapiola.utils.TimeUtils.formatTime(durationSecs,
                plugin.getLanguageManager().getMsg(Bukkit.getConsoleSender(), "playtime.units.week"),
                plugin.getLanguageManager().getMsg(Bukkit.getConsoleSender(), "playtime.units.day"),
                plugin.getLanguageManager().getMsg(Bukkit.getConsoleSender(), "playtime.units.hour"),
                plugin.getLanguageManager().getMsg(Bukkit.getConsoleSender(), "playtime.units.minute"),
                plugin.getLanguageManager().getMsg(Bukkit.getConsoleSender(), "playtime.units.second"));

        historyConfig.set(path + ".name", name);
        String sessionPath = path + ".sessions." + nextIndex;
        historyConfig.set(sessionPath + ".login", loginStr);
        historyConfig.set(sessionPath + ".logout", logoutStr);
        historyConfig.set(sessionPath + ".duration", durStr);

        saveHistory();
    }

    public void clearHistory(UUID uuid) {
        if (historyConfig.contains("history." + uuid.toString())) {
            historyConfig.set("history." + uuid.toString(), null);
            saveHistory();
        }
    }

    // ==============================================
    // GETTERS PARA EL COMANDO
    // ==============================================
    public Set<String> getPlayersWithHistory() {
        Set<String> names = new HashSet<>();
        if (!historyConfig.contains("history")) return names;
        for (String key : historyConfig.getConfigurationSection("history").getKeys(false)) {
            String name = historyConfig.getString("history." + key + ".name");
            if (name != null) names.add(name);
        }
        return names;
    }

    public UUID getUUIDFromHistory(String name) {
        if (!historyConfig.contains("history")) return null;
        for (String key : historyConfig.getConfigurationSection("history").getKeys(false)) {
            if (name.equalsIgnoreCase(historyConfig.getString("history." + key + ".name"))) {
                try { return UUID.fromString(key); } catch (Exception ignored) {}
            }
        }
        return null;
    }

    public List<String> getHistoryLogs(UUID uuid, org.bukkit.command.CommandSender sender, pp.sheero.permapiola.core.LanguageManager lang) {
        List<String> logs = new ArrayList<>();
        String path = "history." + uuid.toString() + ".sessions";
        if (!historyConfig.contains(path)) return logs;

        for (String key : historyConfig.getConfigurationSection(path).getKeys(false)) {
            String login = historyConfig.getString(path + "." + key + ".login");
            String logout = historyConfig.getString(path + "." + key + ".logout");
            String dur = historyConfig.getString(path + "." + key + ".duration");

            String rawEntry = lang.getMsg(sender, "commands.inactivity.log-entry")
                    .replace("%login%", login)
                    .replace("%logout%", logout)
                    .replace("%duration%", dur);
            logs.add(rawEntry);
        }
        return logs;
    }

    public Set<UUID> getDeadByInactivity() { return deadByInactivity; }

    public UUID getDeadUUIDByName(String name) {
        for (UUID uuid : deadByInactivity) {
            String deadName = DeathStateManager.getDeadPlayerNames().get(uuid);
            if (deadName != null && deadName.equalsIgnoreCase(name)) return uuid;
        }
        return null;
    }

    public void removeDeadByInactivity(UUID uuid) { deadByInactivity.remove(uuid); }

    public void resetPlayerTimer(UUID uuid) {
        cache.put(uuid, System.currentTimeMillis());
        saveData();
    }

    // ==============================================
    // EVENTOS DE JUGADOR (Join / Quit)
    // ==============================================
    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();
        long now = System.currentTimeMillis();

        nameCache.put(uuid, player.getName());

        if (DeathStateManager.isDead(uuid)) {
            if (deadByInactivity.contains(uuid)) {
                if (!DeathInventoryManager.hasDeathInventory(player)) {
                    DeathInventoryManager.saveInventory(player);
                    player.getInventory().clear();
                    player.setGameMode(org.bukkit.GameMode.SPECTATOR);

                    Bukkit.getScheduler().runTaskLater(plugin, () -> {
                        String specTitle = plugin.getLanguageManager().getMsg(player, "hurricane.death-event.spectator-title");
                        String specSub = plugin.getLanguageManager().getMsg(player, "hurricane.death-event.spectator-subtitle");
                        player.sendTitle(ColorUtils.format(specTitle), ColorUtils.format(specSub), 10, 40, 10);
                    }, 20L);
                } else {
                    if (player.getGameMode() != org.bukkit.GameMode.SPECTATOR) {
                        player.setGameMode(org.bukkit.GameMode.SPECTATOR);
                    }
                }
            }
            cache.remove(uuid);
            activeSessions.remove(uuid);
            return;
        }

        long lastSeen = now;
        if (cache.containsKey(uuid)) {
            lastSeen = cache.get(uuid);
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

        clearHistory(player.getUniqueId());

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
                logIncompleteSession(uuid, player.getName(), session.loginTime, now);
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