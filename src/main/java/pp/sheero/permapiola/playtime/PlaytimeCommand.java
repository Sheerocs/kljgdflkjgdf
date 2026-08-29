package pp.sheero.permapiola.playtime;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import pp.sheero.permapiola.core.LanguageManager;
import pp.sheero.permapiola.teams.PiolaTeam;
import pp.sheero.permapiola.teams.TeamManager;
import pp.sheero.permapiola.utils.ColorUtils;
import pp.sheero.permapiola.utils.LuckPermsUtils;
import pp.sheero.permapiola.utils.TimeUtils;

import java.util.*;

public class PlaytimeCommand {

    public static void register(Commands commands, PlaytimeManager playtimeManager, LanguageManager lang) {

        var ptNode = Commands.literal("playtime")
                .executes(context -> executeSelf(context, playtimeManager, lang));

        ptNode.then(Commands.literal("show")
                .then(Commands.literal("player")
                        .then(Commands.argument("target", StringArgumentType.word())
                                .suggests((context, builder) -> {
                                    String input = builder.getRemaining().toLowerCase();
                                    for (UUID uuid : playtimeManager.getAllPlaytimes().keySet()) {
                                        String name = playtimeManager.getName(uuid);
                                        if (name != null && !name.equals("Desconocido") && name.toLowerCase().startsWith(input)) {
                                            builder.suggest(name);
                                        }
                                    }
                                    return builder.buildFuture();
                                })
                                .executes(context -> executeShowPlayer(context, playtimeManager, lang))
                        )
                )
                .then(Commands.literal("team")
                        .then(Commands.argument("teamName", StringArgumentType.word())
                                .suggests((context, builder) -> {
                                    String input = builder.getRemaining().toLowerCase();
                                    for (PiolaTeam team : TeamManager.getAllTeams()) {
                                        if (team.getName().startsWith(input)) builder.suggest(team.getName());
                                    }
                                    return builder.buildFuture();
                                })
                                .executes(context -> executeShowTeam(context, lang))
                        )
                )
        );

        ptNode.then(Commands.literal("top").executes(context -> executeTop(context, playtimeManager, lang)));
        ptNode.then(Commands.literal("topteams").executes(context -> executeTopTeams(context, lang)));

        var adminNode = Commands.literal("admin")
                .requires(source -> source.getSender().hasPermission("permapiola.admin.playtime"));

        adminNode.then(Commands.literal("add")
                .requires(source -> source.getSender().hasPermission("permapiola.admin.playtime.add"))
                .then(Commands.argument("target", StringArgumentType.word())
                        .then(Commands.argument("amount", StringArgumentType.word())
                                .executes(context -> executeAdmin(context, "add", playtimeManager, lang))
                        )
                )
        );

        adminNode.then(Commands.literal("remove")
                .requires(source -> source.getSender().hasPermission("permapiola.admin.playtime.remove"))
                .then(Commands.argument("target", StringArgumentType.word())
                        .then(Commands.argument("amount", StringArgumentType.word())
                                .executes(context -> executeAdmin(context, "remove", playtimeManager, lang))
                        )
                )
        );

        adminNode.then(Commands.literal("set")
                .requires(source -> source.getSender().hasPermission("permapiola.admin.playtime.set"))
                .then(Commands.argument("target", StringArgumentType.word())
                        .then(Commands.argument("amount", StringArgumentType.word())
                                .executes(context -> executeAdmin(context, "set", playtimeManager, lang))
                        )
                )
        );

        adminNode.then(Commands.literal("reset")
                .requires(source -> source.getSender().hasPermission("permapiola.admin.playtime.reset"))
                .then(Commands.argument("target", StringArgumentType.word())
                        .executes(context -> executeAdmin(context, "reset", playtimeManager, lang))
                )
        );

        ptNode.then(adminNode);

        for (String alias : Arrays.asList("playtime", "pt")) {
            commands.register(Commands.literal(alias).redirect(ptNode.build()).build(), "Manage and view playtimes");
        }
    }

    private static String getFormattedPlaytime(CommandSender sender, long seconds, LanguageManager lang) {
        return TimeUtils.formatTime(seconds,
                lang.getMsg(sender, "playtime.units.week"),
                lang.getMsg(sender, "playtime.units.day"),
                lang.getMsg(sender, "playtime.units.hour"),
                lang.getMsg(sender, "playtime.units.minute"),
                lang.getMsg(sender, "playtime.units.second"));
    }

