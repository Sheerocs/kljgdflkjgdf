package pp.sheero.permapiola.hurricane;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import pp.sheero.permapiola.PermaPiola;

import java.io.File;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class DeathStateManager {
    private static final Set<UUID> deadPlayers = new HashSet<>();
    private static final Map<UUID, String> deadPlayerNames = new HashMap<>();
    private static final Map<UUID, String> discordMessageIds = new HashMap<>();
    private static int totalDeaths = 0;

    public static Map<UUID, String> getDeadPlayerNames() {
        return deadPlayerNames;
    }

    public static void setDead(UUID uuid, boolean isDead) {
        if (isDead) {
            deadPlayers.add(uuid);
            Player player = Bukkit.getPlayer(uuid);
            if (player != null) {
                deadPlayerNames.put(uuid, player.getName());
            } else {
                OfflinePlayer op = Bukkit.getOfflinePlayer(uuid);
                deadPlayerNames.put(uuid, op.getName() != null ? op.getName() : "Desconocido");
            }
        } else {
            deadPlayers.remove(uuid);
            deadPlayerNames.remove(uuid);
        }
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
        File dataFolder = new File(plugin.getDataFolder(), "data");
        if (!dataFolder.exists()) {
            dataFolder.mkdirs();
        }

        File dataFile = new File(dataFolder, "death_states.yml");
        org.bukkit.configuration.file.YamlConfiguration config = new org.bukkit.configuration.file.YamlConfiguration();

        for (UUID uuid : deadPlayers) {
            String name = deadPlayerNames.get(uuid);
            if (name == null || name.equals("Desconocido")) {
                OfflinePlayer op = Bukkit.getOfflinePlayer(uuid);
                name = (op.getName() != null) ? op.getName() : "Desconocido";
                deadPlayerNames.put(uuid, name);
            }
            config.set("dead-player." + uuid.toString() + ".name", name);
        }

        config.set("total-deaths", totalDeaths);

        for (Map.Entry<UUID, String> entry : discordMessageIds.entrySet()) {
            config.set("discord-messages." + entry.getKey().toString(), entry.getValue());
        }

        try { config.save(dataFile); } catch (Exception ignored) {}
    }

    public static void loadData(PermaPiola plugin) {
        File dataFolder = new File(plugin.getDataFolder(), "data");
        File dataFile = new File(dataFolder, "death_states.yml");
        if (!dataFile.exists()) return;
        org.bukkit.configuration.file.YamlConfiguration config = org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(dataFile);

        deadPlayers.clear();
        deadPlayerNames.clear();
        boolean needsMigration = false;

        if (config.contains("dead-players") && config.isList("dead-players")) {
            for (String uuidStr : config.getStringList("dead-players")) {
                try {
                    UUID uuid = UUID.fromString(uuidStr);
                    deadPlayers.add(uuid);
                    OfflinePlayer op = Bukkit.getOfflinePlayer(uuid);
                    String name = op.getName() != null ? op.getName() : "Desconocido";
                    deadPlayerNames.put(uuid, name);
                } catch (Exception ignored) {}
            }
            needsMigration = true;
        }

        if (config.contains("dead-player")) {
            for (String uuidStr : config.getConfigurationSection("dead-player").getKeys(false)) {
                try {
                    UUID uuid = UUID.fromString(uuidStr);
                    deadPlayers.add(uuid);
                    String name = config.getString("dead-player." + uuidStr + ".name", "Desconocido");

                    if (name.equals("Desconocido")) {
                        OfflinePlayer op = Bukkit.getOfflinePlayer(uuid);
                        if (op.getName() != null) {
                            name = op.getName();
                            needsMigration = true;
                        }
                    }
                    deadPlayerNames.put(uuid, name);
                } catch (Exception ignored) {}
            }
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

        if (needsMigration) {
            saveData(plugin);
        }
    }
}