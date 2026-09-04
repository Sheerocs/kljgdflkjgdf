package pp.sheero.permapiola.totem;

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

public class TotemManager {

    private final PermaPiola plugin;
    private final File dataFile;

    public static class TotemProfile {
        public String name = "Desconocido";
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

        this.dataFile = new File(dataFolder, "totem.yml");
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
                try {
                    UUID uuid = UUID.fromString(uuidStr);
                    TotemProfile profile = new TotemProfile();
                    String basePath = "players." + uuidStr;

                    profile.count = dataConfig.getInt(basePath + ".count", 0);
                    profile.soundMode = dataConfig.getString(basePath + ".mode", "ALL");
                    profile.soundType = dataConfig.getString(basePath + ".type", "ITEM_TOTEM_USE");

                    String savedName = dataConfig.getString(basePath + ".name", "Desconocido");

                    if (savedName.equals("Desconocido")) {
                        org.bukkit.OfflinePlayer op = Bukkit.getOfflinePlayer(uuid);
                        if (op.getName() != null) savedName = op.getName();
                    }
                    profile.name = savedName;

                    cache.put(uuid, profile);
                } catch (IllegalArgumentException ignored) {}
            }
        }
    }

    public void saveData() {
        Map<UUID, TotemProfile> snapshot = new HashMap<>();
        cache.forEach((uuid, profile) -> {
            TotemProfile p = new TotemProfile();
            p.name = profile.name;
            p.count = profile.count;
            p.soundMode = profile.soundMode;
            p.soundType = profile.soundType;
            snapshot.put(uuid, p);
        });

        YamlConfiguration config = new YamlConfiguration();
        for (Map.Entry<UUID, TotemProfile> entry : snapshot.entrySet()) {
            String path = "players." + entry.getKey().toString();
            config.set(path + ".name", entry.getValue().name);
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

    public String getName(UUID uuid) {
        return getProfile(uuid).name;
    }

    public UUID getUUIDByName(String name) {
        for (Map.Entry<UUID, TotemProfile> entry : cache.entrySet()) {
            if (entry.getValue().name.equalsIgnoreCase(name)) {
                return entry.getKey();
            }
        }
        return null;
    }

    public void updateNameCache(UUID uuid, String name) {
        if (name != null && !name.equals("Desconocido")) {
            getProfile(uuid).name = name;
        }
    }

    public void addTotem(UUID uuid, int amount) {
        TotemProfile p = getProfile(uuid);
        p.count += amount;
        Player player = Bukkit.getPlayer(uuid);
        if (player != null) p.name = player.getName();
    }

    public void setTotems(UUID uuid, int amount) {
        TotemProfile p = getProfile(uuid);
        p.count = amount;
        Player player = Bukkit.getPlayer(uuid);
        if (player != null) p.name = player.getName();
    }

    public Map<UUID, TotemProfile> getAllProfiles() {
        return cache;
    }
}