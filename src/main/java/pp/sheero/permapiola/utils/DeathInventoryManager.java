package pp.sheero.permapiola.utils;

import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import pp.sheero.permapiola.PermaPiola;

import java.io.File;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class DeathInventoryManager {
    private static final Map<UUID, ItemStack[]> deathInventories = new HashMap<>();
    private static PermaPiola plugin;
    private static File dataFile;

    public static void init(PermaPiola pl) {
        plugin = pl;
        File dataFolder = new File(plugin.getDataFolder(), "data");
        if (!dataFolder.exists()) {
            dataFolder.mkdirs();
        }
        dataFile = new File(dataFolder, "death_inventories.yml");
        loadData();
    }

    public static void loadData() {
        if (dataFile == null || !dataFile.exists()) return;
        YamlConfiguration config = YamlConfiguration.loadConfiguration(dataFile);

        deathInventories.clear();
        if (config.contains("inventories")) {
            for (String uuidStr : config.getConfigurationSection("inventories").getKeys(false)) {
                try {
                    UUID uuid = UUID.fromString(uuidStr);
                    List<?> list = config.getList("inventories." + uuidStr);
                    if (list != null) {
                        ItemStack[] contents = list.toArray(new ItemStack[0]);
                        deathInventories.put(uuid, contents);
                    }
                } catch (Exception ignored) {}
            }
        }
    }

    public static void saveDataSync() {
        if (plugin == null || dataFile == null) return;
        YamlConfiguration config = new YamlConfiguration();

        for (Map.Entry<UUID, ItemStack[]> entry : deathInventories.entrySet()) {
            config.set("inventories." + entry.getKey().toString(), Arrays.asList(entry.getValue()));
        }

        try { config.save(dataFile); } catch (Exception e) { e.printStackTrace(); }
    }

    public static void saveDataAsync() {
        if (plugin == null) return;
        Bukkit.getScheduler().runTaskAsynchronously(plugin, DeathInventoryManager::saveDataSync);
    }

    public static void saveInventory(Player player) {
        ItemStack[] original = player.getInventory().getContents();
        ItemStack[] copy = new ItemStack[original.length];
        for (int i = 0; i < original.length; i++) {
            if (original[i] != null) copy[i] = original[i].clone();
        }
        deathInventories.put(player.getUniqueId(), copy);
        saveDataAsync();
    }

    public static boolean hasDeathInventory(Player player) {
        return deathInventories.containsKey(player.getUniqueId());
    }

    public static void restoreInventory(Player player) {
        if (hasDeathInventory(player)) {
            player.getInventory().setContents(deathInventories.get(player.getUniqueId()));
            deathInventories.remove(player.getUniqueId());
            saveDataAsync();
        }
    }

    public static void clearInventory(Player player) {
        if (hasDeathInventory(player)) {
            deathInventories.remove(player.getUniqueId());
            saveDataAsync();
        }
    }

    public static ItemStack[] getSavedContents(Player player) {
        return getSavedContents(player.getUniqueId());
    }

    public static ItemStack[] getSavedContents(UUID uuid) {
        return deathInventories.get(uuid);
    }

    public static void updateSavedInventory(UUID uuid, ItemStack[] newContents) {
        if (deathInventories.containsKey(uuid)) {
            ItemStack[] copy = new ItemStack[41];
            for (int i = 0; i < newContents.length && i < copy.length; i++) {
                if (newContents[i] != null) copy[i] = newContents[i].clone();
            }
            deathInventories.put(uuid, copy);
            saveDataAsync();
        }
    }
}