    private static void sendFramedMessage(CommandSender sender, String content, LanguageManager lang) {
        if (content == null || content.isEmpty()) return;
        content = content.replaceAll("[\r\n]+$", "");
        String line = lang.getMsg(sender, "commands.generic.line");
        sender.sendMessage(ColorUtils.format(line));
        for (String splitLine : content.split("\n")) {
            sender.sendMessage(ColorUtils.format(splitLine));
        }
        sender.sendMessage(ColorUtils.format(line));
    }

    private static int getPlayerPosition(UUID targetUUID, PlaytimeManager playtimeManager) {
        Map<UUID, Long> allTimes = playtimeManager.getAllPlaytimes();
        if (allTimes.getOrDefault(targetUUID, 0L) <= 0) return -1;

        List<Map.Entry<UUID, Long>> sortedList = new ArrayList<>(allTimes.entrySet());
        sortedList.sort((a, b) -> b.getValue().compareTo(a.getValue()));

        int position = 1;
        for (Map.Entry<UUID, Long> entry : sortedList) {
            if (entry.getValue() <= 0) continue;
            if (entry.getKey().equals(targetUUID)) return position;
            position++;
        }
        return -1;
    }

    private static int getTeamPosition(UUID teamId) {
        List<PiolaTeam> sortedTeams = new ArrayList<>(TeamManager.getAllTeams());
        if (sortedTeams.isEmpty()) return -1;

        sortedTeams.sort((a, b) -> Long.compare(b.getTotalPlaytime(), a.getTotalPlaytime()));

        int position = 1;
        for (PiolaTeam t : sortedTeams) {
            if (t.getTotalPlaytime() <= 0) continue;
            if (t.getTeamId().equals(teamId)) return position;
            position++;
        }
        return -1;
    }

    private static int executeSelf(CommandContext<CommandSourceStack> context, PlaytimeManager playtimeManager, LanguageManager lang) {
        CommandSender sender = context.getSource().getSender();
        if (!(sender instanceof Player)) return Command.SINGLE_SUCCESS;
        Player player = (Player) sender;

        long time = playtimeManager.getPlaytime(player.getUniqueId());
        int posInt = getPlayerPosition(player.getUniqueId(), playtimeManager);
        String posStr = posInt > 0 ? String.valueOf(posInt) : "N/A";

        String msg = lang.getMsg(sender, "playtime.self")
                .replace("%player%", player.getName())
                .replace("%time%", getFormattedPlaytime(sender, time, lang))
                .replace("%pos%", posStr);
        sendFramedMessage(sender, msg, lang);
        return Command.SINGLE_SUCCESS;
    }

    private static int executeShowPlayer(CommandContext<CommandSourceStack> context, PlaytimeManager playtimeManager, LanguageManager lang) {
        CommandSender sender = context.getSource().getSender();
        String targetName = context.getArgument("target", String.class);

        UUID targetUUID = playtimeManager.getUUIDByName(targetName);
        long time = 0;
        String finalTargetName = targetName;
        boolean isSelf = false;

        if (targetUUID != null) {
            time = playtimeManager.getPlaytime(targetUUID);
            finalTargetName = playtimeManager.getName(targetUUID);
            if (sender instanceof Player) isSelf = ((Player) sender).getUniqueId().equals(targetUUID);
        } else {
            @SuppressWarnings("deprecation")
            OfflinePlayer target = Bukkit.getOfflinePlayer(targetName);
            targetUUID = target.getUniqueId();
            time = playtimeManager.getPlaytime(targetUUID);
            finalTargetName = target.getName() != null ? target.getName() : targetName;
            if (sender instanceof Player) isSelf = ((Player) sender).getUniqueId().equals(targetUUID);
        }

        int posInt = getPlayerPosition(targetUUID, playtimeManager);
        String posStr = posInt > 0 ? String.valueOf(posInt) : "N/A";
        String path = isSelf ? "playtime.self" : "playtime.other-player";

        String msg = lang.getMsg(sender, path)
                .replace("%player%", finalTargetName)
                .replace("%time%", getFormattedPlaytime(sender, time, lang))
                .replace("%pos%", posStr);
        sendFramedMessage(sender, msg, lang);
        return Command.SINGLE_SUCCESS;
    }

