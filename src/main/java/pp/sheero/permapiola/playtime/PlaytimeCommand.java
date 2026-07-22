package pp.sheero.permapiola.playtime;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.Team;
import pp.sheero.permapiola.PermaPiola;
import pp.sheero.permapiola.managers.LanguageManager;
import pp.sheero.permapiola.teams.TeamManager;
import pp.sheero.permapiola.utils.ColorUtils;
import pp.sheero.permapiola.utils.LuckPermsUtils;
import pp.sheero.permapiola.utils.TimeUtils;

import java.util.*;
import java.util.stream.Collectors;

public class PlaytimeCommand implements CommandExecutor, TabCompleter {

    private final PermaPiola plugin;
    private final PlaytimeManager playtimeManager;
    private final LanguageManager languageManager;

    private static final List<String> MAIN_ARGS = Arrays.asList("show", "top", "topteams");
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

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {

        if (args.length == 0) {
            if (!(sender instanceof Player)) return true;
            Player player = (Player) sender;
            long time = playtimeManager.getPlaytime(player.getUniqueId());
            String msg = languageManager.getMsg(sender, "playtime.self")
                    .replace("%player%", getPlayerNameWithPrefix(player.getName()))
                    .replace("%time%", getFormattedPlaytime(sender, time));
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
                    @SuppressWarnings("deprecation")
                    OfflinePlayer target = Bukkit.getOfflinePlayer(targetName);
                    long time = playtimeManager.getPlaytime(target.getUniqueId());

                    boolean isSelf = (sender instanceof Player) && ((Player) sender).getUniqueId().equals(target.getUniqueId());
                    String path = isSelf ? "playtime.self" : "playtime.other-player";

                    String msg = languageManager.getMsg(sender, path)
                            .replace("%player%", getPlayerNameWithPrefix(target.getName() != null ? target.getName() : targetName))
                            .replace("%time%", getFormattedPlaytime(sender, time));
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
                        @SuppressWarnings("deprecation")
                        OfflinePlayer memberOffline = Bukkit.getOfflinePlayer(member);
                        long mTime = playtimeManager.getPlaytime(memberOffline.getUniqueId());
                        totalTime += mTime;
                        memberTimes.put(member, mTime);
                    }

                    List<Map.Entry<String, Long>> sortedMembers = new ArrayList<>(memberTimes.entrySet());
                    sortedMembers.sort((a, b) -> b.getValue().compareTo(a.getValue()));

                    StringBuilder sb = new StringBuilder();

                    String headerMsg = languageManager.getMsg(sender, "playtime.other-team")
                            .replace("%team%", team.getDisplayName())
                            .replace("%time%", getFormattedPlaytime(sender, totalTime));
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

                    OfflinePlayer op = Bukkit.getOfflinePlayer(entry.getKey());
                    String name = op.getName() != null ? op.getName() : "Desconocido";
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

                for (Team team : TeamManager.getMainScoreboard().getTeams()) {
                    long teamTotal = 0;
                    for (String member : team.getEntries()) {
                        Player p = Bukkit.getPlayerExact(member);
                        UUID uid = p != null ? p.getUniqueId() : Bukkit.getOfflinePlayer(member).getUniqueId();
                        teamTotal += playtimeManager.getPlaytime(uid);
                    }
                    if (teamTotal > 0) {
                        teamTimes.put(team.getDisplayName(), teamTotal);
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

                @SuppressWarnings("deprecation")
                OfflinePlayer target = Bukkit.getOfflinePlayer(args[2]);
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

                long currentTime = playtimeManager.getPlaytime(target.getUniqueId());
                String senderMsgPath = "";
                String targetMsgPath = "";

                switch (action) {
                    case "add":
                        playtimeManager.addPlaytime(target.getUniqueId(), amount);
                        senderMsgPath = "playtime.admin-add-sender";
                        targetMsgPath = "playtime.admin-add-target";
                        break;
                    case "remove":
                        playtimeManager.setPlaytime(target.getUniqueId(), Math.max(0, currentTime - amount));
                        senderMsgPath = "playtime.admin-remove-sender";
                        targetMsgPath = "playtime.admin-remove-target";
                        break;
                    case "set":
                        playtimeManager.setPlaytime(target.getUniqueId(), amount);
                        senderMsgPath = "playtime.admin-set-sender";
                        targetMsgPath = "playtime.admin-set-target";
                        break;
                    case "reset":
                        playtimeManager.setPlaytime(target.getUniqueId(), 0);
                        senderMsgPath = "playtime.admin-reset-sender";
                        targetMsgPath = "playtime.admin-reset-target";
                        break;
                }

                long newTime = playtimeManager.getPlaytime(target.getUniqueId());
                String finalTargetName = target.getName() != null ? target.getName() : args[2];
                String adminName = sender.getName();

                String msgSender = languageManager.getMsg(sender, senderMsgPath)
                        .replace("%player%", finalTargetName)
                        .replace("%amount%", formattedAmount)
                        .replace("%time%", getFormattedPlaytime(sender, newTime));
                sendFramedMessage(sender, msgSender);

                if (target.isOnline()) {
                    Player pTarget = target.getPlayer();
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
            if (args[0].equalsIgnoreCase("show")) {
                if (args[1].equalsIgnoreCase("player")) {
                    Bukkit.getOnlinePlayers().forEach(p -> completions.add(p.getName()));
                } else if (args[1].equalsIgnoreCase("team")) {
                    for (Team t : TeamManager.getMainScoreboard().getTeams()) completions.add(t.getName());
                }
            } else if (args[0].equalsIgnoreCase("admin")) {
                Bukkit.getOnlinePlayers().forEach(p -> completions.add(p.getName()));
            }
        }

        return completions.stream()
                .filter(c -> c != null && c.toLowerCase().startsWith(args[args.length-1].toLowerCase()))
                .collect(Collectors.toList());
    }

    private String getPlayerNameWithPrefix(String playerName) {
        String prefix = LuckPermsUtils.getPrefixForOffline(playerName);
        if (prefix == null || prefix.isEmpty()) {
            return playerName;
        }
        return prefix + playerName;
    }
}