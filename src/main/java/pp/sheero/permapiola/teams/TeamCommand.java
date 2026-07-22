package pp.sheero.permapiola.teams;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.Team;
import pp.sheero.permapiola.PermaPiola;
import pp.sheero.permapiola.managers.LanguageManager;
import pp.sheero.permapiola.utils.ColorUtils;
import pp.sheero.permapiola.utils.LuckPermsUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class TeamCommand implements CommandExecutor, TabCompleter {

    private final PermaPiola plugin;
    private final LanguageManager lang;

    private static final Pattern RESTRICTED_COLORS = Pattern.compile("(?i).*&[4klmnor].*");
    private static final Pattern VALID_CHARS = Pattern.compile("^[a-zA-Z0-9_]+$");

    private static final List<String> BASE_ARGS = Arrays.asList("help", "create", "leave", "invite", "accept", "memberlist");
    private static final List<String> ADMIN_ARGS = Arrays.asList("system", "reset", "fire", "size", "forcejoin", "forceleave", "list", "spy");
    private static final List<String> SYSTEM_ARGS = Arrays.asList("on", "off");

    public TeamCommand(PermaPiola plugin, LanguageManager lang) {
        this.plugin = plugin;
        this.lang = lang;
    }

    private void sendFramedMessage(CommandSender sender, String rawMessage) {
        String line = lang.getMsg(sender, "commands.generic.line");
        sender.sendMessage(ColorUtils.format(line));

        int start = 0;
        int end;
        while ((end = rawMessage.indexOf('\n', start)) != -1) {
            sender.sendMessage(ColorUtils.format(rawMessage.substring(start, end)));
            start = end + 1;
        }
        sender.sendMessage(ColorUtils.format(rawMessage.substring(start)));

        sender.sendMessage(ColorUtils.format(line));
    }

    private void sendMessage(CommandSender sender, String path) {
        String message = lang.getMsg(sender, path);
        sendFramedMessage(sender, message);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {

        if (args.length == 0 || args[0].equalsIgnoreCase("help")) {
            List<String> helpLines = new ArrayList<>(lang.getMsgList(sender, "teams.help.user"));

            if (sender.hasPermission("permapiola.admin")) {
                helpLines.add(" ");
                helpLines.addAll(lang.getMsgList(sender, "teams.help.staff"));
            }

            for (String line : helpLines) {
                sender.sendMessage(ColorUtils.format(line));
            }
            return true;
        }

        String subCommand = args[0].toLowerCase();
        boolean isPlayer = sender instanceof Player;
        Player player = isPlayer ? (Player) sender : null;

        if (isPlayer && !sender.hasPermission("permapiola.admin") && !TeamManager.isTeamsEnabled()) {
            sendMessage(player, "teams.system-disabled");
            return true;
        }

        switch (subCommand) {

            // ========================================== #
            //               ZONA STAFF                   #
            // ========================================== #
            case "system": {
                if (!sender.hasPermission("permapiola.admin.team.system")) { sendMessage(sender, "commands.generic.no-permission"); return true; }
                if (args.length < 2) { sendMessage(sender, "teams.staff.usage-system"); return true; }

                String state = args[1].toLowerCase();
                boolean currentState = TeamManager.isTeamsEnabled();

                if (state.equals("on") || state.equals("enable") || state.equals("true")) {
                    if (currentState) {
                        sendMessage(sender, "teams.staff.system-already-on");
                    } else {
                        plugin.getConfig().set("teams.enabled", true);
                        plugin.saveConfig();
                        TeamManager.loadConfigCache(plugin); // Actualiza la caché
                        String msg = lang.getMsg(sender, "teams.staff.system-status").replace("%status%", lang.getMsg(sender, "teams.staff.system-on"));
                        sendFramedMessage(sender, msg);
                    }
                } else if (state.equals("off") || state.equals("disable") || state.equals("false")) {
                    if (!currentState) {
                        sendMessage(sender, "teams.staff.system-already-off");
                    } else {
                        plugin.getConfig().set("teams.enabled", false);
                        plugin.saveConfig();
                        TeamManager.loadConfigCache(plugin); // Actualiza la caché
                        String msg = lang.getMsg(sender, "teams.staff.system-status").replace("%status%", lang.getMsg(sender, "teams.staff.system-off"));
                        sendFramedMessage(sender, msg);
                    }
                } else {
                    sendMessage(sender, "teams.staff.usage-system");
                }
                break;
            }

            case "reset": {
                if (!sender.hasPermission("permapiola.admin.team.reset")) { sendMessage(sender, "commands.generic.no-permission"); return true; }
                sendMessage(sender, "teams.staff.reset-start");
                TeamManager.resetAllTeams();
                sendMessage(sender, "teams.staff.reset-complete");
                break;
            }

            case "fire":
            case "friendlyfire": {
                if (!sender.hasPermission("permapiola.admin.team.fire")) { sendMessage(sender, "commands.generic.no-permission"); return true; }
                if (args.length < 2) { sendMessage(sender, "teams.staff.usage-fire"); return true; }

                boolean enabled = args[1].toLowerCase().matches("on|true|enable");
                boolean currentState = plugin.getConfig().getBoolean("teams.friendly-fire");

                if (enabled == currentState) {
                    if (enabled) sendMessage(sender, "teams.staff.fire-already-on");
                    else sendMessage(sender, "teams.staff.fire-already-off");
                } else {
                    plugin.getConfig().set("teams.friendly-fire", enabled);
                    plugin.saveConfig();
                    TeamManager.loadConfigCache(plugin); // Actualiza caché
                    TeamManager.setGlobalFriendlyFire(enabled);

                    String statusText = enabled ? lang.getMsg(sender, "teams.staff.system-on") : lang.getMsg(sender, "teams.staff.system-off");
                    String msg = lang.getMsg(sender, "teams.staff.fire-status").replace("%status%", statusText);
                    sendFramedMessage(sender, msg);
                }
                break;
            }

            case "size": {
                if (!sender.hasPermission("permapiola.admin.team.size")) { sendMessage(sender, "commands.generic.no-permission"); return true; }
                if (args.length < 2) { sendMessage(sender, "teams.staff.usage-size"); return true; }
                try {
                    int newSize = Integer.parseInt(args[1]);
                    if (newSize < 1) throw new NumberFormatException();

                    int currentSize = TeamManager.getMaxSize();
                    if (newSize == currentSize) {
                        sendFramedMessage(sender, lang.getMsg(sender, "teams.staff.size-already-same").replace("%size%", String.valueOf(newSize)));
                        return true;
                    }

                    plugin.getConfig().set("teams.max-size", newSize);
                    plugin.saveConfig();
                    TeamManager.loadConfigCache(plugin); // Actualiza caché
                    String msg = lang.getMsg(sender, "teams.staff.size-status").replace("%size%", String.valueOf(newSize));
                    sendFramedMessage(sender, msg);
                } catch (NumberFormatException e) {
                    sendMessage(sender, "teams.staff.usage-size");
                }
                break;
            }

            case "forcejoin": {
                if (!sender.hasPermission("permapiola.admin.team.forcejoin")) { sendMessage(sender, "commands.generic.no-permission"); return true; }
                if (args.length < 2) { sendMessage(sender, "teams.staff.usage-forcejoin"); return true; }

                Team team = TeamManager.getTeamByName(args[1]);
                if (team == null) { sendMessage(sender, "teams.staff.team-not-found"); return true; }

                Player target = args.length >= 3 ? Bukkit.getPlayerExact(args[2]) : player;
                if (target == null) { sendMessage(sender, "teams.staff.player-not-found"); return true; }

                if (TeamManager.hasTeam(target)) {
                    Team targetTeam = TeamManager.getTeam(target);
                    boolean isSameTeam = targetTeam.equals(team);

                    if (sender.equals(target)) {
                        if (isSameTeam) {
                            sendMessage(sender, "teams.staff.forcejoin-self-already-same");
                        } else {
                            sendMessage(sender, "teams.already-in-team");
                        }
                    } else {
                        String rawPrefix = LuckPermsUtils.getPrefix(target);
                        String cleanPrefix = rawPrefix != null ? LuckPermsUtils.cleanTeamTag(rawPrefix, targetTeam) : "";

                        if (isSameTeam) {
                            sendFramedMessage(sender, lang.getMsg(sender, "teams.staff.forcejoin-other-already-same")
                                    .replace("%player_prefix%", cleanPrefix)
                                    .replace("%player%", target.getName()));
                        } else {
                            sendFramedMessage(sender, lang.getMsg(sender, "teams.staff.forcejoin-other-already-different")
                                    .replace("%player_prefix%", cleanPrefix)
                                    .replace("%player%", target.getName())
                                    .replace("%current_team%", targetTeam.getDisplayName()));
                        }
                    }
                    return true;
                }

                team.addEntry(target.getName());
                LuckPermsUtils.addPlayerToGroup(target.getName(), team.getName());

                String rawTargetPrefix = LuckPermsUtils.getPrefix(target);
                String rawTargetSuffix = LuckPermsUtils.getSuffix(target);
                String cleanTargetPrefix = rawTargetPrefix != null ? LuckPermsUtils.cleanTeamTag(rawTargetPrefix, team) : "";
                String cleanTargetSuffix = rawTargetSuffix != null ? LuckPermsUtils.cleanTeamTag(rawTargetSuffix, team) : "";

                if (sender.equals(target)) {
                    sendFramedMessage(sender, lang.getMsg(sender, "teams.staff.forceleave-self"));
                } else {
                    sendFramedMessage(target, lang.getMsg(target, "teams.staff.forceleave-target"));
                    sendFramedMessage(sender, lang.getMsg(sender, "teams.staff.forceleave-success")
                            .replace("%player_prefix%", cleanTargetPrefix)
                            .replace("%player_suffix%", cleanTargetSuffix)
                            .replace("%player%", target.getName()));
                }

                sendFramedMessage(target, lang.getMsg(target, "teams.staff.forcejoin-target")
                        .replace("%team%", team.getDisplayName()));

                String broadcastMsg = lang.getMsg(sender, "teams.staff.forcejoin-broadcast")
                        .replace("%player_prefix%", cleanTargetPrefix)
                        .replace("%player_suffix%", cleanTargetSuffix)
                        .replace("%player%", target.getName());

                for (String memberName : team.getEntries()) {
                    if (!memberName.equals(target.getName())) {
                        Player member = Bukkit.getPlayerExact(memberName);
                        if (member != null && member.isOnline()) sendFramedMessage(member, broadcastMsg);
                    }
                }
                break;
            }

            case "forceleave": {
                if (!sender.hasPermission("permapiola.admin.team.forceleave")) { sendMessage(sender, "commands.generic.no-permission"); return true; }
                Player target = args.length >= 2 ? Bukkit.getPlayerExact(args[1]) : player;

                if (target == null) { sendMessage(sender, "teams.staff.player-not-found"); return true; }

                if (!TeamManager.hasTeam(target)) {
                    if (sender.equals(target)) {
                        sendMessage(sender, "teams.not-in-team");
                    } else {
                        String rawPrefix = LuckPermsUtils.getPrefix(target);
                        String cleanPrefix = rawPrefix != null ? rawPrefix : "";
                        sendFramedMessage(sender, lang.getMsg(sender, "teams.staff.forceleave-other-no-team")
                                .replace("%player_prefix%", cleanPrefix)
                                .replace("%player%", target.getName()));
                    }
                    return true;
                }

                Team team = TeamManager.getTeam(target);
                boolean isLeader = target.getName().equals(TeamManager.getTeamLeader(team));
                String internalTeamName = team.getName();

                String rawTargetPrefix = LuckPermsUtils.getPrefix(target);
                String rawTargetSuffix = LuckPermsUtils.getSuffix(target);
                String cleanTargetPrefix = rawTargetPrefix != null ? LuckPermsUtils.cleanTeamTag(rawTargetPrefix, team) : "";
                String cleanTargetSuffix = rawTargetSuffix != null ? LuckPermsUtils.cleanTeamTag(rawTargetSuffix, team) : "";

                if (isLeader) {
                    for (String memberName : team.getEntries()) {
                        LuckPermsUtils.removePlayerFromGroup(memberName, internalTeamName);
                        if (!memberName.equals(target.getName())) {
                            Player member = Bukkit.getPlayerExact(memberName);
                            if (member != null && member.isOnline()) {
                                String disbandMsg = lang.getMsg(member, "teams.staff.forceleave-disbanded");
                                sendFramedMessage(member, disbandMsg);
                            }
                        }
                    }
                    LuckPermsUtils.deleteTeamGroup(internalTeamName);
                } else {
                    for (String memberName : team.getEntries()) {
                        if (!memberName.equals(target.getName())) {
                            Player member = Bukkit.getPlayerExact(memberName);
                            if (member != null && member.isOnline()) {
                                String broadcastMsg = lang.getMsg(member, "teams.staff.forceleave-broadcast")
                                        .replace("%player_prefix%", cleanTargetPrefix)
                                        .replace("%player_suffix%", cleanTargetSuffix)
                                        .replace("%player%", target.getName());
                                sendFramedMessage(member, broadcastMsg);
                            }
                        }
                    }
                    LuckPermsUtils.removePlayerFromGroup(target.getName(), internalTeamName);
                }
                TeamManager.removePlayerFromTeam(target);
                refreshPlayerForEveryone(target);

                sendFramedMessage(target, lang.getMsg(target, "teams.staff.forceleave-target"));

                if (!sender.equals(target)) {
                    sendFramedMessage(sender, lang.getMsg(sender, "teams.staff.forceleave-success")
                            .replace("%player_prefix%", cleanTargetPrefix)
                            .replace("%player_suffix%", cleanTargetSuffix)
                            .replace("%player%", target.getName()));
                }
                break;
            }

            case "list": {
                if (!sender.hasPermission("permapiola.admin.team.list")) { sendMessage(sender, "commands.generic.no-permission"); return true; }

                List<Team> allTeams = new ArrayList<>(TeamManager.getMainScoreboard().getTeams());
                if (allTeams.isEmpty()) { sendMessage(sender, "teams.staff.no-teams"); return true; }

                int teamsPerPage = 5;
                int maxPages = (int) Math.ceil((double) allTeams.size() / teamsPerPage);
                int page = 1;

                if (args.length >= 2) {
                    try { page = Integer.parseInt(args[1]); } catch (NumberFormatException ignored) {}
                }

                if (page < 1 || page > maxPages) {
                    sendFramedMessage(sender, lang.getMsg(sender, "teams.staff.invalid-page").replace("%max_page%", String.valueOf(maxPages)));
                    return true;
                }

                int startIndex = (page - 1) * teamsPerPage;
                List<Team> pageTeams = allTeams.subList(startIndex, Math.min(startIndex + teamsPerPage, allTeams.size()));

                sender.sendMessage(ColorUtils.format(lang.getMsg(sender, "commands.generic.line")));
                sender.sendMessage(ColorUtils.format(lang.getMsg(sender, "teams.staff.lists-header").replace("%page%", String.valueOf(page)).replace("%max_page%", String.valueOf(maxPages))));
                sender.sendMessage(" ");

                int index = startIndex + 1;
                for (Team t : pageTeams) {
                    String leaderName = TeamManager.getTeamLeader(t);
                    String formattedLeader = "";
                    List<String> formattedMembers = new ArrayList<>();

                    for (String memberName : t.getEntries()) {
                        String pFormat = LuckPermsUtils.formatPlayerForList(memberName, t, sender, plugin);
                        if (memberName.equals(leaderName)) formattedLeader = pFormat;
                        else formattedMembers.add(pFormat);
                    }

                    sender.sendMessage(ColorUtils.format(lang.getMsg(sender, "teams.staff.lists-team-format").replace("%index%", String.valueOf(index)).replace("%team_name%", t.getDisplayName())));
                    sender.sendMessage(ColorUtils.format(lang.getMsg(sender, "teams.staff.lists-leader").replace("%leader%", formattedLeader)));

                    if (!formattedMembers.isEmpty()) {
                        String memPrefix = lang.getMsg(sender, "teams.staff.lists-members-prefix");
                        String separator = lang.getMsg(sender, "teams.staff.lists-separator");
                        sender.sendMessage(ColorUtils.format(memPrefix + String.join(separator, formattedMembers)));
                    }
                    index++;
                }

                if (maxPages > 1 && sender instanceof Player) {
                    sender.sendMessage(" ");
                    Component navRow = Component.empty();

                    if (page > 1) {
                        String prevStr = lang.getMsg(sender, "teams.staff.lists-btn-prev");
                        Component prevBtn = LegacyComponentSerializer.legacySection().deserialize(ColorUtils.format(prevStr))
                                .clickEvent(ClickEvent.runCommand("/team list " + (page - 1)));
                        navRow = navRow.append(prevBtn).append(Component.text("   "));
                    }

                    if (page < maxPages) {
                        String nextStr = lang.getMsg(sender, "teams.staff.lists-btn-next");
                        Component nextBtn = LegacyComponentSerializer.legacySection().deserialize(ColorUtils.format(nextStr))
                                .clickEvent(ClickEvent.runCommand("/team list " + (page + 1)));
                        navRow = navRow.append(nextBtn);
                    }

                    sender.sendMessage(navRow);
                }

                sender.sendMessage(ColorUtils.format(lang.getMsg(sender, "commands.generic.line")));
                break;
            }

            case "spy": {
                if (!sender.hasPermission("permapiola.admin.team.spy")) { sendMessage(sender, "commands.generic.no-permission"); return true; }
                if (!isPlayer) { sendMessage(sender, "teams.only-players"); return true; }

                TeamManager.toggleSpy(player);
                boolean isSpying = TeamManager.hasSpyEnabled(player);

                String path = isSpying ? "teams.staff.spy-toggled-on" : "teams.staff.spy-toggled-off";
                sendFramedMessage(player, lang.getMsg(player, path));
                break;
            }

            // ========================================== #
            //               ZONA USUARIO                 #
            // ========================================== #
            case "create": {
                if (!isPlayer) { sendMessage(sender, "teams.only-players"); return true; }
                if (TeamManager.hasTeam(player)) { sendMessage(player, "teams.already-in-team"); return true; }
                if (args.length < 2) { sendMessage(player, "teams.create.specify-name"); return true; }

                String rawTeamName = args[1];
                String cleanName = ColorUtils.stripColors(rawTeamName);
                boolean isDonator = player.hasPermission("permapiola.donor");

                if (RESTRICTED_COLORS.matcher(rawTeamName).matches()) {
                    sendMessage(player, "teams.create.restricted-colors");
                    return true;
                }

                if (!VALID_CHARS.matcher(cleanName).matches()) {
                    sendMessage(player, "teams.create.invalid-chars");
                    return true;
                }

                if (!isDonator) {
                    if (cleanName.length() > 8) { sendMessage(player, "teams.create.name-too-long-default"); return true; }
                    if (rawTeamName.contains("#")) { sendMessage(player, "teams.create.no-hex-colors"); return true; }
                } else {
                    if (cleanName.length() > 16) { sendMessage(player, "teams.create.name-too-long"); return true; }
                }

                if (TeamManager.isTeamNameTaken(cleanName)) { sendMessage(player, "teams.create.name-taken"); return true; }

                String displayName = rawTeamName.replace("_", " ");
                TeamManager.createTeam(player, cleanName, displayName, plugin);
                String lpPrefix = ColorUtils.format(displayName);
                LuckPermsUtils.createTeamGroupAndAddPlayer(player, cleanName, lpPrefix);

                sendFramedMessage(player, lang.getMsg(player, "teams.create.success").replace("%team%", lpPrefix));
                break;
            }

            case "leave": {
                if (!isPlayer) { sendMessage(sender, "teams.only-players"); return true; }
                if (!TeamManager.hasTeam(player)) { sendMessage(player, "teams.not-in-team"); return true; }

                Team team = TeamManager.getTeam(player);
                boolean isLeader = player.getName().equals(TeamManager.getTeamLeader(team));
                String internalTeamName = team.getName();

                if (isLeader) {
                    for (String memberName : team.getEntries()) {
                        LuckPermsUtils.removePlayerFromGroup(memberName, internalTeamName);
                        if (!memberName.equals(player.getName())) {
                            Player member = Bukkit.getPlayerExact(memberName);
                            if (member != null && member.isOnline()) {
                                String disbandMsg = lang.getMsg(member, "teams.leave.disbanded");
                                sendFramedMessage(member, disbandMsg);
                            }
                        }
                    }
                    sendMessage(player, "teams.leave.success");
                    LuckPermsUtils.deleteTeamGroup(internalTeamName);
                } else {
                    String rawPrefix = LuckPermsUtils.getPrefix(player);
                    String rawSuffix = LuckPermsUtils.getSuffix(player);

                    String cleanPrefix = rawPrefix != null ? LuckPermsUtils.cleanTeamTag(rawPrefix, team) : "";
                    String cleanSuffix = rawSuffix != null ? LuckPermsUtils.cleanTeamTag(rawSuffix, team) : "";

                    for (String memberName : team.getEntries()) {
                        if (!memberName.equals(player.getName())) {
                            Player member = Bukkit.getPlayerExact(memberName);
                            if (member != null && member.isOnline()) {
                                String broadcastMsg = lang.getMsg(member, "teams.leave.broadcast")
                                        .replace("%player_prefix%", cleanPrefix)
                                        .replace("%player_suffix%", cleanSuffix)
                                        .replace("%player%", player.getName());
                                sendFramedMessage(member, broadcastMsg);
                            }
                        }
                    }
                    sendMessage(player, "teams.leave.success");
                    LuckPermsUtils.removePlayerFromGroup(player.getName(), internalTeamName);
                }
                TeamManager.removePlayerFromTeam(player);
                refreshPlayerForEveryone(player);
                break;
            }

            case "glow": {
                if (!isPlayer) { sendMessage(sender, "teams.only-players"); return true; }
                if (!TeamManager.hasTeam(player)) { sendMessage(player, "teams.not-in-team"); return true; }

                if (!Bukkit.getPluginManager().isPluginEnabled("ProtocolLib")) {
                    sendFramedMessage(player, lang.getMsg(player, "teams.glow.disabled-dependency"));
                    return true;
                }

                if (args.length < 2) {
                    player.sendMessage(ColorUtils.format(lang.getMsg(player, "teams.glow.usage")));
                    return true;
                }

                String action = args[1].toLowerCase();
                Team team = TeamManager.getTeam(player);

                if (action.equals("on")) {
                    if (TeamManager.hasGlowEnabled(player)) {
                        sendFramedMessage(player, lang.getMsg(player, "teams.glow.already-on"));
                        return true;
                    }
                    TeamManager.toggleGlow(player);
                    refreshTeamForPlayer(player, team);
                    sendFramedMessage(player, lang.getMsg(player, "teams.glow.toggled-on"));
                }
                else if (action.equals("off")) {
                    if (!TeamManager.hasGlowEnabled(player)) {
                        sendFramedMessage(player, lang.getMsg(player, "teams.glow.already-off"));
                        return true;
                    }
                    TeamManager.toggleGlow(player);
                    refreshTeamForPlayer(player, team);
                    sendFramedMessage(player, lang.getMsg(player, "teams.glow.toggled-off"));
                }
                break;
            }

            case "invite": {
                if (!isPlayer) { sendMessage(sender, "teams.only-players"); return true; }
                if (!TeamManager.hasTeam(player)) { sendMessage(player, "teams.not-in-team"); return true; }
                if (args.length < 2) { sendMessage(player, "teams.invite.specify-player"); return true; }

                Player target = Bukkit.getPlayerExact(args[1]);
                if (target == null) { sendMessage(player, "commands.generic.player-offline"); return true; }
                if (target.equals(player)) { sendMessage(player, "teams.invite.cant-invite-self"); return true; }

                Team inviterTeam = TeamManager.getTeam(player);

                if (TeamManager.hasTeam(target)) {
                    Team targetTeam = TeamManager.getTeam(target);

                    if (inviterTeam.equals(targetTeam)) {
                        String msg = lang.getMsg(player, "teams.invite.already-in-your-team")
                                .replace("%target%", target.getName());
                        sendFramedMessage(player, msg);
                    } else {
                        sendMessage(player, "teams.invite.target-already-in-team");
                    }
                    return true;
                }
                if (TeamManager.hasInvite(player, target)) { sendMessage(player, "teams.invite.already-invited"); return true; }

                int maxSize = TeamManager.getMaxSize();

                if (inviterTeam.getEntries().size() >= maxSize) {
                    sendFramedMessage(player, lang.getMsg(player, "teams.team-full").replace("%size%", String.valueOf(maxSize)));
                    return true;
                }

                String tRawPrefix = LuckPermsUtils.getPrefix(target);
                String tRawSuffix = LuckPermsUtils.getSuffix(target);

                sendFramedMessage(player, lang.getMsg(player, "teams.invite.sent")
                        .replace("%target_prefix%", tRawPrefix != null ? tRawPrefix : "")
                        .replace("%target_suffix%", tRawSuffix != null ? tRawSuffix : "")
                        .replace("%target%", target.getName()));

                String pRawPrefix = LuckPermsUtils.getPrefix(player);
                String pRawSuffix = LuckPermsUtils.getSuffix(player);

                String cleanInviterPrefix = pRawPrefix != null ? LuckPermsUtils.cleanTeamTag(pRawPrefix, inviterTeam) : "";
                String cleanInviterSuffix = pRawSuffix != null ? LuckPermsUtils.cleanTeamTag(pRawSuffix, inviterTeam) : "";

                String lineStr = lang.getMsg(target, "commands.generic.line");
                Component lineComp = LegacyComponentSerializer.legacySection().deserialize(ColorUtils.format(lineStr));

                target.sendMessage(lineComp);

                String receivedMsg = lang.getMsg(target, "teams.invite.received")
                        .replace("%player_prefix%", cleanInviterPrefix)
                        .replace("%player_suffix%", cleanInviterSuffix)
                        .replace("%player%", player.getName());

                String[] lines = receivedMsg.split("\\n");

                String hoverRaw = lang.getMsg(target, "teams.invite.hover")
                        .replace("%player%", player.getName())
                        .replace("\\n", "\n");

                Component hoverComp = LegacyComponentSerializer.legacySection().deserialize(ColorUtils.format(hoverRaw));

                for (int i = 0; i < lines.length; i++) {
                    Component textComp = LegacyComponentSerializer.legacySection().deserialize(ColorUtils.format(lines[i]));

                    if (i == lines.length - 1) {
                        textComp = textComp
                                .clickEvent(ClickEvent.runCommand("/team accept " + player.getName()))
                                .hoverEvent(net.kyori.adventure.text.event.HoverEvent.showText(hoverComp));
                    }

                    target.sendMessage(textComp);
                }

                target.sendMessage(lineComp);

                int taskId = Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    if (TeamManager.hasInvite(player, target)) {
                        TeamManager.removeInvite(player, target);
                        if (player.isOnline()) {
                            String expireTPre = LuckPermsUtils.getPrefix(target);
                            String expireTSuf = LuckPermsUtils.getSuffix(target);
                            sendFramedMessage(player, lang.getMsg(player, "teams.invite.expired-sender")
                                    .replace("%target_prefix%", expireTPre != null ? expireTPre : "")
                                    .replace("%target_suffix%", expireTSuf != null ? expireTSuf : "")
                                    .replace("%target%", target.getName()));
                        }
                        if (target.isOnline()) {
                            sendFramedMessage(target, lang.getMsg(target, "teams.invite.expired-target")
                                    .replace("%player_prefix%", cleanInviterPrefix)
                                    .replace("%player_suffix%", cleanInviterSuffix)
                                    .replace("%player%", player.getName()));
                        }
                    }
                }, 1200L).getTaskId();

                TeamManager.addInvite(player, target, taskId);
                break;
            }

            case "accept": {
                if (!isPlayer) { sendMessage(sender, "teams.only-players"); return true; }
                if (TeamManager.hasTeam(player)) { sendMessage(player, "teams.already-in-team"); return true; }
                if (args.length < 2) { sendMessage(player, "teams.invite.specify-player"); return true; }

                Player inviter = Bukkit.getPlayerExact(args[1]);
                if (inviter == null || !TeamManager.hasInvite(inviter, player)) { sendMessage(player, "teams.accept.no-invite"); return true; }

                Team team = TeamManager.getTeam(inviter);
                if (team == null) {
                    sendMessage(player, "teams.accept.no-invite");
                    TeamManager.removeInvite(inviter, player);
                    return true;
                }

                int maxSize = TeamManager.getMaxSize();
                if (team.getEntries().size() >= maxSize) {
                    sendFramedMessage(player, lang.getMsg(player, "teams.team-full").replace("%size%", String.valueOf(maxSize)));
                    return true;
                }

                TeamManager.removeInvite(inviter, player);
                team.addEntry(player.getName());

                String iRawPrefix = LuckPermsUtils.getPrefix(inviter);
                String iRawSuffix = LuckPermsUtils.getSuffix(inviter);
                String cleanInviterPrefix = iRawPrefix != null ? LuckPermsUtils.cleanTeamTag(iRawPrefix, team) : "";
                String cleanInviterSuffix = iRawSuffix != null ? LuckPermsUtils.cleanTeamTag(iRawSuffix, team) : "";

                sendFramedMessage(player, lang.getMsg(player, "teams.accept.success")
                        .replace("%player_prefix%", cleanInviterPrefix)
                        .replace("%player_suffix%", cleanInviterSuffix)
                        .replace("%player%", inviter.getName()));

                String pRawPrefix = LuckPermsUtils.getPrefix(player);
                String pRawSuffix = LuckPermsUtils.getSuffix(player);
                String cleanNewMemberPrefix = pRawPrefix != null ? LuckPermsUtils.cleanTeamTag(pRawPrefix, team) : "";
                String cleanNewMemberSuffix = pRawSuffix != null ? LuckPermsUtils.cleanTeamTag(pRawSuffix, team) : "";

                for (String memberName : team.getEntries()) {
                    if (!memberName.equals(player.getName())) {
                        Player member = Bukkit.getPlayerExact(memberName);
                        if (member != null && member.isOnline()) {
                            String broadcastMsg = lang.getMsg(member, "teams.accept.broadcast")
                                    .replace("%new_member_prefix%", cleanNewMemberPrefix)
                                    .replace("%new_member_suffix%", cleanNewMemberSuffix)
                                    .replace("%new_member%", player.getName());
                            sendFramedMessage(member, broadcastMsg);
                        }
                    }
                }

                LuckPermsUtils.addPlayerToGroup(player.getName(), team.getName());
                break;
            }

            case "memberlist": {
                if (!isPlayer) { sendMessage(sender, "teams.only-players"); return true; }
                if (!TeamManager.hasTeam(player)) { sendMessage(player, "teams.not-in-team"); return true; }

                Team team = TeamManager.getTeam(player);
                String leaderName = TeamManager.getTeamLeader(team);
                String formattedLeader = "";
                List<String> formattedMembers = new ArrayList<>();

                for (String memberName : team.getEntries()) {
                    String playerString = LuckPermsUtils.formatPlayerForList(memberName, team, player, plugin);
                    if (memberName.equals(leaderName)) formattedLeader = playerString;
                    else formattedMembers.add(playerString);
                }

                player.sendMessage(ColorUtils.format(lang.getMsg(player, "commands.generic.line")));
                player.sendMessage(ColorUtils.format(lang.getMsg(player, "teams.memberlist.team-name").replace("%team%", team.getDisplayName())));
                player.sendMessage(" ");
                player.sendMessage(ColorUtils.format(lang.getMsg(player, "teams.memberlist.leader").replace("%leader_info%", formattedLeader)));

                if (!formattedMembers.isEmpty()) {
                    String memPrefix = lang.getMsg(player, "teams.memberlist.members-prefix");
                    String separator = lang.getMsg(player, "teams.memberlist.members-separator");
                    player.sendMessage(ColorUtils.format(memPrefix + String.join(separator, formattedMembers)));
                }

                player.sendMessage(ColorUtils.format(lang.getMsg(player, "commands.generic.line")));
                break;
            }
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();
        boolean isAdmin = sender.hasPermission("permapiola.admin");
        boolean hasProtocolLib = Bukkit.getPluginManager().isPluginEnabled("ProtocolLib");

        if (args.length == 1) {
            completions.addAll(BASE_ARGS);
            if (hasProtocolLib) completions.add("glow");
            if (isAdmin) completions.addAll(ADMIN_ARGS);

            return completions.stream().filter(c -> c.startsWith(args[0].toLowerCase())).collect(Collectors.toList());
        }

        if (args.length == 2) {
            String sub = args[0].toLowerCase();

            if (sub.equals("invite") || sub.equals("accept")) {
                Bukkit.getOnlinePlayers().forEach(p -> completions.add(p.getName()));
            }

            if (sub.equals("glow") && hasProtocolLib) {
                completions.addAll(SYSTEM_ARGS);
            }

            if (isAdmin) {
                if (sub.equals("system") || sub.equals("fire")) {
                    completions.addAll(SYSTEM_ARGS);
                } else if (sub.equals("size")) {
                    completions.add("<numero>");
                } else if (sub.equals("forcejoin")) {
                    TeamManager.getMainScoreboard().getTeams().forEach(t -> completions.add(t.getName()));
                } else if (sub.equals("forceleave")) {
                    Bukkit.getOnlinePlayers().forEach(p -> completions.add(p.getName()));
                }
            }
            return completions.stream().filter(c -> c.toLowerCase().startsWith(args[1].toLowerCase())).collect(Collectors.toList());
        }

        if (args.length == 3 && args[0].equalsIgnoreCase("forcejoin") && isAdmin) {
            Bukkit.getOnlinePlayers().forEach(p -> completions.add(p.getName()));
            return completions.stream().filter(c -> c.toLowerCase().startsWith(args[2].toLowerCase())).collect(Collectors.toList());
        }

        return new ArrayList<>();
    }

    private void refreshTeamForPlayer(Player player, Team team) {
        for (String memberName : team.getEntries()) {
            Player member = Bukkit.getPlayerExact(memberName);
            if (member != null && member.isOnline() && !member.equals(player)) {
                player.hidePlayer(plugin, member);
                player.showPlayer(plugin, member);
            }
        }
    }

    private void refreshPlayerForEveryone(Player target) {
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (!online.equals(target)) {
                online.hidePlayer(plugin, target);
                online.showPlayer(plugin, target);
            }
        }
    }
}