package pp.sheero.permapiola.managers;

import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import pp.sheero.permapiola.PermaPiola;
import pp.sheero.permapiola.utils.ChatChannel;
import pp.sheero.permapiola.utils.ColorUtils;
import pp.sheero.permapiola.utils.LuckPermsUtils;

import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class ChatManager {

    private final PermaPiola plugin;
    private final Map<UUID, ChatChannel> playerChannels = new ConcurrentHashMap<>();
    private final File dataFile;

    private String staffFormat;
    private String overrideSymbol;
    private boolean overrideEnabled;
    private String specFormatDonator;
    private String specFormatDefault;
    private String globalFormatDonator;
    private String globalFormatDefault;

    public ChatManager(PermaPiola plugin) {
        this.plugin = plugin;
        File dataFolder = new File(plugin.getDataFolder(), "data");
        if (!dataFolder.exists()) {
            dataFolder.mkdirs();
        }

        this.dataFile = new File(dataFolder, "chat_data.yml");
        loadData();
        loadConfigCache();
    }

    public void loadConfigCache() {
        FileConfiguration config = plugin.getConfig();
        this.staffFormat = config.getString("chat.staff-format", "&4[Staff] %player_prefix%%player%&f: %message%");
        this.overrideSymbol = config.getString("chat.override.symbol", "!");
        this.overrideEnabled = config.getBoolean("chat.override.enabled", true);
        this.specFormatDonator = config.getString("chat.spec-format.donator", "&8[&7Spec&8] %player_prefix%%player%&7: &f%message%");
        this.specFormatDefault = config.getString("chat.spec-format.default", "&8[&7Spec&8] %player_prefix%%player%&7: %message%");
        this.globalFormatDonator = config.getString("chat.global-format.donator", "%player_prefix%%player%%player_suffix%&f: %message%");
        this.globalFormatDefault = config.getString("chat.global-format.default", "%player_prefix%%player%%player_suffix%&7: %message%");
    }

    public String getOverrideSymbol() { return overrideSymbol; }
    public boolean isOverrideEnabled() { return overrideEnabled; }
    public String getSpecFormatDonator() { return specFormatDonator; }
    public String getSpecFormatDefault() { return specFormatDefault; }
    public String getGlobalFormatDonator() { return globalFormatDonator; }
    public String getGlobalFormatDefault() { return globalFormatDefault; }

    public ChatChannel getChannel(Player player) {
        return playerChannels.getOrDefault(player.getUniqueId(), ChatChannel.ALL);
    }

    public void setChannel(Player player, ChatChannel channel) {
        if (channel == ChatChannel.ALL) {
            playerChannels.remove(player.getUniqueId());
        } else {
            playerChannels.put(player.getUniqueId(), channel);
        }
    }

    public void sendStaffMessage(Player sender, String message) {
        String prefix = LuckPermsUtils.getPrefix(sender);
        String suffix = LuckPermsUtils.getSuffix(sender);

        if (prefix == null) prefix = "";
        if (suffix == null) suffix = "";

        String finalMessage = this.staffFormat
                .replace("%player_prefix%", prefix)
                .replace("%player_suffix%", suffix)
                .replace("%player%", sender.getName())
                .replace("%message%", message);

        String coloredMessage = ColorUtils.format(finalMessage);
        Bukkit.getConsoleSender().sendMessage(coloredMessage);

        for (Player staff : Bukkit.getOnlinePlayers()) {
            if (staff.hasPermission("permapiola.admin.staffchat")) {
                staff.sendMessage(coloredMessage);
            }
        }
    }

    public void saveData() {
        Map<UUID, ChatChannel> snapshot = new HashMap<>(playerChannels);
        YamlConfiguration config = new YamlConfiguration();
        for (Map.Entry<UUID, ChatChannel> entry : snapshot.entrySet()) {
            config.set("channels." + entry.getKey().toString(), entry.getValue().name());
        }
        try { config.save(dataFile); } catch (Exception ignored) {}
    }

    public void loadData() {
        if (!dataFile.exists()) return;
        YamlConfiguration config = YamlConfiguration.loadConfiguration(dataFile);
        if (config.contains("channels")) {
            for (String uuidStr : config.getConfigurationSection("channels").getKeys(false)) {
                try {
                    UUID uuid = UUID.fromString(uuidStr);
                    ChatChannel channel = ChatChannel.valueOf(config.getString("channels." + uuidStr));
                    playerChannels.put(uuid, channel);
                } catch (Exception ignored) {}
            }
        }
    }
}