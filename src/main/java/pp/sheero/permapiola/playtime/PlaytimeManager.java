package pp.sheero.permapiola.playtime;

import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import pp.sheero.permapiola.PermaPiola;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class PlaytimeManager {

    private final PermaPiola plugin;
    private final File dataFile;
    private final Map<UUID, Long> playtimeCache = new ConcurrentHashMap<>();
    private final Map<UUID, String> nameCache = new ConcurrentHashMap<>();

    public PlaytimeManager(PermaPiola plugin) {
        this.plugin = plugin;
        File dataFolder = new File(plugin.getDataFolder(), "data");
        if (!dataFolder.exists()) {
            dataFolder.mkdirs();
        }

        this.dataFile = new File(dataFolder, "playtime_data.yml");
        loadData();
        startAutoSave();
    }

    private void loadData() {
        if (!dataFile.exists()) {
            try { dataFile.createNewFile(); } catch (IOException e) { e.printStackTrace(); }
        }
        org.bukkit.configuration.file.YamlConfiguration dataConfig = org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(dataFile);

        String rootKey = dataConfig.contains("players") ? "players" : (dataConfig.contains("playtime") ? "playtime" : null);

        if (rootKey != null) {
            for (String uuidStr : dataConfig.getConfigurationSection(rootKey).getKeys(false)) {
                try {
                    UUID uuid = UUID.fromString(uuidStr);
                    String basePath = rootKey + "." + uuidStr;

                    if (dataConfig.isLong(basePath) || dataConfig.isInt(basePath)) {
                        playtimeCache.put(uuid, dataConfig.getLong(basePath));

                        org.bukkit.OfflinePlayer op = Bukkit.getOfflinePlayer(uuid);
                        nameCache.put(uuid, op.getName() != null ? op.getName() : "Desconocido");
                    }
                    else if (dataConfig.isConfigurationSection(basePath)) {
                        playtimeCache.put(uuid, dataConfig.getLong(basePath + ".time"));
                        String savedName = dataConfig.getString(basePath + ".name", "Desconocido");

                        if (savedName.equals("Desconocido")) {
                            org.bukkit.OfflinePlayer op = Bukkit.getOfflinePlayer(uuid);
                            if (op.getName() != null) savedName = op.getName();
                        }
                        nameCache.put(uuid, savedName);
                    }
                } catch (IllegalArgumentException ignored) {}
            }
        }
    }

    public void saveData() {
        Map<UUID, Long> timeSnapshot = new HashMap<>(playtimeCache);
        Map<UUID, String> nameSnapshot = new HashMap<>(nameCache);
        YamlConfiguration config = new YamlConfiguration();

        for (Map.Entry<UUID, Long> entry : timeSnapshot.entrySet()) {
            String path = "players." + entry.getKey().toString();
            config.set(path + ".name", nameSnapshot.getOrDefault(entry.getKey(), "Desconocido"));
            config.set(path + ".time", entry.getValue());
        }
        try { config.save(dataFile); } catch (IOException e) { e.printStackTrace(); }
    }

    private void startAutoSave() {
        Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, this::saveData, 6000L, 6000L);
    }

    public long getPlaytime(UUID uuid) { return playtimeCache.getOrDefault(uuid, 0L); }

    public String getName(UUID uuid) { return nameCache.getOrDefault(uuid, "Desconocido"); }

    public UUID getUUIDByName(String name) {
        for (Map.Entry<UUID, String> entry : nameCache.entrySet()) {
            if (entry.getValue().equalsIgnoreCase(name)) {
                return entry.getKey();
            }
        }
        return null;
    }

    public void addPlaytime(UUID uuid, long seconds) {
        playtimeCache.merge(uuid, seconds, Long::sum);
        Player p = Bukkit.getPlayer(uuid);
        if (p != null) nameCache.put(uuid, p.getName());
    }

    public void setPlaytime(UUID uuid, long seconds) {
        playtimeCache.put(uuid, seconds);
        Player p = Bukkit.getPlayer(uuid);
        if (p != null) nameCache.put(uuid, p.getName());
    }

    public void updateNameCache(UUID uuid, String name) {
        if (name != null && !name.equals("Desconocido")) {
            nameCache.put(uuid, name);
        }
    }

    public Map<UUID, Long> getAllPlaytimes() { return playtimeCache; }
}