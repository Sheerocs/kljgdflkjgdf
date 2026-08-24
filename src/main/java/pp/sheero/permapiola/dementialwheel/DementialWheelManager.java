package pp.sheero.permapiola.dementialwheel;

import org.bukkit.Bukkit;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Player;
import pp.sheero.permapiola.PermaPiola;
import pp.sheero.permapiola.managers.LanguageManager;
import pp.sheero.permapiola.utils.ColorUtils;

import java.io.File;
import java.util.*;

public class DementialWheelManager {

    private final PermaPiola plugin;
    private final LanguageManager lang;
    private final Random random = new Random();

    private final Set<DementialEventType> activeEvents = EnumSet.noneOf(DementialEventType.class);
    private final Map<DementialEventType, Integer> expirationTasks = new HashMap<>();
    private final Map<DementialEventType, Long> eventExpirations = new HashMap<>();
    private final java.util.Set<java.util.UUID> erodedPlayers = new java.util.HashSet<>();

    private final List<Integer> sequenceTasks = new ArrayList<>();

    private final File dataFile;

    private int putrifiedWaterDuration;
    private int putrifiedWaterAmplifier;
    private double acidRainDamage;
    private int toxicAirLayer;
    private double brokenGearExtraDamageChance;
    private double lifeErosionHeartsToRemove;

    public DementialWheelManager(PermaPiola plugin, LanguageManager lang) {
        this.plugin = plugin;
        this.lang = lang;

        File dataFolder = new File(plugin.getDataFolder(), "data");
        if (!dataFolder.exists()) {
            dataFolder.mkdirs();
        }
        this.dataFile = new File(dataFolder, "demential_data.yml");

        loadConfigCache();
    }

    public void loadConfigCache() {
        org.bukkit.configuration.file.FileConfiguration cfg = plugin.getConfig();
        this.putrifiedWaterDuration = cfg.getInt("demential-wheel.settings.putrified-water.wither-duration-ticks", 40);
        this.putrifiedWaterAmplifier = cfg.getInt("demential-wheel.settings.putrified-water.wither-amplifier", 0);
        this.acidRainDamage = cfg.getDouble("demential-wheel.settings.acid-rain.damage", 1.0);
        this.toxicAirLayer = cfg.getInt("demential-wheel.settings.toxic-air.y-layer", 40);
        this.brokenGearExtraDamageChance = cfg.getDouble("demential-wheel.settings.broken-gear.extra-damage-percentage", 0.50);
        this.lifeErosionHeartsToRemove = cfg.getDouble("demential-wheel.settings.life-erosion.hearts-to-remove", 1.0);
    }

    public int getPutrifiedWaterDuration() { return putrifiedWaterDuration; }
    public int getPutrifiedWaterAmplifier() { return putrifiedWaterAmplifier; }
    public double getAcidRainDamage() { return acidRainDamage; }
    public int getToxicAirLayer() { return toxicAirLayer; }
    public double getBrokenGearExtraDamageChance() { return brokenGearExtraDamageChance; }

    public boolean isActive() { return !activeEvents.isEmpty(); }

    public boolean hasEvent(DementialEventType event) { return activeEvents.contains(event); }

    public void cancelPendingSequences() {
        for (int taskId : sequenceTasks) {
            Bukkit.getScheduler().cancelTask(taskId);
        }
        sequenceTasks.clear();
    }

    public void stopWheel() {
        cancelPendingSequences();

        for (int taskId : expirationTasks.values()) Bukkit.getScheduler().cancelTask(taskId);
        expirationTasks.clear();
        eventExpirations.clear();

        if (activeEvents.contains(DementialEventType.LIFE_EROSION)) {
            restoreLifeErosion();
        }

        activeEvents.clear();
        saveData();
    }

