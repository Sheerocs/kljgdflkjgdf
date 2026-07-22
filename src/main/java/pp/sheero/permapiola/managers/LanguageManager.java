package pp.sheero.permapiola.managers;

import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import pp.sheero.permapiola.PermaPiola;

import java.io.File;

public class LanguageManager {

    private final PermaPiola plugin;
    private FileConfiguration esConfig;
    private FileConfiguration enConfig;

    public LanguageManager(PermaPiola plugin) {
        this.plugin = plugin;
        loadLocales();
    }

    public void loadLocales() {
        File localesFolder = new File(plugin.getDataFolder(), "locales");
        if (!localesFolder.exists()) {
            localesFolder.mkdirs();
        }

        File esFile = new File(localesFolder, "es_ES.yml");
        File enFile = new File(localesFolder, "en_US.yml");

        if (!esFile.exists()) plugin.saveResource("locales/es_ES.yml", false);
        if (!enFile.exists()) plugin.saveResource("locales/en_US.yml", false);

        esConfig = YamlConfiguration.loadConfiguration(esFile);
        enConfig = YamlConfiguration.loadConfiguration(enFile);
    }

    public String getMsg(Player player, String path) {
        String locale = player.getLocale().toLowerCase();
        FileConfiguration targetConfig;

        if (locale.startsWith("es")) {
            targetConfig = esConfig;
        } else if (locale.startsWith("en")) {
            targetConfig = enConfig;
        } else {
            String defaultLang = plugin.getConfig().getString("settings.default-language", "en_US");
            targetConfig = defaultLang.startsWith("es") ? esConfig : enConfig;
        }

        return targetConfig.getString(path, "Error: Mensaje faltante en " + path);
    }

    public String getMsg(CommandSender sender, String path) {
        if (sender instanceof Player) {
            return getMsg((Player) sender, path);
        }

        String defaultLang = plugin.getConfig().getString("settings.default-language", "es_ES");
        FileConfiguration targetConfig = defaultLang.startsWith("es") ? esConfig : enConfig;
        return targetConfig.getString(path, "Error: Mensaje faltante en " + path);
    }

    public java.util.List<String> getMsgList(CommandSender sender, String path) {
        FileConfiguration targetConfig;

        if (sender instanceof Player) {
            Player player = (Player) sender;
            String locale = player.getLocale().toLowerCase();
            if (locale.startsWith("es")) {
                targetConfig = esConfig;
            } else if (locale.startsWith("en")) {
                targetConfig = enConfig;
            } else {
                String defaultLang = plugin.getConfig().getString("settings.default-language", "en_US");
                targetConfig = defaultLang.startsWith("es") ? esConfig : enConfig;
            }
        } else {
            String defaultLang = plugin.getConfig().getString("settings.default-language", "es_ES");
            targetConfig = defaultLang.startsWith("es") ? esConfig : enConfig;
        }

        if (targetConfig.isList(path)) {
            return targetConfig.getStringList(path);
        }

        String singleMsg = targetConfig.getString(path);
        if (singleMsg != null) {
            return java.util.Collections.singletonList(singleMsg);
        }

        return java.util.Collections.singletonList("&cLista de mensajes no encontrada: " + path);
    }
}