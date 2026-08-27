package pp.sheero.permapiola.chat;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import pp.sheero.permapiola.PermaPiola;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

public class EmoteManager {

    private final PermaPiola plugin;
    private FileConfiguration emotesConfig;
    private final Map<String, String[]> emotesMap = new HashMap<>();

    public EmoteManager(PermaPiola plugin) {
        this.plugin = plugin;
        loadEmotes();
    }

    public void loadEmotes() {
        File file = new File(plugin.getDataFolder(), "emotes.yml");
        if (!file.exists()) {
            plugin.saveResource("emotes.yml", false);
        }
        emotesConfig = YamlConfiguration.loadConfiguration(file);
        emotesMap.clear();

        ConfigurationSection section = emotesConfig.getConfigurationSection("emotes");
        if (section != null) {
            for (String key : section.getKeys(false)) {
                String trigger = section.getString(key + ".trigger");
                String emote = section.getString(key + ".emote");
                if (trigger != null && emote != null) {
                    emotesMap.put(key, new String[]{trigger, emote});
                }
            }
        }
    }

    public String translateEmotes(Player player, String message) {
        if (!player.hasPermission("permapiola.donor.emotes")) {
            return message;
        }

        String translated = message;
        for (String[] data : emotesMap.values()) {
            translated = translated.replace(data[0], data[1]);
        }
        return translated;
    }

    public Map<String, String[]> getEmotesMap() {
        return emotesMap;
    }
}