    private void restoreLifeErosion() {
        double healthToRestore = this.lifeErosionHeartsToRemove * 2.0;

        java.util.Iterator<java.util.UUID> iterator = erodedPlayers.iterator();
        while (iterator.hasNext()) {
            java.util.UUID uuid = iterator.next();
            Player p = Bukkit.getPlayer(uuid);

            if (p != null && p.isOnline()) {
                org.bukkit.attribute.AttributeInstance maxHealth = p.getAttribute(Attribute.MAX_HEALTH);
                if (maxHealth != null) {
                    maxHealth.setBaseValue(maxHealth.getBaseValue() + healthToRestore);
                }
                iterator.remove();
            }
        }
    }

    public boolean isRolling() {
        return !sequenceTasks.isEmpty();
    }

    public void checkPendingRestoration(Player p) {
        if (!hasEvent(DementialEventType.LIFE_EROSION) && erodedPlayers.contains(p.getUniqueId())) {
            double healthToRestore = this.lifeErosionHeartsToRemove * 2.0;

            org.bukkit.attribute.AttributeInstance maxHealth = p.getAttribute(Attribute.MAX_HEALTH);
            if (maxHealth != null) {
                maxHealth.setBaseValue(maxHealth.getBaseValue() + healthToRestore);
            }
            erodedPlayers.remove(p.getUniqueId());
            saveData();
        }
    }

    public void startSequence(Player victim) {
        startSequenceInternal(victim, -1, false);
    }

    public void startExtraSequence(Player victim, long durationSeconds) {
        startSequenceInternal(victim, durationSeconds, true);
    }

    // ==============================================================
    // LÓGICA DE MENSAJES DINÁMICOS Y MULTI-IDIOMA CORREGIDA
    // ==============================================================

    public void broadcastEventMessage(DementialEventType event) {
        String path = "demential-wheel.events." + event.name().toLowerCase();

        String consoleRaw = lang.getMsg(Bukkit.getConsoleSender(), path);
        Bukkit.getConsoleSender().sendMessage(ColorUtils.format(applyEventPlaceholders(consoleRaw, event, Bukkit.getConsoleSender())));

        for (Player p : Bukkit.getOnlinePlayers()) {
            String playerRaw = lang.getMsg(p, path);
            p.sendMessage(ColorUtils.format(applyEventPlaceholders(playerRaw, event, p)));
        }
    }

    private String applyEventPlaceholders(String rawMsg, DementialEventType event, org.bukkit.command.CommandSender sender) {
        switch (event) {
            case TOTEM_DROP:
                int totemChance = (int) (plugin.getConfig().getDouble("demential-wheel.settings.totem-drop.chance", 0.10) * 100);
                return rawMsg.replace("%chance%", String.valueOf(totemChance));
            case PUTRIFIED_WATER:
                int witherLevel = plugin.getConfig().getInt("demential-wheel.settings.putrified-water.wither-amplifier", 0) + 1;
                return rawMsg.replace("%level%", String.valueOf(witherLevel));
            case TOXIC_AIR:
                int layer = plugin.getConfig().getInt("demential-wheel.settings.toxic-air.y-layer", 40);
                return rawMsg.replace("%layer%", String.valueOf(layer));
            case BROKEN_GEAR:
                int gearChance = (int) (plugin.getConfig().getDouble("demential-wheel.settings.broken-gear.extra-damage-percentage", 0.50) * 100);
                return rawMsg.replace("%chance%", String.valueOf(gearChance));
            case LIFE_EROSION:
                double hearts = plugin.getConfig().getDouble("demential-wheel.settings.life-erosion.hearts-to-remove", 1.0);
                String slotWordKey = (hearts == 1.0) ? "demential-wheel.words.slot-singular" : "demential-wheel.words.slot-plural";
                String slotWord = lang.getMsg(sender, slotWordKey); // Usa el sender específico (jugador o consola)

                String heartsStr = (hearts == Math.floor(hearts)) ? String.valueOf((int)hearts) : String.valueOf(hearts);
                return rawMsg.replace("%hearts%", heartsStr).replace("%slot_word%", slotWord);
            case ACID_RAIN:
            case NONE:
            default:
                return rawMsg;
        }
    }

