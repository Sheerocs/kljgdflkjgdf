package pp.sheero.permapiola.teams;

import me.neznamy.tab.api.TabAPI;
import me.neznamy.tab.api.TabPlayer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import pp.sheero.permapiola.PermaPiola;
import pp.sheero.permapiola.chat.ChatListener;

public class TabManager {

    public static void updatePlayerTab(Player player, PermaPiola plugin) {
        if (!Bukkit.getPluginManager().isPluginEnabled("TAB")) return;

        TabAPI tabAPI = TabAPI.getInstance();
        TabPlayer tabPlayer = tabAPI.getPlayer(player.getUniqueId());

        if (tabPlayer == null) return;

        String tag = ChatListener.getPlayerTag(player);
        String nameColorRaw = ChatListener.getPlayerNameColor(player, plugin);

        // ADAPTADOR HEX: Aseguramos que TAB lea los Hex puros de la config
        if (nameColorRaw.startsWith("#") && nameColorRaw.length() == 7) {
            nameColorRaw = "&" + nameColorRaw;
        }

        // 1. PREFIX PARA EL TABLIST (Mantiene el formato cursiva/negrita original)
        String tablistPrefix = tag + nameColorRaw;

        // 2. PREFIX PARA EL NAMETAG (Filtramos cursiva/negrita para no romper el Scoreboard Vanilla)
        String cleanNameColor = nameColorRaw.replaceAll("(?i)&[k-o]", "");
        String nametagPrefix = tag + cleanNameColor;

        if (tabAPI.getTabListFormatManager() != null) {
            tabAPI.getTabListFormatManager().setPrefix(tabPlayer, tablistPrefix);
        }

        if (tabAPI.getNameTagManager() != null) {
            tabAPI.getNameTagManager().setPrefix(tabPlayer, nametagPrefix);
        }
    }

    public static void updateAllTabs(PermaPiola plugin) {
        if (!Bukkit.getPluginManager().isPluginEnabled("TAB")) return;
        for (Player player : Bukkit.getOnlinePlayers()) {
            updatePlayerTab(player, plugin);
        }
    }

    private static String getRankWeight(Player player, PermaPiola plugin) {
        try {
            net.luckperms.api.LuckPerms api = net.luckperms.api.LuckPermsProvider.get();
            net.luckperms.api.model.user.User user = api.getUserManager().getUser(player.getUniqueId());
            if (user != null) {
                String group = user.getPrimaryGroup().toLowerCase();
                return plugin.getConfig().getString("rank-weights." + group, "z").toLowerCase();
            }
        } catch (Exception ignored) {}
        return "z";
    }

    public static void registerTabPlaceholder(PermaPiola plugin) {
        if (!Bukkit.getPluginManager().isPluginEnabled("TAB")) return;

        try {
            TabAPI.getInstance().getPlaceholderManager().registerPlayerPlaceholder("%permapiola_sort%", 1000, tabPlayer -> {
                Player player = Bukkit.getPlayer(tabPlayer.getUniqueId());
                if (player == null) return "4_z";

                String rankWeight = getRankWeight(player, plugin);

                if (ReTeamManager.hasReTeam(player)) {
                    return "2_" + ReTeamManager.getReTeam(player).getName().toLowerCase() + "_" + rankWeight;
                } else if (TeamManager.hasTeam(player)) {
                    return "3_" + TeamManager.getTeam(player).getName().toLowerCase() + "_" + rankWeight;
                }

                if (rankWeight.equals("z")) {
                    return "4_z";
                } else {
                    return "1_" + rankWeight;
                }
            });
        } catch (Exception ignored) {
            plugin.getLogger().warning("No se pudo registrar el Placeholder de ordenamiento en TAB.");
        }
    }
}