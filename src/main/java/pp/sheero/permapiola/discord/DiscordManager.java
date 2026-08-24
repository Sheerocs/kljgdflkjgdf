package pp.sheero.permapiola.discord;

import github.scarsz.discordsrv.DiscordSRV;
import github.scarsz.discordsrv.dependencies.jda.api.EmbedBuilder;
import github.scarsz.discordsrv.dependencies.jda.api.entities.TextChannel;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import pp.sheero.permapiola.PermaPiola;
import pp.sheero.permapiola.utils.DeathStateManager;

import java.awt.Color;
import java.util.UUID;

public class DiscordManager {

    private final PermaPiola plugin;
    private final boolean isDiscordSRVLoaded;

    private boolean discordEnabled;
    private String channelName;
    private String embedTitle;
    private Color embedColor;
    private String dayAndDeathFormat;
    private String causeTitle;
    private String locTitle;
    private String locFormat;
    private String footerFormat;
    private String deathMsgTitle;

    public DiscordManager(PermaPiola plugin) {
        this.plugin = plugin;
        this.isDiscordSRVLoaded = Bukkit.getPluginManager().getPlugin("DiscordSRV") != null;
        loadConfigCache();
    }

    public void loadConfigCache() {
        org.bukkit.configuration.file.FileConfiguration config = plugin.getConfig();
        this.discordEnabled = config.getBoolean("discord.enabled", false);
        this.channelName = config.getString("discord.channel-name", "muertes");
        this.embedTitle = config.getString("discord.death-embed.title", "¡%victim% ha muerto!");

        try {
            this.embedColor = Color.decode(config.getString("discord.death-embed.color", "#cc0425"));
        } catch (Exception e) {
            this.embedColor = new Color(204, 4, 37);
        }

        this.dayAndDeathFormat = config.getString("discord.death-embed.day-and-death", "**Día: %day% | Muerte: %death_number%**");
        this.causeTitle = config.getString("discord.death-embed.cause-title", "Causa de Muerte");
        this.locTitle = config.getString("discord.death-embed.location-title", "Ubicación");
        this.locFormat = config.getString("discord.death-embed.location-format", "Coordenadas: %x% %y% %z%\nMundo: %world%");
        this.footerFormat = config.getString("discord.death-embed.footer", "TPS: %tps% | Ping: %ping%ms • %date%");
        this.deathMsgTitle = config.getString("discord.death-embed.death-message-title", "Últimas palabras");
    }

    private String wrapText(String text, int maxLineLength) {
        StringBuilder result = new StringBuilder();
        String[] words = text.split(" ");
        int currentLineLength = 0;

        for (String word : words) {
            if (word.length() > maxLineLength) {
                if (currentLineLength > 0) {
                    result.append("\n");
                    currentLineLength = 0;
                }
                for (int i = 0; i < word.length(); i += maxLineLength) {
                    String chunk = word.substring(i, Math.min(i + maxLineLength, word.length()));
                    result.append(chunk).append("\n");
                }
                continue;
            }

            if (currentLineLength + word.length() + 1 > maxLineLength) {
                result.append("\n");
                currentLineLength = 0;
            } else if (currentLineLength > 0) {
                result.append(" ");
                currentLineLength++;
            }

            result.append(word);
            currentLineLength += word.length();
        }
        return result.toString().trim();
    }

    public void sendDeathEmbed(Player victim, String vanillaCause, int deathNumber, int currentDay) {
        if (!isDiscordSRVLoaded || !discordEnabled) return;

        Location loc = victim.getLocation();
        String victimName = victim.getName();
        UUID victimUUID = victim.getUniqueId();
        String tps = String.format("%.2f", Bukkit.getTPS()[0]);
        int ping = victim.getPing();

        String rawWorldName = victim.getWorld().getName();
        String worldName;

        if (plugin.getConfig().contains("discord.death-embed.worlds." + rawWorldName)) {
            worldName = plugin.getConfig().getString("discord.death-embed.worlds." + rawWorldName);
        } else {
            if (rawWorldName.equals("world_permapiola_fallen_memories")) {
                worldName = "Fallen Memories";
            } else if (rawWorldName.endsWith("_nether")) {
                worldName = "Nether";
            } else if (rawWorldName.endsWith("_the_end")) {
                worldName = "End";
            } else {
                worldName = "Over";
            }
        }

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                TextChannel channel = DiscordSRV.getPlugin().getDestinationTextChannelForGameChannelName(channelName);
                if (channel == null) return;

                EmbedBuilder embed = new EmbedBuilder();
                embed.setTitle(embedTitle.replace("%victim%", victimName));
                embed.setColor(embedColor);
                embed.setThumbnail("https://mc-heads.net/avatar/" + victimUUID + "/100");

                String description = dayAndDeathFormat
                        .replace("%day%", String.valueOf(currentDay))
                        .replace("%death_number%", String.valueOf(deathNumber));
                embed.setDescription(description + "\n\n");

                String cleanCause = vanillaCause.replaceAll("(?i)[§&][0-9a-fk-orx]", "");
                embed.addField(causeTitle, cleanCause, false);

                String formattedLoc = locFormat
                        .replace("%x%", String.valueOf(loc.getBlockX()))
                        .replace("%y%", String.valueOf(loc.getBlockY()))
                        .replace("%z%", String.valueOf(loc.getBlockZ()))
                        .replace("%world%", worldName);
                embed.addField(locTitle, "```\n" + formattedLoc + "\n```", false);

                if (plugin.getDeathMessageManager().hasMessage(victimUUID)) {
                    String customMsg = plugin.getDeathMessageManager().getMessage(victimUUID);

                    String wrappedMsg = wrapText(customMsg, 50);

                    embed.addField(deathMsgTitle, wrappedMsg, false);
                }

                java.time.LocalDateTime now = java.time.LocalDateTime.now(java.time.ZoneId.of("America/Argentina/Buenos_Aires"));
                String dateStr = now.format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"));

                embed.setFooter(footerFormat
                        .replace("%tps%", tps)
                        .replace("%ping%", String.valueOf(ping))
                        .replace("%date%", dateStr));

                channel.sendMessageEmbeds(embed.build()).queue(message -> {
                    DeathStateManager.setDiscordMessageId(victimUUID, message.getId());
                });

            } catch (Exception e) {
                plugin.getLogger().severe("Error al enviar el embed a Discord: " + e.getMessage());
            }
        });
    }

    public void deleteDeathMessage(UUID victimUUID) {
        if (!isDiscordSRVLoaded || !discordEnabled) return;

        String messageId = DeathStateManager.getDiscordMessageId(victimUUID);
        if (messageId == null) return;

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                TextChannel channel = DiscordSRV.getPlugin().getDestinationTextChannelForGameChannelName(channelName);
                if (channel != null) {
                    channel.deleteMessageById(messageId).queue(success -> {
                        DeathStateManager.removeDiscordMessageId(victimUUID);
                    }, error -> {
                        DeathStateManager.removeDiscordMessageId(victimUUID);
                    });
                }
            } catch (Exception ignored) {}
        });
    }
}