    private void broadcastMessage(String path) {
        String consoleMsg = ColorUtils.format(lang.getMsg(Bukkit.getConsoleSender(), path));
        Bukkit.getConsoleSender().sendMessage(consoleMsg);

        for (Player p : Bukkit.getOnlinePlayers()) {
            p.sendMessage(ColorUtils.format(lang.getMsg(p, path)));
        }
    }

    private void startSequenceInternal(Player victim, long durationSeconds, boolean isExtraSpin) {
        if (!isExtraSpin) {
            broadcastMessage("demential-wheel.warning");
        }

        if (victim != null && plugin.getDeathMessageManager().hasMessage(victim.getUniqueId())) {
            String customMsg = plugin.getDeathMessageManager().getMessage(victim.getUniqueId());

            String consoleRaw = lang.getMsg(Bukkit.getConsoleSender(), "hurricane.death-message.broadcast")
                    .replace("%player%", victim.getName())
                    .replace("%message%", customMsg);
            Bukkit.getConsoleSender().sendMessage(ColorUtils.format(consoleRaw));

            for (Player p : Bukkit.getOnlinePlayers()) {
                String pRaw = lang.getMsg(p, "hurricane.death-message.broadcast")
                        .replace("%player%", victim.getName())
                        .replace("%message%", customMsg);
                p.sendMessage(ColorUtils.format(pRaw));
            }
        }

        sequenceTasks.add(Bukkit.getScheduler().runTaskLater(plugin, () -> broadcastMessage("demential-wheel.spin-3"), 300L).getTaskId());
        sequenceTasks.add(Bukkit.getScheduler().runTaskLater(plugin, () -> broadcastMessage("demential-wheel.spin-2"), 320L).getTaskId());
        sequenceTasks.add(Bukkit.getScheduler().runTaskLater(plugin, () -> broadcastMessage("demential-wheel.spin-1"), 340L).getTaskId());

        sequenceTasks.add(Bukkit.getScheduler().runTaskLater(plugin, () -> {
            sequenceTasks.clear();

            DementialEventType picked = pickRandomEvent();
            if (picked == DementialEventType.NONE) return;

            activeEvents.add(picked);
            broadcastEventMessage(picked);
            executeOneTimeEvent(picked);

            if (durationSeconds > 0) {
                long expirationTime = System.currentTimeMillis() + (durationSeconds * 1000L);
                eventExpirations.put(picked, expirationTime);

                int taskId = Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    removeEvent(picked);
                }, durationSeconds * 20L).getTaskId();
                expirationTasks.put(picked, taskId);
            }
            saveData();
        }, 400L).getTaskId());
    }

    public void removeEvent(DementialEventType event) {
        if (!activeEvents.contains(event)) return;

        activeEvents.remove(event);
        expirationTasks.remove(event);
        eventExpirations.remove(event);

        if (event == DementialEventType.LIFE_EROSION) {
            restoreLifeErosion();
        }

        String niceEventName = pp.sheero.permapiola.dementialwheel.DementialWheelCommand.formatEventName(event.name());

        String consoleRaw = lang.getMsg(Bukkit.getConsoleSender(), "demential-wheel.event-ended").replace("%event%", niceEventName);
        Bukkit.getConsoleSender().sendMessage(ColorUtils.format(consoleRaw));

        for (Player p : Bukkit.getOnlinePlayers()) {
            String pRaw = lang.getMsg(p, "demential-wheel.event-ended").replace("%event%", niceEventName);
            p.sendMessage(ColorUtils.format(pRaw));
        }

        saveData();
    }

    private DementialEventType pickRandomEvent() {
        List<DementialEventType> available = new ArrayList<>();
        for (DementialEventType type : DementialEventType.values()) {
            if (type != DementialEventType.NONE && !activeEvents.contains(type)) {
                available.add(type);
            }
        }
        if (available.isEmpty()) return DementialEventType.NONE;
        return available.get(random.nextInt(available.size()));
    }

    public void forceEvent(DementialEventType event) {
        if (event == DementialEventType.NONE || activeEvents.contains(event)) return;
        activeEvents.add(event);
        executeOneTimeEvent(event);
        saveData();
    }

    private void executeOneTimeEvent(DementialEventType event) {
        if (event == DementialEventType.LIFE_EROSION) {
            double healthToRemove = this.lifeErosionHeartsToRemove * 2.0;

            for (Player p : Bukkit.getOnlinePlayers()) {
                if (p.getGameMode() == org.bukkit.GameMode.SPECTATOR || p.getGameMode() == org.bukkit.GameMode.CREATIVE || pp.sheero.permapiola.utils.DeathStateManager.isDead(p.getUniqueId())) continue;

                org.bukkit.attribute.AttributeInstance maxHealth = p.getAttribute(Attribute.MAX_HEALTH);
                if (maxHealth != null) {
                    double currentMax = maxHealth.getBaseValue();
                    maxHealth.setBaseValue(Math.max(2.0, currentMax - healthToRemove));
                    erodedPlayers.add(p.getUniqueId());
                    saveData();
                }
            }
        }
    }

    public void applyErosionIfMissing(Player p) {
        if (!hasEvent(DementialEventType.LIFE_EROSION)) return;
        if (p.getGameMode() == org.bukkit.GameMode.SPECTATOR || p.getGameMode() == org.bukkit.GameMode.CREATIVE || pp.sheero.permapiola.utils.DeathStateManager.isDead(p.getUniqueId())) return;
        if (erodedPlayers.contains(p.getUniqueId())) return;

        double healthToRemove = this.lifeErosionHeartsToRemove * 2.0;
        org.bukkit.attribute.AttributeInstance maxHealth = p.getAttribute(Attribute.MAX_HEALTH);

        if (maxHealth != null) {
            double currentMax = maxHealth.getBaseValue();
            maxHealth.setBaseValue(Math.max(2.0, currentMax - healthToRemove));
            erodedPlayers.add(p.getUniqueId());
            saveData();
        }
    }

    public void saveData() {
        org.bukkit.configuration.file.YamlConfiguration config = new org.bukkit.configuration.file.YamlConfiguration();

        List<String> list = new ArrayList<>();
        for (java.util.UUID uuid : erodedPlayers) {
            list.add(uuid.toString());
        }
        config.set("eroded-players", list);

        config.set("active-events", null);
        for (DementialEventType type : activeEvents) {
            long exp = eventExpirations.getOrDefault(type, -1L);
            config.set("active-events." + type.name(), exp);
        }

        try { config.save(dataFile); } catch (Exception ignored) {}
    }

    public void loadData() {
        if (!dataFile.exists()) return;
        org.bukkit.configuration.file.YamlConfiguration config = org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(dataFile);

        erodedPlayers.clear();
        for (String uuidStr : config.getStringList("eroded-players")) {
            try { erodedPlayers.add(java.util.UUID.fromString(uuidStr)); } catch (Exception ignored) {}
        }

        activeEvents.clear();
        expirationTasks.clear();
        eventExpirations.clear();

        if (config.contains("active-events")) {
            for (String eventStr : config.getConfigurationSection("active-events").getKeys(false)) {
                try {
                    DementialEventType type = DementialEventType.valueOf(eventStr);
                    long expirationTime = config.getLong("active-events." + eventStr, -1L);

                    if (expirationTime == -1L) {
                        activeEvents.add(type);
                    } else {
                        long timeRemainingMillis = expirationTime - System.currentTimeMillis();
                        if (timeRemainingMillis > 0) {
                            activeEvents.add(type);
                            eventExpirations.put(type, expirationTime);

                            long ticksRemaining = (timeRemainingMillis / 1000L) * 20L;
                            int taskId = Bukkit.getScheduler().runTaskLater(plugin, () -> {
                                removeEvent(type);
                            }, ticksRemaining).getTaskId();

                            expirationTasks.put(type, taskId);
                        }
                    }
                } catch (Exception ignored) {}
            }
        }
    }
}