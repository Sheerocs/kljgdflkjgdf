package pp.sheero.permapiola.totem;

import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import pp.sheero.permapiola.PermaPiola;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class TotemManager {

    private final PermaPiola plugin;
    private final File dataFile;

    public static class TotemProfile {
        public int count = 0;
        public String soundMode = "ALL";
        public String soundType = "ITEM_TOTEM_USE";
    }

    private final Map<UUID, TotemProfile> cache = new ConcurrentHashMap<>();

    public TotemManager(PermaPiola plugin) {
        this.plugin = plugin;
        File dataFolder = new File(plugin.getDataFolder(), "data");
        if (!dataFolder.exists()) {
            dataFolder.mkdirs();
        }

        this.dataFile = new File(dataFolder, "totem_data.yml");
        loadData();
        startAutoSave();
    }

    private void loadData() {
        if (!dataFile.exists()) {
            try { dataFile.createNewFile(); } catch (IOException e) { e.printStackTrace(); }
        }
        YamlConfiguration dataConfig = YamlConfiguration.loadConfiguration(dataFile);

        if (dataConfig.contains("players")) {
            for (String uuidStr : dataConfig.getConfigurationSection("players").getKeys(false)) {
                TotemProfile profile = new TotemProfile();
                profile.count = dataConfig.getInt("players." + uuidStr + ".count", 0);
                profile.soundMode = dataConfig.getString("players." + uuidStr + ".mode", "ALL");
                profile.soundType = dataConfig.getString("players." + uuidStr + ".type", "ITEM_TOTEM_USE");
                try {
                    cache.put(UUID.fromString(uuidStr), profile);
                } catch (IllegalArgumentException ignored) {}
            }
        }
    }

    public void saveData() {
        Map<UUID, TotemProfile> snapshot = new HashMap<>();
        cache.forEach((uuid, profile) -> {
            TotemProfile p = new TotemProfile();
            p.count = profile.count;
            p.soundMode = profile.soundMode;
            p.soundType = profile.soundType;
            snapshot.put(uuid, p);
        });

        YamlConfiguration config = new YamlConfiguration();
        for (Map.Entry<UUID, TotemProfile> entry : snapshot.entrySet()) {
            String path = "players." + entry.getKey().toString();
            config.set(path + ".count", entry.getValue().count);
            config.set(path + ".mode", entry.getValue().soundMode);
            config.set(path + ".type", entry.getValue().soundType);
        }
        try { config.save(dataFile); } catch (IOException e) { e.printStackTrace(); }
    }

    private void startAutoSave() {
        Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, this::saveData, 6000L, 6000L);
    }

    public TotemProfile getProfile(UUID uuid) {
        return cache.computeIfAbsent(uuid, k -> new TotemProfile());
    }

    public int getTotems(UUID uuid) {
        return getProfile(uuid).count;
    }

    public void addTotem(UUID uuid, int amount) {
        getProfile(uuid).count += amount;
    }

    public void setTotems(UUID uuid, int amount) {
        getProfile(uuid).count = amount;
    }

    public Map<UUID, TotemProfile> getAllProfiles() {
        return cache;
    }
}