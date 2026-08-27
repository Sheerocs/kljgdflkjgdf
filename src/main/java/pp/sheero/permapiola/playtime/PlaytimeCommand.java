package pp.sheero.permapiola.playtime;

import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.model.group.Group;
import net.luckperms.api.model.user.User;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.Team;
import pp.sheero.permapiola.PermaPiola;
import pp.sheero.permapiola.core.LanguageManager;
import pp.sheero.permapiola.teams.TeamManager;
import pp.sheero.permapiola.utils.ColorUtils;
import pp.sheero.permapiola.utils.TimeUtils;

import java.util.*;
import java.util.stream.Collectors;

public class PlaytimeCommand implements CommandExecutor, TabCompleter {

    private final PermaPiola plugin;
    private final PlaytimeManager playtimeManager;
    private final LanguageManager languageManager;

    private static final List<String> MAIN_ARGS = Arrays.asList("show", "top", "topteams", "admin");
    private static final List<String> SHOW_ARGS = Arrays.asList("player", "team");
    private static final List<String> ADMIN_ARGS = Arrays.asList("add", "remove", "set", "reset");

    public PlaytimeCommand(PermaPiola plugin, PlaytimeManager playtimeManager, LanguageManager languageManager) {
        this.plugin = plugin;
        this.playtimeManager = playtimeManager;
        this.languageManager = languageManager;
    }

    private String getFormattedPlaytime(CommandSender sender, long seconds) {
        String w = languageManager.getMsg(sender, "playtime.units.week");
        String d = languageManager.getMsg(sender, "playtime.units.day");
        String h = languageManager.getMsg(sender, "playtime.units.hour");
        String m = languageManager.getMsg(sender, "playtime.units.minute");
        String s = languageManager.getMsg(sender, "playtime.units.second");

        return TimeUtils.formatTime(seconds, w, d, h, m, s);
    }

    private void sendFramedMessage(CommandSender sender, String content) {
        if (content == null || content.isEmpty()) return;

        content = content.replaceAll("[\r\n]+$", "");

        String line = languageManager.getMsg(sender, "commands.generic.line");
        sender.sendMessage(ColorUtils.format(line));

        int start = 0;
        int end;
        while ((end = content.indexOf('\n', start)) != -1) {
            sender.sendMessage(ColorUtils.format(content.substring(start, end)));
            start = end + 1;
        }
        sender.sendMessage(ColorUtils.format(content.substring(start)));
        sender.sendMessage(ColorUtils.format(line));
    }

    private int getPlayerPosition(UUID targetUUID) {
        Map<UUID, Long> allTimes = playtimeManager.getAllPlaytimes();
        if (allTimes.getOrDefault(targetUUID, 0L) <= 0) return -1;

        List<Map.Entry<UUID, Long>> sortedList = new ArrayList<>(allTimes.entrySet());
        sortedList.sort((a, b) -> b.getValue().compareTo(a.getValue()));

        int position = 1;
        for (Map.Entry<UUID, Long> entry : sortedList) {
            if (entry.getValue() <= 0) continue;
            if (entry.getKey().equals(targetUUID)) {
                return position;
            }
            position++;
        }
        return -1;
    }

