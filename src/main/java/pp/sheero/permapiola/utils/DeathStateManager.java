package pp.sheero.permapiola.utils;

import pp.sheero.permapiola.PermaPiola;

import java.io.File;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class DeathStateManager {
    private static final Set<UUID> deadPlayers = new HashSet<>();
    private static final Map<UUID, String> discordMessageIds = new HashMap<>();
    private static int totalDeaths = 0;

    public static void setDead(UUID uuid, boolean isDead) {
        if (isDead) deadPlayers.add(uuid);
        else deadPlayers.remove(uuid);
    }

    public static boolean isDead(UUID uuid) {
        return deadPlayers.contains(uuid);
    }

    public static int incrementAndGetTotalDeaths() {
        totalDeaths++;
        return totalDeaths;
    }

    public static void decrementTotalDeaths() {
        if (totalDeaths > 0) totalDeaths--;
    }

    public static void setDiscordMessageId(UUID uuid, String messageId) {
        discordMessageIds.put(uuid, messageId);
    }

    public static String getDiscordMessageId(UUID uuid) {
        return discordMessageIds.get(uuid);
    }

    public static void removeDiscordMessageId(UUID uuid) {
        discordMessageIds.remove(uuid);
    }

    public static void saveData(PermaPiola plugin) {
        java.io.File dataFolder = new java.io.File(plugin.getDataFolder(), "data");
        if (!dataFolder.exists()) {
            dataFolder.mkdirs();
        }

        java.io.File dataFile = new java.io.File(dataFolder, "death_states.yml");
        org.bukkit.configuration.file.YamlConfiguration config = new org.bukkit.configuration.file.YamlConfiguration();

        java.util.List<String> list = new java.util.ArrayList<>();
        for (UUID uuid : deadPlayers) list.add(uuid.toString());
        config.set("dead-players", list);

        config.set("total-deaths", totalDeaths);

        for (Map.Entry<UUID, String> entry : discordMessageIds.entrySet()) {
            config.set("discord-messages." + entry.getKey().toString(), entry.getValue());
        }

        try { config.save(dataFile); } catch (Exception ignored) {}
    }

    public static void loadData(PermaPiola plugin) {
        java.io.File dataFolder = new java.io.File(plugin.getDataFolder(), "data");
        java.io.File dataFile = new java.io.File(dataFolder, "death_states.yml");
        if (!dataFile.exists()) return;
        org.bukkit.configuration.file.YamlConfiguration config = org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(dataFile);

        deadPlayers.clear();
        for (String uuidStr : config.getStringList("dead-players")) {
            try { deadPlayers.add(UUID.fromString(uuidStr)); } catch (Exception ignored) {}
        }

        totalDeaths = config.getInt("total-deaths", 0);

        discordMessageIds.clear();
        if (config.contains("discord-messages")) {
            for (String key : config.getConfigurationSection("discord-messages").getKeys(false)) {
                try {
                    UUID uuid = UUID.fromString(key);
                    String msgId = config.getString("discord-messages." + key);
                    discordMessageIds.put(uuid, msgId);
                } catch (Exception ignored) {}
            }
        }
    }
}