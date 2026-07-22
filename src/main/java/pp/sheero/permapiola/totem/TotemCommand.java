package pp.sheero.permapiola.totem;

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

import java.util.*;
import java.util.stream.Collectors;

public class TotemCommand implements CommandExecutor, TabCompleter {

    private final PermaPiola plugin;
    private final TotemManager totemManager;
    private final LanguageManager languageManager;

    private final Map<String, String> commonSounds = new HashMap<>();
    private final Map<String, String> donorSounds = new HashMap<>();

    private static final List<String> MAIN_ARGS = Arrays.asList("show", "top", "topteams", "sound");
    private static final List<String> SHOW_ARGS = Arrays.asList("player", "team");
    private static final List<String> SOUND_ARGS = Arrays.asList("mode", "type");
    private static final List<String> SOUND_MODES = Arrays.asList("all", "team", "off");
    private static final List<String> ADMIN_ARGS = Arrays.asList("add", "remove", "set", "reset");

    public TotemCommand(PermaPiola plugin, TotemManager totemManager, LanguageManager languageManager) {
        this.plugin = plugin;
        this.totemManager = totemManager;
        this.languageManager = languageManager;

        commonSounds.put("totem", "ITEM_TOTEM_USE");

        donorSounds.put("cat_hurt", "ENTITY_CAT_HURT");
        donorSounds.put("blaze_hurt", "ENTITY_BLAZE_HURT");
        donorSounds.put("creacking_attack", "ENTITY_CREAKING_ATTACK");
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

    private String getPlayerNameWithPrefix(String playerName) {
        String prefix = LuckPermsUtils.getPrefixForOffline(playerName);
        if (prefix == null || prefix.isEmpty()) return playerName;
        return prefix + playerName;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {

        if (args.length == 0) {
            if (!(sender instanceof Player)) return true;
            Player player = (Player) sender;
            int count = totemManager.getTotems(player.getUniqueId());
            String msg = languageManager.getMsg(sender, "totems.self")
                    .replace("%player%", getPlayerNameWithPrefix(player.getName()))
                    .replace("%totems%", String.valueOf(count));
            sendFramedMessage(sender, msg);
            return true;
        }

        String subCommand = args[0].toLowerCase();

        switch (subCommand) {
            case "show": {
                if (args.length < 3) {
                    sendFramedMessage(sender, languageManager.getMsg(sender, "totems.usage-show"));
                    return true;
                }

                String type = args[1].toLowerCase();
                String targetName = args[2];

                if (type.equals("player")) {
                    @SuppressWarnings("deprecation")
                    OfflinePlayer target = Bukkit.getOfflinePlayer(targetName);
                    int count = totemManager.getTotems(target.getUniqueId());

                    boolean isSelf = (sender instanceof Player) && ((Player) sender).getUniqueId().equals(target.getUniqueId());
                    String path = isSelf ? "totems.self" : "totems.other-player";

                    String msg = languageManager.getMsg(sender, path)
                            .replace("%player%", getPlayerNameWithPrefix(target.getName() != null ? target.getName() : targetName))
                            .replace("%totems%", String.valueOf(count));
                    sendFramedMessage(sender, msg);
                }
                else if (type.equals("team")) {
                    Team team = TeamManager.getTeamByName(targetName);
                    if (team == null) {
                        sendFramedMessage(sender, languageManager.getMsg(sender, "totems.team-not-found"));
                        return true;
                    }

                    int totalTotems = 0;
                    Map<String, Integer> memberTotems = new HashMap<>();

                    for (String member : team.getEntries()) {
                        @SuppressWarnings("deprecation")
                        OfflinePlayer memberOffline = Bukkit.getOfflinePlayer(member);
                        int mTotems = totemManager.getTotems(memberOffline.getUniqueId());
                        totalTotems += mTotems;
                        memberTotems.put(member, mTotems);
                    }

                    List<Map.Entry<String, Integer>> sortedMembers = new ArrayList<>(memberTotems.entrySet());
                    sortedMembers.sort((a, b) -> b.getValue().compareTo(a.getValue()));

                    StringBuilder sb = new StringBuilder();
                    String headerMsg = languageManager.getMsg(sender, "totems.other-team")
                            .replace("%team%", team.getDisplayName())
                            .replace("%totems%", String.valueOf(totalTotems));
                    sb.append(headerMsg).append("\n");

                    for (Map.Entry<String, Integer> entry : sortedMembers) {
                        String memberLine = languageManager.getMsg(sender, "totems.team-member-format")
                                .replace("%player%", getPlayerNameWithPrefix(entry.getKey()))
                                .replace("%totems%", String.valueOf(entry.getValue()));
                        sb.append(memberLine).append("\n");
                    }
                    sendFramedMessage(sender, sb.toString());
                }
                break;
            }

            case "top": {
                Map<UUID, TotemManager.TotemProfile> allProfiles = totemManager.getAllProfiles();
                List<Map.Entry<UUID, TotemManager.TotemProfile>> sortedList = new ArrayList<>(allProfiles.entrySet());
                sortedList.sort((a, b) -> Integer.compare(b.getValue().count, a.getValue().count));

                StringBuilder sb = new StringBuilder();
                sb.append(languageManager.getMsg(sender, "totems.top-header")).append("\n");

                int position = 1;
                for (Map.Entry<UUID, TotemManager.TotemProfile> entry : sortedList) {
                    if (position > 10) break;
                    if (entry.getValue().count <= 0) continue;

                    OfflinePlayer op = Bukkit.getOfflinePlayer(entry.getKey());
                    String name = op.getName() != null ? op.getName() : "Desconocido";

                    String line = languageManager.getMsg(sender, "totems.top-format")
                            .replace("%pos%", String.valueOf(position))
                            .replace("%name%", getPlayerNameWithPrefix(name))
                            .replace("%totems%", String.valueOf(entry.getValue().count));
                    sb.append(line).append("\n");
                    position++;
                }
                sendFramedMessage(sender, sb.toString());
                break;
            }

            case "topteams": {
                Map<String, Integer> teamTotems = new HashMap<>();
                for (Team team : TeamManager.getMainScoreboard().getTeams()) {
                    int teamTotal = 0;
                    for (String member : team.getEntries()) {
                        Player p = Bukkit.getPlayerExact(member);
                        UUID uid = p != null ? p.getUniqueId() : Bukkit.getOfflinePlayer(member).getUniqueId();
                        teamTotal += totemManager.getTotems(uid);
                    }
                    if (teamTotal > 0) {
                        teamTotems.put(team.getDisplayName(), teamTotal);
                    }
                }

                List<Map.Entry<String, Integer>> sortedTeams = new ArrayList<>(teamTotems.entrySet());
                sortedTeams.sort((a, b) -> b.getValue().compareTo(a.getValue()));

                StringBuilder sb = new StringBuilder();
                sb.append(languageManager.getMsg(sender, "totems.topteams-header")).append("\n");

                int position = 1;
                for (Map.Entry<String, Integer> entry : sortedTeams) {
                    if (position > 10) break;
                    String line = languageManager.getMsg(sender, "totems.topteams-format")
                            .replace("%pos%", String.valueOf(position))
                            .replace("%team%", entry.getKey())
                            .replace("%totems%", String.valueOf(entry.getValue()));
                    sb.append(line).append("\n");
                    position++;
                }
                sendFramedMessage(sender, sb.toString());
                break;
            }

            case "sound": {
                if (!(sender instanceof Player)) return true;
                Player player = (Player) sender;
                if (args.length < 3) {
                    sendFramedMessage(player, languageManager.getMsg(player, "totems.usage-sound"));
                    return true;
                }

                String setting = args[1].toLowerCase();
                String value = args[2].toLowerCase();
                TotemManager.TotemProfile profile = totemManager.getProfile(player.getUniqueId());

                if (setting.equals("mode")) {
                    if (value.equals("all") || value.equals("team") || value.equals("off")) {
                        profile.soundMode = value.toUpperCase();
                        sendFramedMessage(player, languageManager.getMsg(player, "totems.sound-mode-success").replace("%mode%", profile.soundMode));
                    } else {
                        sendFramedMessage(player, languageManager.getMsg(player, "totems.sound-invalid-mode"));
                    }
                }
                else if (setting.equals("type")) {
                    String soundToSet = null;

                    if (commonSounds.containsKey(value)) {
                        soundToSet = commonSounds.get(value);
                    }
                    else if (donorSounds.containsKey(value)) {
                        if (player.hasPermission("permapiola.donor.totem")) {
                            soundToSet = donorSounds.get(value);
                        } else {
                            sendFramedMessage(player, languageManager.getMsg(player, "totems.sound-type-noperm"));
                            return true;
                        }
                    }

                    if (soundToSet != null) {
                        profile.soundType = soundToSet;
                        sendFramedMessage(player, languageManager.getMsg(player, "totems.sound-type-success").replace("%type%", value.toUpperCase()));
                    } else {
                        sendFramedMessage(player, languageManager.getMsg(player, "totems.sound-invalid-type"));
                    }
                }
                break;
            }

            case "admin": {
                if (!sender.hasPermission("permapiola.admin.totem")) {
                    sender.sendMessage(ColorUtils.format(languageManager.getMsg(sender, "commands.generic.no-permission")));
                    return true;
                }
                if (args.length < 3) {
                    sendFramedMessage(sender, languageManager.getMsg(sender, "totems.usage-admin"));
                    return true;
                }

                String action = args[1].toLowerCase();

                @SuppressWarnings("deprecation")
                OfflinePlayer target = Bukkit.getOfflinePlayer(args[2]);
                int amount = 0;

                switch (action) {
                    case "add":
                        if (!sender.hasPermission("permapiola.admin.totem.add")) {
                            sender.sendMessage(ColorUtils.format(languageManager.getMsg(sender, "commands.generic.no-permission")));
                            return true;
                        }
                        if (args.length < 4) {
                            sendFramedMessage(sender, languageManager.getMsg(sender, "totems.usage-admin-add"));
                            return true;
                        }
                        break;
                    case "remove":
                        if (!sender.hasPermission("permapiola.admin.totem.remove")) {
                            sender.sendMessage(ColorUtils.format(languageManager.getMsg(sender, "commands.generic.no-permission")));
                            return true;
                        }
                        if (args.length < 4) {
                            sendFramedMessage(sender, languageManager.getMsg(sender, "totems.usage-admin-remove"));
                            return true;
                        }
                        break;
                    case "set":
                        if (!sender.hasPermission("permapiola.admin.totem.set")) {
                            sender.sendMessage(ColorUtils.format(languageManager.getMsg(sender, "commands.generic.no-permission")));
                            return true;
                        }
                        if (args.length < 4) {
                            sendFramedMessage(sender, languageManager.getMsg(sender, "totems.usage-admin-set"));
                            return true;
                        }
                        break;
                    case "reset":
                        if (!sender.hasPermission("permapiola.admin.totem.reset")) {
                            sender.sendMessage(ColorUtils.format(languageManager.getMsg(sender, "commands.generic.no-permission")));
                            return true;
                        }
                        break;
                    default:
                        sendFramedMessage(sender, languageManager.getMsg(sender, "totems.usage-admin"));
                        return true;
                }

                if (!action.equals("reset")) {
                    try {
                        amount = Integer.parseInt(args[3]);
                    } catch (NumberFormatException e) {
                        sendFramedMessage(sender, languageManager.getMsg(sender, "totems.admin-invalid-number"));
                        return true;
                    }
                }

                int currentTotems = totemManager.getTotems(target.getUniqueId());
                String senderMsgPath = "";
                String targetMsgPath = "";

                switch (action) {
                    case "add":
                        totemManager.setTotems(target.getUniqueId(), currentTotems + amount);
                        senderMsgPath = "totems.admin-add-sender";
                        targetMsgPath = "totems.admin-add-target";
                        break;
                    case "remove":
                        totemManager.setTotems(target.getUniqueId(), Math.max(0, currentTotems - amount));
                        senderMsgPath = "totems.admin-remove-sender";
                        targetMsgPath = "totems.admin-remove-target";
                        break;
                    case "set":
                        totemManager.setTotems(target.getUniqueId(), amount);
                        senderMsgPath = "totems.admin-set-sender";
                        targetMsgPath = "totems.admin-set-target";
                        break;
                    case "reset":
                        totemManager.setTotems(target.getUniqueId(), 0);
                        senderMsgPath = "totems.admin-reset-sender";
                        targetMsgPath = "totems.admin-reset-target";
                        break;
                }

                int newCount = totemManager.getTotems(target.getUniqueId());
                String finalTargetName = target.getName() != null ? target.getName() : args[2];
                String adminName = sender.getName();

                String msgSender = languageManager.getMsg(sender, senderMsgPath)
                        .replace("%player%", finalTargetName)
                        .replace("%amount%", String.valueOf(amount))
                        .replace("%totems%", String.valueOf(newCount));
                sendFramedMessage(sender, msgSender);

                if (target.isOnline()) {
                    Player pTarget = target.getPlayer();
                    if (!pTarget.equals(sender)) {
                        String msgTarget = languageManager.getMsg(pTarget, targetMsgPath)
                                .replace("%amount%", String.valueOf(amount))
                                .replace("%totems%", String.valueOf(newCount))
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
            if (sender.hasPermission("permapiola.admin.totem")) completions.add("admin");
        }
        else if (args.length == 2) {
            if (args[0].equalsIgnoreCase("show")) completions.addAll(SHOW_ARGS);
            else if (args[0].equalsIgnoreCase("sound")) completions.addAll(SOUND_ARGS);
            else if (args[0].equalsIgnoreCase("admin")) {
                if (sender.hasPermission("permapiola.admin.totem")) {
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
            }
            else if (args[0].equalsIgnoreCase("sound")) {
                if (args[1].equalsIgnoreCase("mode")) {
                    completions.addAll(SOUND_MODES);
                } else if (args[1].equalsIgnoreCase("type")) {
                    completions.addAll(commonSounds.keySet());
                    if (sender.hasPermission("permapiola.donor.totem")) {
                        completions.addAll(donorSounds.keySet());
                    }
                }
            }
            else if (args[0].equalsIgnoreCase("admin")) {
                Bukkit.getOnlinePlayers().forEach(p -> completions.add(p.getName()));
            }
        }

        return completions.stream()
                .filter(c -> c != null && c.toLowerCase().startsWith(args[args.length-1].toLowerCase()))
                .collect(Collectors.toList());
    }
}