    private int getTeamPosition(String teamDisplayName) {
        Map<String, Long> teamTimes = new HashMap<>();
        Map<UUID, Long> allTimes = playtimeManager.getAllPlaytimes();

        for (Map.Entry<UUID, Long> entry : allTimes.entrySet()) {
            String memberName = playtimeManager.getName(entry.getKey());
            if (memberName != null && !memberName.equals("Desconocido")) {
                Team t = TeamManager.getMainScoreboard().getEntryTeam(memberName);
                if (t != null) {
                    teamTimes.put(t.getDisplayName(), teamTimes.getOrDefault(t.getDisplayName(), 0L) + entry.getValue());
                }
            }
        }

        if (teamTimes.getOrDefault(teamDisplayName, 0L) <= 0) return -1;

        List<Map.Entry<String, Long>> sortedTeams = new ArrayList<>(teamTimes.entrySet());
        sortedTeams.sort((a, b) -> b.getValue().compareTo(a.getValue()));

        int position = 1;
        for (Map.Entry<String, Long> entry : sortedTeams) {
            if (entry.getValue() <= 0) continue;
            if (entry.getKey().equals(teamDisplayName)) {
                return position;
            }
            position++;
        }
        return -1;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {

        if (args.length == 0) {
            if (!(sender instanceof Player)) return true;
            Player player = (Player) sender;

            long time = playtimeManager.getPlaytime(player.getUniqueId());
            int posInt = getPlayerPosition(player.getUniqueId());
            String posStr = posInt > 0 ? String.valueOf(posInt) : "N/A";

            String msg = languageManager.getMsg(sender, "playtime.self")
                    .replace("%player%", player.getName())
                    .replace("%time%", getFormattedPlaytime(sender, time))
                    .replace("%pos%", posStr);
            sendFramedMessage(sender, msg);
            return true;
        }

        String subCommand = args[0].toLowerCase();

        switch (subCommand) {
            case "show": {
                if (args.length < 3) {
                    sendFramedMessage(sender, languageManager.getMsg(sender, "playtime.usage-show"));
                    return true;
                }

                String type = args[1].toLowerCase();
                String targetName = args[2];

                if (type.equals("player")) {
                    UUID targetUUID = playtimeManager.getUUIDByName(targetName);
                    long time = 0;
                    String finalTargetName = targetName;
                    boolean isSelf = false;

                    if (targetUUID != null) {
                        time = playtimeManager.getPlaytime(targetUUID);
                        finalTargetName = playtimeManager.getName(targetUUID);
                        if (sender instanceof Player) {
                            isSelf = ((Player) sender).getUniqueId().equals(targetUUID);
                        }
                    } else {
                        @SuppressWarnings("deprecation")
                        OfflinePlayer target = Bukkit.getOfflinePlayer(targetName);
                        targetUUID = target.getUniqueId();
                        time = playtimeManager.getPlaytime(targetUUID);
                        finalTargetName = target.getName() != null ? target.getName() : targetName;
                        if (sender instanceof Player) {
                            isSelf = ((Player) sender).getUniqueId().equals(targetUUID);
                        }
                    }

                    int posInt = getPlayerPosition(targetUUID);
                    String posStr = posInt > 0 ? String.valueOf(posInt) : "N/A";
                    String path = isSelf ? "playtime.self" : "playtime.other-player";

                    String msg = languageManager.getMsg(sender, path)
                            .replace("%player%", finalTargetName)
                            .replace("%time%", getFormattedPlaytime(sender, time))
                            .replace("%pos%", posStr);
                    sendFramedMessage(sender, msg);
                }
                else if (type.equals("team")) {
                    Team team = TeamManager.getTeamByName(targetName);
                    if (team == null) {
                        sendFramedMessage(sender, languageManager.getMsg(sender, "playtime.team-not-found"));
                        return true;
                    }

                    long totalTime = 0;
                    Map<String, Long> memberTimes = new HashMap<>();

                    for (String member : team.getEntries()) {
                        UUID memberUUID = playtimeManager.getUUIDByName(member);
                        long mTime = 0;
                        if (memberUUID != null) {
                            mTime = playtimeManager.getPlaytime(memberUUID);
                        } else {
                            @SuppressWarnings("deprecation")
                            OfflinePlayer memberOffline = Bukkit.getOfflinePlayer(member);
                            mTime = playtimeManager.getPlaytime(memberOffline.getUniqueId());
                        }
                        totalTime += mTime;
                        memberTimes.put(member, mTime);
                    }

                    int posInt = getTeamPosition(team.getDisplayName());
                    String posStr = posInt > 0 ? String.valueOf(posInt) : "N/A";

                    List<Map.Entry<String, Long>> sortedMembers = new ArrayList<>(memberTimes.entrySet());
                    sortedMembers.sort((a, b) -> b.getValue().compareTo(a.getValue()));

                    StringBuilder sb = new StringBuilder();
                    String headerMsg = languageManager.getMsg(sender, "playtime.other-team")
                            .replace("%team%", team.getDisplayName())
                            .replace("%time%", getFormattedPlaytime(sender, totalTime))
                            .replace("%pos%", posStr);
                    sb.append(headerMsg).append("\n");

                    for (Map.Entry<String, Long> entry : sortedMembers) {
                        String memberLine = languageManager.getMsg(sender, "playtime.team-member-format")
                                .replace("%player%", getPlayerNameWithPrefix(entry.getKey()))
                                .replace("%time%", getFormattedPlaytime(sender, entry.getValue()));
                        sb.append(memberLine).append("\n");
                    }

                    sendFramedMessage(sender, sb.toString());
                }
                break;
            }

            case "top": {
                Map<UUID, Long> allTimes = playtimeManager.getAllPlaytimes();
                List<Map.Entry<UUID, Long>> sortedList = new ArrayList<>(allTimes.entrySet());
                sortedList.sort((a, b) -> b.getValue().compareTo(a.getValue()));

                StringBuilder sb = new StringBuilder();
                sb.append(languageManager.getMsg(sender, "playtime.top-header")).append("\n");

                int position = 1;
                for (Map.Entry<UUID, Long> entry : sortedList) {
                    if (position > 10) break;
                    if (entry.getValue() <= 0) continue;

                    String name = playtimeManager.getName(entry.getKey());

                    if (name.equals("Desconocido")) {
                        OfflinePlayer op = Bukkit.getOfflinePlayer(entry.getKey());
                        if (op.getName() != null) {
                            name = op.getName();
                            playtimeManager.updateNameCache(entry.getKey(), name);
                        }
                    }

                    String line = languageManager.getMsg(sender, "playtime.top-format")
                            .replace("%pos%", String.valueOf(position))
                            .replace("%name%", getPlayerNameWithPrefix(name))
                            .replace("%time%", getFormattedPlaytime(sender, entry.getValue()));
                    sb.append(line).append("\n");
                    position++;
                }
                sendFramedMessage(sender, sb.toString());
                break;
            }

            case "topteams": {
                Map<String, Long> teamTimes = new HashMap<>();
                Map<UUID, Long> allTimes = playtimeManager.getAllPlaytimes();

                for (Map.Entry<UUID, Long> entry : allTimes.entrySet()) {
                    String memberName = playtimeManager.getName(entry.getKey());

                    if (memberName.equals("Desconocido")) {
                        OfflinePlayer op = Bukkit.getOfflinePlayer(entry.getKey());
                        if (op.getName() != null) {
                            memberName = op.getName();
                            playtimeManager.updateNameCache(entry.getKey(), memberName);
                        }
                    }

                    if (memberName != null && !memberName.equals("Desconocido")) {
                        Team team = TeamManager.getMainScoreboard().getEntryTeam(memberName);

                        if (team != null) {
                            long currentTeamTotal = teamTimes.getOrDefault(team.getDisplayName(), 0L);
                            teamTimes.put(team.getDisplayName(), currentTeamTotal + entry.getValue());
                        }
                    }
                }

                List<Map.Entry<String, Long>> sortedTeams = new ArrayList<>(teamTimes.entrySet());
                sortedTeams.sort((a, b) -> b.getValue().compareTo(a.getValue()));

                StringBuilder sb = new StringBuilder();
                sb.append(languageManager.getMsg(sender, "playtime.topteams-header")).append("\n");

                int position = 1;
                for (Map.Entry<String, Long> entry : sortedTeams) {
                    if (position > 10) break;
                    String line = languageManager.getMsg(sender, "playtime.topteams-format")
                            .replace("%pos%", String.valueOf(position))
                            .replace("%team%", entry.getKey())
                            .replace("%time%", getFormattedPlaytime(sender, entry.getValue()));
                    sb.append(line).append("\n");
                    position++;
                }
                sendFramedMessage(sender, sb.toString());
                break;
            }

            case "admin": {
                if (!sender.hasPermission("permapiola.admin.playtime")) {
                    sender.sendMessage(ColorUtils.format(languageManager.getMsg(sender, "commands.generic.no-permission")));
                    return true;
                }
                if (args.length < 3) {
                    sendFramedMessage(sender, languageManager.getMsg(sender, "playtime.usage-admin"));
                    return true;
                }

                String action = args[1].toLowerCase();
                String targetName = args[2];

                UUID targetUUID = playtimeManager.getUUIDByName(targetName);
                String finalTargetName = targetName;

                if (targetUUID != null) {
                    finalTargetName = playtimeManager.getName(targetUUID);
                } else {
                    @SuppressWarnings("deprecation")
                    OfflinePlayer op = Bukkit.getOfflinePlayer(targetName);
                    targetUUID = op.getUniqueId();
                    finalTargetName = op.getName() != null ? op.getName() : targetName;
                }

                long amount = 0;
                String formattedAmount = "0s";

                switch (action) {
                    case "add":
                        if (!sender.hasPermission("permapiola.admin.playtime.add")) {
                            sender.sendMessage(ColorUtils.format(languageManager.getMsg(sender, "commands.generic.no-permission")));
                            return true;
                        }
                        if (args.length < 4) {
                            sendFramedMessage(sender, languageManager.getMsg(sender, "playtime.usage-admin-add"));
                            return true;
                        }
                        break;
                    case "remove":
                        if (!sender.hasPermission("permapiola.admin.playtime.remove")) {
                            sender.sendMessage(ColorUtils.format(languageManager.getMsg(sender, "commands.generic.no-permission")));
                            return true;
                        }
                        if (args.length < 4) {
                            sendFramedMessage(sender, languageManager.getMsg(sender, "playtime.usage-admin-remove"));
                            return true;
                        }
                        break;
                    case "set":
                        if (!sender.hasPermission("permapiola.admin.playtime.set")) {
                            sender.sendMessage(ColorUtils.format(languageManager.getMsg(sender, "commands.generic.no-permission")));
                            return true;
                        }
                        if (args.length < 4) {
                            sendFramedMessage(sender, languageManager.getMsg(sender, "playtime.usage-admin-set"));
                            return true;
                        }
                        break;
                    case "reset":
                        if (!sender.hasPermission("permapiola.admin.playtime.reset")) {
                            sender.sendMessage(ColorUtils.format(languageManager.getMsg(sender, "commands.generic.no-permission")));
                            return true;
                        }
                        break;
                    default:
                        sendFramedMessage(sender, languageManager.getMsg(sender, "playtime.usage-admin"));
                        return true;
                }

                if (!action.equals("reset")) {
                    amount = TimeUtils.parseTimeString(args[3]);
                    if (amount <= 0) {
                        sendFramedMessage(sender, languageManager.getMsg(sender, "playtime.admin-invalid-time"));
                        return true;
                    }
                    formattedAmount = getFormattedPlaytime(sender, amount);
                }

                long currentTime = playtimeManager.getPlaytime(targetUUID);
                String senderMsgPath = "";
                String targetMsgPath = "";

                switch (action) {
                    case "add":
                        playtimeManager.addPlaytime(targetUUID, amount);
                        senderMsgPath = "playtime.admin-add-sender";
                        targetMsgPath = "playtime.admin-add-target";
                        break;
                    case "remove":
                        playtimeManager.setPlaytime(targetUUID, Math.max(0, currentTime - amount));
                        senderMsgPath = "playtime.admin-remove-sender";
                        targetMsgPath = "playtime.admin-remove-target";
                        break;
                    case "set":
                        playtimeManager.setPlaytime(targetUUID, amount);
                        senderMsgPath = "playtime.admin-set-sender";
                        targetMsgPath = "playtime.admin-set-target";
                        break;
                    case "reset":
                        playtimeManager.setPlaytime(targetUUID, 0);
                        senderMsgPath = "playtime.admin-reset-sender";
                        targetMsgPath = "playtime.admin-reset-target";
                        break;
                }

                if (playtimeManager.getName(targetUUID).equals("Desconocido")) {
                    playtimeManager.setPlaytime(targetUUID, playtimeManager.getPlaytime(targetUUID));
                }

                long newTime = playtimeManager.getPlaytime(targetUUID);
                String adminName = sender.getName();

                String msgSender = languageManager.getMsg(sender, senderMsgPath)
                        .replace("%player%", finalTargetName)
                        .replace("%amount%", formattedAmount)
                        .replace("%time%", getFormattedPlaytime(sender, newTime));
                sendFramedMessage(sender, msgSender);

                Player pTarget = Bukkit.getPlayer(targetUUID);
                if (pTarget != null && pTarget.isOnline()) {
                    if (!pTarget.equals(sender)) {
                        String msgTarget = languageManager.getMsg(pTarget, targetMsgPath)
                                .replace("%amount%", formattedAmount)
                                .replace("%time%", getFormattedPlaytime(pTarget, newTime))
                                .replace("%admin%", adminName);
                        sendFramedMessage(pTarget, msgTarget);
                    }
                }
                break;
            }
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            completions.addAll(MAIN_ARGS);
            if (sender.hasPermission("permapiola.admin.playtime")) completions.add("admin");
        }
        else if (args.length == 2) {
            if (args[0].equalsIgnoreCase("show")) completions.addAll(SHOW_ARGS);
            else if (args[0].equalsIgnoreCase("admin")) {
                if (sender.hasPermission("permapiola.admin.playtime")) {
                    completions.addAll(ADMIN_ARGS);
                }
            }
        }
        else if (args.length == 3) {
            boolean isShowPlayer = args[0].equalsIgnoreCase("show") && args[1].equalsIgnoreCase("player");
            boolean isAdminCmd = args[0].equalsIgnoreCase("admin");

            if (isShowPlayer || isAdminCmd) {
                for (UUID uuid : playtimeManager.getAllPlaytimes().keySet()) {
                    String name = playtimeManager.getName(uuid);
                    if (name != null && !name.equals("Desconocido") && !completions.contains(name)) {
                        completions.add(name);
                    }
                }
                Bukkit.getOnlinePlayers().forEach(p -> {
                    if (!completions.contains(p.getName())) completions.add(p.getName());
                });
            }
            else if (args[0].equalsIgnoreCase("show") && args[1].equalsIgnoreCase("team")) {
                for (Team t : TeamManager.getMainScoreboard().getTeams()) completions.add(t.getName());
            }
        }

        return completions.stream()
                .filter(c -> c != null && c.toLowerCase().startsWith(args[args.length-1].toLowerCase()))
                .collect(Collectors.toList());
    }

    private String getPlayerNameWithPrefix(String playerName) {
        @SuppressWarnings("deprecation")
        OfflinePlayer op = Bukkit.getOfflinePlayer(playerName);

        try {
            LuckPerms api = LuckPermsProvider.get();
            User user = api.getUserManager().loadUser(op.getUniqueId()).join();

            if (user != null) {
                SortedMap<Integer, String> prefixes = user.getCachedData().getMetaData().getPrefixes();

                String finalPrefix = "";
                int highestWeight = Integer.MIN_VALUE;

                for (Map.Entry<Integer, String> entry : prefixes.entrySet()) {
                    int weight = entry.getKey();
                    if (weight != 9999 && weight > highestWeight) {
                        highestWeight = weight;
                        finalPrefix = entry.getValue();
                    }
                }

                if (!finalPrefix.isEmpty()) {
                    return finalPrefix + playerName;
                }
            }
        } catch (Exception ignored) {}

        return playerName;
    }
}