    private static int executeShowTeam(CommandContext<CommandSourceStack> context, LanguageManager lang) {
        CommandSender sender = context.getSource().getSender();
        String targetName = context.getArgument("teamName", String.class);

        PiolaTeam team = TeamManager.getTeamByName(targetName);
        if (team == null) {
            sendFramedMessage(sender, lang.getMsg(sender, "playtime.team-not-found"), lang);
            return Command.SINGLE_SUCCESS;
        }

        int posInt = getTeamPosition(team.getTeamId());
        String posStr = posInt > 0 ? String.valueOf(posInt) : "N/A";

        String msg = lang.getMsg(sender, "playtime.other-team")
                .replace("%team%", team.getDisplayName())
                .replace("%time%", getFormattedPlaytime(sender, team.getTotalPlaytime(), lang))
                .replace("%pos%", posStr);
        sendFramedMessage(sender, msg, lang);
        return Command.SINGLE_SUCCESS;
    }

    private static int executeTop(CommandContext<CommandSourceStack> context, PlaytimeManager playtimeManager, LanguageManager lang) {
        CommandSender sender = context.getSource().getSender();
        Map<UUID, Long> allTimes = playtimeManager.getAllPlaytimes();
        List<Map.Entry<UUID, Long>> sortedList = new ArrayList<>(allTimes.entrySet());
        sortedList.sort((a, b) -> b.getValue().compareTo(a.getValue()));

        StringBuilder sb = new StringBuilder();
        sb.append(lang.getMsg(sender, "playtime.top-header")).append("\n");

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

            String prefix = LuckPermsUtils.getPrefixForOffline(name);
            String formattedName = (prefix != null ? prefix : "") + name;

            String line = lang.getMsg(sender, "playtime.top-format")
                    .replace("%pos%", String.valueOf(position))
                    .replace("%name%", formattedName)
                    .replace("%time%", getFormattedPlaytime(sender, entry.getValue(), lang));
            sb.append(line).append("\n");
            position++;
        }
        sendFramedMessage(sender, sb.toString(), lang);
        return Command.SINGLE_SUCCESS;
    }

    private static int executeTopTeams(CommandContext<CommandSourceStack> context, LanguageManager lang) {
        CommandSender sender = context.getSource().getSender();
        List<PiolaTeam> sortedTeams = new ArrayList<>(TeamManager.getAllTeams());
        sortedTeams.sort((a, b) -> Long.compare(b.getTotalPlaytime(), a.getTotalPlaytime()));

        StringBuilder sb = new StringBuilder();
        sb.append(lang.getMsg(sender, "playtime.topteams-header")).append("\n");

        int position = 1;
        for (PiolaTeam team : sortedTeams) {
            if (position > 10) break;
            if (team.getTotalPlaytime() <= 0) continue;

            String line = lang.getMsg(sender, "playtime.topteams-format")
                    .replace("%pos%", String.valueOf(position))
                    .replace("%team%", team.getDisplayName())
                    .replace("%time%", getFormattedPlaytime(sender, team.getTotalPlaytime(), lang));
            sb.append(line).append("\n");
            position++;
        }
        sendFramedMessage(sender, sb.toString(), lang);
        return Command.SINGLE_SUCCESS;
    }

    private static int executeAdmin(CommandContext<CommandSourceStack> context, String action, PlaytimeManager playtimeManager, LanguageManager lang) {
        CommandSender sender = context.getSource().getSender();
        String targetName = context.getArgument("target", String.class);

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

        if (!action.equals("reset")) {
            amount = TimeUtils.parseTimeString(context.getArgument("amount", String.class));
            if (amount <= 0) {
                sendFramedMessage(sender, lang.getMsg(sender, "playtime.admin-invalid-time"), lang);
                return Command.SINGLE_SUCCESS;
            }
            formattedAmount = getFormattedPlaytime(sender, amount, lang);
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

        String msgSender = lang.getMsg(sender, senderMsgPath)
                .replace("%player%", finalTargetName)
                .replace("%amount%", formattedAmount)
                .replace("%time%", getFormattedPlaytime(sender, newTime, lang));
        sendFramedMessage(sender, msgSender, lang);

        Player pTarget = Bukkit.getPlayer(targetUUID);
        if (pTarget != null && pTarget.isOnline() && !pTarget.equals(sender)) {
            String msgTarget = lang.getMsg(pTarget, targetMsgPath)
                    .replace("%amount%", formattedAmount)
                    .replace("%time%", getFormattedPlaytime(pTarget, newTime, lang))
                    .replace("%admin%", adminName);
            sendFramedMessage(pTarget, msgTarget, lang);
        }
        return Command.SINGLE_SUCCESS;
    }
}