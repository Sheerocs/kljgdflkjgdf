package pp.sheero.permapiola.hurricane;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import pp.sheero.permapiola.PermaPiola;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class DeathMessageManager {

    private final PermaPiola plugin;
    private final File dataFile;
    private final Map<UUID, String> deathMessages = new ConcurrentHashMap<>();
    private final Map<UUID, String> nameCache = new ConcurrentHashMap<>();

    public DeathMessageManager(PermaPiola plugin) {
        this.plugin = plugin;
        File dataFolder = new File(plugin.getDataFolder(), "data");
        if (!dataFolder.exists()) {
            dataFolder.mkdirs();
        }

        this.dataFile = new File(dataFolder, "deathmessage.yml");
        loadData();
    }

    private void loadData() {
        if (!dataFile.exists()) {
            try { dataFile.createNewFile(); } catch (IOException e) { e.printStackTrace(); }
        }
        YamlConfiguration config = YamlConfiguration.loadConfiguration(dataFile);
        boolean needsMigration = false;

        if (config.contains("messages")) {
            for (String uuidStr : config.getConfigurationSection("messages").getKeys(false)) {
                try {
                    UUID uuid = UUID.fromString(uuidStr);
                    String message = config.getString("messages." + uuidStr);
                    if (message != null) {
                        deathMessages.put(uuid, message);

                        @SuppressWarnings("deprecation")
                        OfflinePlayer op = Bukkit.getOfflinePlayer(uuid);
                        nameCache.put(uuid, op.getName() != null ? op.getName() : "Desconocido");
                    }
                } catch (IllegalArgumentException ignored) {}
            }
            needsMigration = true;
        }

        if (config.contains("players")) {
            for (String uuidStr : config.getConfigurationSection("players").getKeys(false)) {
                try {
                    UUID uuid = UUID.fromString(uuidStr);
                    String basePath = "players." + uuidStr;
                    String message = config.getString(basePath + ".message");

                    if (message != null) {
                        deathMessages.put(uuid, message);
                        String name = config.getString(basePath + ".name", "Desconocido");

                        if (name.equals("Desconocido")) {
                            @SuppressWarnings("deprecation")
                            OfflinePlayer op = Bukkit.getOfflinePlayer(uuid);
                            if (op.getName() != null) {
                                name = op.getName();
                                needsMigration = true;
                            }
                        }
                        nameCache.put(uuid, name);
                    }
                } catch (IllegalArgumentException ignored) {}
            }
        }

        if (needsMigration && !deathMessages.isEmpty()) {
            saveDataAsync();
        }
    }

    public void saveData() {
        Map<UUID, String> snapshot = new HashMap<>(deathMessages);
        Map<UUID, String> namesSnapshot = new HashMap<>(nameCache);
        YamlConfiguration config = new YamlConfiguration();

        for (Map.Entry<UUID, String> entry : snapshot.entrySet()) {
            String basePath = "players." + entry.getKey().toString();
            config.set(basePath + ".name", namesSnapshot.getOrDefault(entry.getKey(), "Desconocido"));
            config.set(basePath + ".message", entry.getValue());
        }

        try { config.save(dataFile); } catch (IOException e) { e.printStackTrace(); }
    }

    public void saveDataAsync() {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, this::saveData);
    }

    public String getMessage(UUID uuid) {
        return deathMessages.get(uuid);
    }

    public void setMessage(UUID uuid, String message) {
        deathMessages.put(uuid, message);
        Player p = Bukkit.getPlayer(uuid);
        if (p != null) {
            nameCache.put(uuid, p.getName());
        }
        saveDataAsync();
    }

    public boolean hasMessage(UUID uuid) {
        return deathMessages.containsKey(uuid);
    }
}