package pp.sheero.permapiola.hurricane;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.weather.WeatherChangeEvent;
import org.bukkit.event.world.TimeSkipEvent;
import pp.sheero.permapiola.PermaPiola;
import pp.sheero.permapiola.managers.LanguageManager;
import pp.sheero.permapiola.utils.ColorUtils;
import pp.sheero.permapiola.utils.TimeUtils;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class HurricaneManager implements Listener {

    private final PermaPiola plugin;
    private final LanguageManager lang;
    private BossBar bossBar;
    private boolean active = false;
    private long timeRemaining = 0;
    private long totalDuration = 0;
    private int taskId = -1;

    private final File dataFile;
    private long durationSecondsCache;

    public HurricaneManager(PermaPiola plugin, LanguageManager lang) {
        this.plugin = plugin;
        this.lang = lang;

        File dataFolder = new File(plugin.getDataFolder(), "data");
        if (!dataFolder.exists()) {
            dataFolder.mkdirs();
        }
        this.dataFile = new File(dataFolder, "hurricane_data.yml");

        loadConfigCache();
        loadData();

        if (active && timeRemaining > 0) {
            startTimer();
        } else {
            active = false;
        }
    }

    public void loadConfigCache() {
        String durationStr = plugin.getConfig().getString("hurricane.duration", "1h");
        this.durationSecondsCache = TimeUtils.parseTimeString(durationStr);
        if(this.durationSecondsCache <= 0) this.durationSecondsCache = 3600; // Por seguridad
    }

    public long getDurationSecondsCache() {
        return durationSecondsCache;
    }

    public void setDefaultDuration(String rawTime, long totalSeconds) {
        this.durationSecondsCache = totalSeconds;
        plugin.getConfig().set("hurricane.duration", rawTime);
        plugin.saveConfig();
    }

    public String getFormattedDuration(long totalSeconds, org.bukkit.command.CommandSender sender) {
        long h = totalSeconds / 3600;
        long m = (totalSeconds % 3600) / 60;
        long s = totalSeconds % 60;

        List<String> parts = new ArrayList<>();

        if (h > 0) {
            String word = h == 1 ? lang.getMsg(sender, "hurricane.time-words.hour") : lang.getMsg(sender, "hurricane.time-words.hours");
            parts.add(h + " " + word);
        }
        if (m > 0) {
            String word = m == 1 ? lang.getMsg(sender, "hurricane.time-words.minute") : lang.getMsg(sender, "hurricane.time-words.minutes");
            parts.add(m + " " + word);
        }
        if (s > 0 || (h == 0 && m == 0)) {
            String word = s == 1 ? lang.getMsg(sender, "hurricane.time-words.second") : lang.getMsg(sender, "hurricane.time-words.seconds");
            parts.add(s + " " + word);
        }

        if (parts.size() == 1) return parts.get(0);
        if (parts.size() == 2) return parts.get(0) + " " + lang.getMsg(sender, "hurricane.time-words.and") + " " + parts.get(1);

        return parts.get(0) + ", " + parts.get(1) + " " + lang.getMsg(sender, "hurricane.time-words.and") + " " + parts.get(2);
    }

    public boolean isActive() {
        return active;
    }

    public long getTimeRemaining() {
        return timeRemaining;
    }

    public void addHurricaneTime() {
        long durationSeconds = this.durationSecondsCache;

        if (!active) {
            active = true;
            timeRemaining = durationSeconds;
            totalDuration = durationSeconds;
            createBossBar();
            startTimer();
            forceStorm();
        } else {
            timeRemaining += durationSeconds;
            totalDuration += durationSeconds;
        }
    }

    private void createBossBar() {
        if (bossBar == null) {
            String title = lang.getMsg(Bukkit.getConsoleSender(), "hurricane.bossbar.title").replace("%time%", formatTime(timeRemaining));
            bossBar = Bukkit.createBossBar(ColorUtils.format(title), BarColor.BLUE, BarStyle.SOLID);
        }
        for (Player p : Bukkit.getOnlinePlayers()) {
            bossBar.addPlayer(p);
        }
    }

    private void startTimer() {
        if (bossBar == null) createBossBar();
        if (taskId != -1) Bukkit.getScheduler().cancelTask(taskId);

        taskId = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (timeRemaining <= 0) {
                stopHurricane();
                return;
            }
            timeRemaining--;

            String title = lang.getMsg(Bukkit.getConsoleSender(), "hurricane.bossbar.title").replace("%time%", formatTime(timeRemaining));
            bossBar.setTitle(ColorUtils.format(title));

            double progress = (double) timeRemaining / totalDuration;
            bossBar.setProgress(Math.max(0.0, Math.min(1.0, progress)));

            if (timeRemaining % 100 == 0) forceStorm();

        }, 20L, 20L).getTaskId();
    }

    public void stopHurricane() {
        if (active) {
            String msg = ColorUtils.format(lang.getMsg(Bukkit.getConsoleSender(), "hurricane.death-event.hurricane-end"));
            Bukkit.getConsoleSender().sendMessage(msg);
            for (Player p : Bukkit.getOnlinePlayers()) {
                p.sendMessage(msg);
            }
        }

        active = false;
        timeRemaining = 0;
        if (taskId != -1) Bukkit.getScheduler().cancelTask(taskId);
        taskId = -1;

        if (bossBar != null) {
            bossBar.removeAll();
            bossBar = null;
        }

        for (org.bukkit.World w : Bukkit.getWorlds()) {
            w.setStorm(false);
            w.setThundering(false);
        }

        plugin.getDementialWheelManager().stopWheel();
    }

    private void forceStorm() {
        for (World w : Bukkit.getWorlds()) {
            if (!w.hasStorm()) w.setStorm(true);
            if (!w.isThundering()) w.setThundering(true);
            w.setWeatherDuration(12000);
            w.setThunderDuration(12000);
        }
    }

    private String formatTime(long seconds) {
        long h = seconds / 3600;
        long m = (seconds % 3600) / 60;
        long s = seconds % 60;

        StringBuilder sb = new StringBuilder();
        if (h > 0) {
            sb.append(h < 10 ? "0" + h : h).append(":");
        }
        sb.append(m < 10 ? "0" + m : m).append(":");
        sb.append(s < 10 ? "0" + s : s);

        return sb.toString();
    }

    public void setTime(long seconds) {
        if (seconds <= 0) {
            stopHurricane();
            return;
        }
        this.timeRemaining = seconds;
        this.totalDuration = seconds;
        if (!active) {
            active = true;
            createBossBar();
            startTimer();
            forceStorm();
        }
    }

    public void addTime(long seconds) {
        if (!active) {
            setTime(seconds);
        } else {
            this.timeRemaining += seconds;
            this.totalDuration += seconds;
        }
    }

    public void removeTime(long seconds) {
        if (!active) return;

        this.timeRemaining -= seconds;
        this.totalDuration -= seconds;

        if (this.totalDuration <= 0) {
            this.totalDuration = 1;
        }

        if (this.timeRemaining <= 0) {
            stopHurricane();
        }
    }

    @EventHandler
    public void onWeatherChange(WeatherChangeEvent event) {
        if (active && !event.toWeatherState()) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onTimeSkip(TimeSkipEvent event) {
        if (active && event.getSkipReason() == TimeSkipEvent.SkipReason.NIGHT_SKIP) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        if (active && bossBar != null) {
            bossBar.addPlayer(event.getPlayer());
        }
    }

    public void saveData() {
        YamlConfiguration config = new YamlConfiguration();
        config.set("active", active);
        config.set("timeRemaining", timeRemaining);
        config.set("totalDuration", totalDuration);
        try { config.save(dataFile); } catch (Exception ignored) {}
    }

    private void loadData() {
        if (!dataFile.exists()) return;
        YamlConfiguration config = YamlConfiguration.loadConfiguration(dataFile);
        active = config.getBoolean("active", false);
        timeRemaining = config.getLong("timeRemaining", 0);
        totalDuration = config.getLong("totalDuration", timeRemaining);
    }
}