package pp.sheero.permapiola.playtime;

import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
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
        YamlConfiguration dataConfig = YamlConfiguration.loadConfiguration(dataFile);
        if (dataConfig.contains("playtime")) {
            for (String uuidStr : dataConfig.getConfigurationSection("playtime").getKeys(false)) {
                try {
                    playtimeCache.put(UUID.fromString(uuidStr), dataConfig.getLong("playtime." + uuidStr));
                } catch (IllegalArgumentException ignored) {}
            }
        }
    }

    public void saveData() {
        Map<UUID, Long> snapshot = new HashMap<>(playtimeCache);
        YamlConfiguration config = new YamlConfiguration();

        for (Map.Entry<UUID, Long> entry : snapshot.entrySet()) {
            config.set("playtime." + entry.getKey().toString(), entry.getValue());
        }
        try { config.save(dataFile); } catch (IOException e) { e.printStackTrace(); }
    }

    private void startAutoSave() {
        Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, this::saveData, 6000L, 6000L);
    }

    public long getPlaytime(UUID uuid) { return playtimeCache.getOrDefault(uuid, 0L); }

    public void addPlaytime(UUID uuid, long seconds) {
        playtimeCache.merge(uuid, seconds, Long::sum);
    }

    public void setPlaytime(UUID uuid, long seconds) { playtimeCache.put(uuid, seconds); }
    public Map<UUID, Long> getAllPlaytimes() { return playtimeCache; }
}