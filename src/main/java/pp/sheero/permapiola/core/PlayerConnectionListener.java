package pp.sheero.permapiola.core;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scoreboard.Team;
import pp.sheero.permapiola.PermaPiola;
import pp.sheero.permapiola.teams.TeamManager;
import pp.sheero.permapiola.utils.ColorUtils;
import pp.sheero.permapiola.utils.LuckPermsUtils;

public class PlayerConnectionListener implements Listener {

    private final PermaPiola plugin;
    private final LanguageManager lang;

    public PlayerConnectionListener(PermaPiola plugin, LanguageManager lang) {
        this.plugin = plugin;
        this.lang = lang;
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();

        if (!player.hasPlayedBefore() && plugin.getConfig().getBoolean("spawn.first-join.enabled")) {
            String worldName = plugin.getConfig().getString("spawn.first-join.world", "world");
            org.bukkit.World world = org.bukkit.Bukkit.getWorld(worldName);

            if (world != null) {
                double x = plugin.getConfig().getDouble("spawn.first-join.x");
                double y = plugin.getConfig().getDouble("spawn.first-join.y");
                double z = plugin.getConfig().getDouble("spawn.first-join.z");
                float yaw = (float) plugin.getConfig().getDouble("spawn.first-join.yaw", 0.0);
                float pitch = (float) plugin.getConfig().getDouble("spawn.first-join.pitch", 0.0);

                org.bukkit.Location firstSpawn = new org.bukkit.Location(world, x, y, z, yaw, pitch);

                org.bukkit.Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    player.teleport(firstSpawn);
                }, 1L);
            }
        }

        event.setJoinMessage(null);

        String messagePath;
        if (player.hasPermission("permapiola.admin") || player.hasPermission("permapiola.donor")) {
            messagePath = "join.donator";
        } else {
            messagePath = "join.default";
        }

        String prefix = LuckPermsUtils.getPrefix(player);
        String suffix = LuckPermsUtils.getSuffix(player);

        Team team = TeamManager.getTeam(player);
        if (team != null) {
            prefix = LuckPermsUtils.cleanTeamTag(prefix, team);
            suffix = LuckPermsUtils.cleanTeamTag(suffix, team);
        }

        if (prefix == null) prefix = "";
        if (suffix == null) suffix = "";

        String consoleRaw = lang.getMsg(Bukkit.getConsoleSender(), messagePath);
        if (consoleRaw != null && !consoleRaw.isEmpty()) {
            String consoleMsg = consoleRaw.replace("%player_prefix%", prefix)
                    .replace("%player_suffix%", suffix)
                    .replace("%player%", player.getName());
            Bukkit.getConsoleSender().sendMessage(ColorUtils.format(consoleMsg));
        }

        for (Player onlinePlayer : Bukkit.getOnlinePlayers()) {
            String rawMessage = lang.getMsg(onlinePlayer, messagePath);

            if (rawMessage != null && !rawMessage.isEmpty()) {
                String finalMessage = rawMessage.replace("%player_prefix%", prefix)
                        .replace("%player_suffix%", suffix)
                        .replace("%player%", player.getName());

                onlinePlayer.sendMessage(ColorUtils.format(finalMessage));
            }
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();

        event.setQuitMessage(null);

        String messagePath;
        if (player.hasPermission("permapiola.admin") || player.hasPermission("permapiola.donor")) {
            messagePath = "quit.donator";
        } else {
            messagePath = "quit.default";
        }

        String prefix = LuckPermsUtils.getPrefix(player);
        String suffix = LuckPermsUtils.getSuffix(player);

        Team team = TeamManager.getTeam(player);
        if (team != null) {
            prefix = LuckPermsUtils.cleanTeamTag(prefix, team);
            suffix = LuckPermsUtils.cleanTeamTag(suffix, team);
        }

        if (prefix == null) prefix = "";
        if (suffix == null) suffix = "";

        String consoleRaw = lang.getMsg(Bukkit.getConsoleSender(), messagePath);
        if (consoleRaw != null && !consoleRaw.isEmpty()) {
            String consoleMsg = consoleRaw.replace("%player_prefix%", prefix)
                    .replace("%player_suffix%", suffix)
                    .replace("%player%", player.getName());
            Bukkit.getConsoleSender().sendMessage(ColorUtils.format(consoleMsg));
        }

        for (Player onlinePlayer : Bukkit.getOnlinePlayers()) {
            String rawMessage = lang.getMsg(onlinePlayer, messagePath);

            if (rawMessage != null && !rawMessage.isEmpty()) {
                String finalMessage = rawMessage.replace("%player_prefix%", prefix)
                        .replace("%player_suffix%", suffix)
                        .replace("%player%", player.getName());

                onlinePlayer.sendMessage(ColorUtils.format(finalMessage));
            }
        }
    }
}