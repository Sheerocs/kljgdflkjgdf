package pp.sheero.permapiola.totem;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.IntegerArgumentType;
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

import java.util.*;

public class TotemCommand {

    private static final Map<String, String> commonSounds = new HashMap<>();
    private static final Map<String, String> donorSounds = new HashMap<>();

    public static void register(Commands commands, TotemManager totemManager, LanguageManager lang) {

        commonSounds.put("totem", "ITEM_TOTEM_USE");
        donorSounds.put("cat_hurt", "ENTITY_CAT_HURT");
        donorSounds.put("blaze_hurt", "ENTITY_BLAZE_HURT");
        donorSounds.put("creacking_attack", "ENTITY_CREAKING_ATTACK");

        var totemNode = Commands.literal("totem")
                .executes(context -> executeSelf(context, totemManager, lang));

        totemNode.then(Commands.literal("show")
                .then(Commands.literal("player")
                        .then(Commands.argument("target", StringArgumentType.word())
                                .suggests((context, builder) -> {
                                    String input = builder.getRemaining().toLowerCase();
                                    for (UUID uuid : totemManager.getAllProfiles().keySet()) {
                                        String name = totemManager.getName(uuid);
                                        if (name != null && !name.equals("Desconocido") && name.toLowerCase().startsWith(input)) {
                                            builder.suggest(name);
                                        }
                                    }
                                    return builder.buildFuture();
                                })
                                .executes(context -> executeShowPlayer(context, totemManager, lang))
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

        totemNode.then(Commands.literal("top").executes(context -> executeTop(context, totemManager, lang)));
        totemNode.then(Commands.literal("topteams").executes(context -> executeTopTeams(context, lang)));

        totemNode.then(Commands.literal("sound")
                .then(Commands.literal("mode")
                        .then(Commands.argument("modeType", StringArgumentType.word())
                                .suggests((context, builder) -> {
                                    String input = builder.getRemaining().toLowerCase();
                                    List.of("all", "team", "off").forEach(opt -> {
                                        if (opt.startsWith(input)) builder.suggest(opt);
                                    });
                                    return builder.buildFuture();
                                })
                                .executes(context -> executeSoundMode(context, totemManager, lang))
                        )
                )
                .then(Commands.literal("type")
                        .then(Commands.argument("soundType", StringArgumentType.word())
                                .suggests((context, builder) -> {
                                    String input = builder.getRemaining().toLowerCase();
                                    commonSounds.keySet().forEach(opt -> {
                                        if (opt.startsWith(input)) builder.suggest(opt);
                                    });
                                    if (context.getSource().getSender().hasPermission("permapiola.donor.totem")) {
                                        donorSounds.keySet().forEach(opt -> {
                                            if (opt.startsWith(input)) builder.suggest(opt);
                                        });
                                    }
                                    return builder.buildFuture();
                                })
                                .executes(context -> executeSoundType(context, totemManager, lang))
                        )
                )
        );

        var adminNode = Commands.literal("admin")
                .requires(source -> source.getSender().hasPermission("permapiola.admin.totem"));

        adminNode.then(Commands.literal("add")
                .requires(source -> source.getSender().hasPermission("permapiola.admin.totem.add"))
                .then(Commands.argument("target", StringArgumentType.word())
                        .then(Commands.argument("amount", IntegerArgumentType.integer(1))
                                .executes(context -> executeAdmin(context, "add", totemManager, lang))
                        )
                )
        );

        adminNode.then(Commands.literal("remove")
                .requires(source -> source.getSender().hasPermission("permapiola.admin.totem.remove"))
                .then(Commands.argument("target", StringArgumentType.word())
                        .then(Commands.argument("amount", IntegerArgumentType.integer(1))
                                .executes(context -> executeAdmin(context, "remove", totemManager, lang))
                        )
                )
        );

        adminNode.then(Commands.literal("set")
                .requires(source -> source.getSender().hasPermission("permapiola.admin.totem.set"))
                .then(Commands.argument("target", StringArgumentType.word())
                        .then(Commands.argument("amount", IntegerArgumentType.integer(0))
                                .executes(context -> executeAdmin(context, "set", totemManager, lang))
                        )
                )
        );

        adminNode.then(Commands.literal("reset")
                .requires(source -> source.getSender().hasPermission("permapiola.admin.totem.reset"))
                .then(Commands.argument("target", StringArgumentType.word())
                        .executes(context -> executeAdmin(context, "reset", totemManager, lang))
                )
        );

        totemNode.then(adminNode);

        for (String alias : Arrays.asList("totem", "totems")) {
            commands.register(Commands.literal(alias).redirect(totemNode.build()).build(), "Manage and view totems");
        }
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

    private static int getPlayerPosition(UUID targetUUID, TotemManager totemManager) {
        Map<UUID, TotemManager.TotemProfile> allProfiles = totemManager.getAllProfiles();
        if (allProfiles.getOrDefault(targetUUID, new TotemManager.TotemProfile()).count <= 0) return -1;

        List<Map.Entry<UUID, TotemManager.TotemProfile>> sortedList = new ArrayList<>(allProfiles.entrySet());
        sortedList.sort((a, b) -> Integer.compare(b.getValue().count, a.getValue().count));

        int position = 1;
        for (Map.Entry<UUID, TotemManager.TotemProfile> entry : sortedList) {
            if (entry.getValue().count <= 0) continue;
            if (entry.getKey().equals(targetUUID)) return position;
            position++;
        }
        return -1;
    }

    private static int getTeamPosition(UUID teamId) {
        List<PiolaTeam> sortedTeams = new ArrayList<>(TeamManager.getAllTeams());
        if (sortedTeams.isEmpty()) return -1;

        sortedTeams.sort((a, b) -> Integer.compare(b.getTotalTotems(), a.getTotalTotems()));

        int position = 1;
        for (PiolaTeam t : sortedTeams) {
            if (t.getTotalTotems() <= 0) continue;
            if (t.getTeamId().equals(teamId)) return position;
            position++;
        }
        return -1;
    }

    private static int executeSelf(CommandContext<CommandSourceStack> context, TotemManager totemManager, LanguageManager lang) {
        CommandSender sender = context.getSource().getSender();
        if (!(sender instanceof Player)) return Command.SINGLE_SUCCESS;
        Player player = (Player) sender;

        int count = totemManager.getTotems(player.getUniqueId());
        int posInt = getPlayerPosition(player.getUniqueId(), totemManager);
        String posStr = posInt > 0 ? String.valueOf(posInt) : "N/A";

        String msg = lang.getMsg(sender, "totems.self")
                .replace("%player%", player.getName())
                .replace("%totems%", String.valueOf(count))
                .replace("%pos%", posStr);
        sendFramedMessage(sender, msg, lang);
        return Command.SINGLE_SUCCESS;
    }

    private static int executeShowPlayer(CommandContext<CommandSourceStack> context, TotemManager totemManager, LanguageManager lang) {
        CommandSender sender = context.getSource().getSender();
        String targetName = context.getArgument("target", String.class);

        UUID targetUUID = totemManager.getUUIDByName(targetName);
        int count = 0;
        String finalTargetName = targetName;
        boolean isSelf = false;

        if (targetUUID != null) {
            count = totemManager.getTotems(targetUUID);
            finalTargetName = totemManager.getName(targetUUID);
            if (sender instanceof Player) isSelf = ((Player) sender).getUniqueId().equals(targetUUID);
        } else {
            @SuppressWarnings("deprecation")
            OfflinePlayer target = Bukkit.getOfflinePlayer(targetName);
            targetUUID = target.getUniqueId();
            count = totemManager.getTotems(targetUUID);
            finalTargetName = target.getName() != null ? target.getName() : targetName;
            if (sender instanceof Player) isSelf = ((Player) sender).getUniqueId().equals(targetUUID);
        }

        int posInt = getPlayerPosition(targetUUID, totemManager);
        String posStr = posInt > 0 ? String.valueOf(posInt) : "N/A";
        String path = isSelf ? "totems.self" : "totems.other-player";

        String msg = lang.getMsg(sender, path)
                .replace("%player%", finalTargetName)
                .replace("%totems%", String.valueOf(count))
                .replace("%pos%", posStr);
        sendFramedMessage(sender, msg, lang);
        return Command.SINGLE_SUCCESS;
    }

    private static int executeShowTeam(CommandContext<CommandSourceStack> context, LanguageManager lang) {
        CommandSender sender = context.getSource().getSender();
        String targetName = context.getArgument("teamName", String.class);

        PiolaTeam team = TeamManager.getTeamByName(targetName);
        if (team == null) {
            sendFramedMessage(sender, lang.getMsg(sender, "totems.team-not-found"), lang);
            return Command.SINGLE_SUCCESS;
        }

        int posInt = getTeamPosition(team.getTeamId());
        String posStr = posInt > 0 ? String.valueOf(posInt) : "N/A";

        String msg = lang.getMsg(sender, "totems.other-team")
                .replace("%team%", team.getDisplayName())
                .replace("%totems%", String.valueOf(team.getTotalTotems()))
                .replace("%pos%", posStr);
        sendFramedMessage(sender, msg, lang);
        return Command.SINGLE_SUCCESS;
    }

    private static int executeTop(CommandContext<CommandSourceStack> context, TotemManager totemManager, LanguageManager lang) {
        CommandSender sender = context.getSource().getSender();
        Map<UUID, TotemManager.TotemProfile> allProfiles = totemManager.getAllProfiles();
        List<Map.Entry<UUID, TotemManager.TotemProfile>> sortedList = new ArrayList<>(allProfiles.entrySet());
        sortedList.sort((a, b) -> Integer.compare(b.getValue().count, a.getValue().count));

        StringBuilder sb = new StringBuilder();
        sb.append(lang.getMsg(sender, "totems.top-header")).append("\n");

        int position = 1;
        for (Map.Entry<UUID, TotemManager.TotemProfile> entry : sortedList) {
            if (position > 10) break;
            if (entry.getValue().count <= 0) continue;

            String name = entry.getValue().name;
            if (name.equals("Desconocido")) {
                OfflinePlayer op = Bukkit.getOfflinePlayer(entry.getKey());
                if (op.getName() != null) {
                    name = op.getName();
                    totemManager.updateNameCache(entry.getKey(), name);
                }
            }

            String prefix = LuckPermsUtils.getPrefixForOffline(name);
            String formattedName = (prefix != null ? prefix : "") + name;

            String line = lang.getMsg(sender, "totems.top-format")
                    .replace("%pos%", String.valueOf(position))
                    .replace("%name%", formattedName)
                    .replace("%totems%", String.valueOf(entry.getValue().count));
            sb.append(line).append("\n");
            position++;
        }
        sendFramedMessage(sender, sb.toString(), lang);
        return Command.SINGLE_SUCCESS;
    }

    private static int executeTopTeams(CommandContext<CommandSourceStack> context, LanguageManager lang) {
        CommandSender sender = context.getSource().getSender();
        List<PiolaTeam> sortedTeams = new ArrayList<>(TeamManager.getAllTeams());
        sortedTeams.sort((a, b) -> Integer.compare(b.getTotalTotems(), a.getTotalTotems()));

        StringBuilder sb = new StringBuilder();
        sb.append(lang.getMsg(sender, "totems.topteams-header")).append("\n");

        int position = 1;
        for (PiolaTeam team : sortedTeams) {
            if (position > 10) break;
            if (team.getTotalTotems() <= 0) continue;

            String line = lang.getMsg(sender, "totems.topteams-format")
                    .replace("%pos%", String.valueOf(position))
                    .replace("%team%", team.getDisplayName())
                    .replace("%totems%", String.valueOf(team.getTotalTotems()));
            sb.append(line).append("\n");
            position++;
        }
        sendFramedMessage(sender, sb.toString(), lang);
        return Command.SINGLE_SUCCESS;
    }

    private static int executeSoundMode(CommandContext<CommandSourceStack> context, TotemManager totemManager, LanguageManager lang) {
        CommandSender sender = context.getSource().getSender();
        if (!(sender instanceof Player)) return Command.SINGLE_SUCCESS;
        Player player = (Player) sender;

        String value = context.getArgument("modeType", String.class).toLowerCase();
        TotemManager.TotemProfile profile = totemManager.getProfile(player.getUniqueId());

        if (value.equals("all") || value.equals("team") || value.equals("off")) {
            profile.soundMode = value.toUpperCase();
            sendFramedMessage(player, lang.getMsg(player, "totems.sound-mode-success").replace("%mode%", profile.soundMode), lang);
        } else {
            sendFramedMessage(player, lang.getMsg(player, "totems.sound-invalid-mode"), lang);
        }
        return Command.SINGLE_SUCCESS;
    }

    private static int executeSoundType(CommandContext<CommandSourceStack> context, TotemManager totemManager, LanguageManager lang) {
        CommandSender sender = context.getSource().getSender();
        if (!(sender instanceof Player)) return Command.SINGLE_SUCCESS;
        Player player = (Player) sender;

        String value = context.getArgument("soundType", String.class).toLowerCase();
        TotemManager.TotemProfile profile = totemManager.getProfile(player.getUniqueId());

        String soundToSet = null;

        if (commonSounds.containsKey(value)) {
            soundToSet = commonSounds.get(value);
        } else if (donorSounds.containsKey(value)) {
            if (player.hasPermission("permapiola.donor.totem")) {
                soundToSet = donorSounds.get(value);
            } else {
                sendFramedMessage(player, lang.getMsg(player, "totems.sound-type-noperm"), lang);
                return Command.SINGLE_SUCCESS;
            }
        }

        if (soundToSet != null) {
            profile.soundType = soundToSet;
            sendFramedMessage(player, lang.getMsg(player, "totems.sound-type-success").replace("%type%", value.toUpperCase()), lang);
        } else {
            sendFramedMessage(player, lang.getMsg(player, "totems.sound-invalid-type"), lang);
        }
        return Command.SINGLE_SUCCESS;
    }

    private static int executeAdmin(CommandContext<CommandSourceStack> context, String action, TotemManager totemManager, LanguageManager lang) {
        CommandSender sender = context.getSource().getSender();
        String targetName = context.getArgument("target", String.class);

        UUID targetUUID = totemManager.getUUIDByName(targetName);
        String finalTargetName = targetName;

        if (targetUUID != null) {
            finalTargetName = totemManager.getName(targetUUID);
        } else {
            @SuppressWarnings("deprecation")
            OfflinePlayer op = Bukkit.getOfflinePlayer(targetName);
            targetUUID = op.getUniqueId();
            finalTargetName = op.getName() != null ? op.getName() : targetName;
        }

        int amount = 0;
        if (!action.equals("reset")) {
            amount = context.getArgument("amount", Integer.class);
        }

        int currentTotems = totemManager.getTotems(targetUUID);
        String senderMsgPath = "";
        String targetMsgPath = "";

        switch (action) {
            case "add":
                totemManager.setTotems(targetUUID, currentTotems + amount);
                senderMsgPath = "totems.admin-add-sender";
                targetMsgPath = "totems.admin-add-target";
                break;
            case "remove":
                totemManager.setTotems(targetUUID, Math.max(0, currentTotems - amount));
                senderMsgPath = "totems.admin-remove-sender";
                targetMsgPath = "totems.admin-remove-target";
                break;
            case "set":
                totemManager.setTotems(targetUUID, amount);
                senderMsgPath = "totems.admin-set-sender";
                targetMsgPath = "totems.admin-set-target";
                break;
            case "reset":
                totemManager.setTotems(targetUUID, 0);
                senderMsgPath = "totems.admin-reset-sender";
                targetMsgPath = "totems.admin-reset-target";
                break;
        }

        if (totemManager.getName(targetUUID).equals("Desconocido")) {
            totemManager.setTotems(targetUUID, totemManager.getTotems(targetUUID));
        }

        int newCount = totemManager.getTotems(targetUUID);
        String adminName = sender.getName();

        String msgSender = lang.getMsg(sender, senderMsgPath)
                .replace("%player%", finalTargetName)
                .replace("%amount%", String.valueOf(amount))
                .replace("%totems%", String.valueOf(newCount));
        sendFramedMessage(sender, msgSender, lang);

        Player pTarget = Bukkit.getPlayer(targetUUID);
        if (pTarget != null && pTarget.isOnline() && !pTarget.equals(sender)) {
            String msgTarget = lang.getMsg(pTarget, targetMsgPath)
                    .replace("%amount%", String.valueOf(amount))
                    .replace("%totems%", String.valueOf(newCount))
                    .replace("%admin%", adminName);
            sendFramedMessage(pTarget, msgTarget, lang);
        }
        return Command.SINGLE_SUCCESS;
    }
}