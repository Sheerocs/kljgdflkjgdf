package pp.sheero.permapiola.teams;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import pp.sheero.permapiola.PermaPiola;
import pp.sheero.permapiola.core.LanguageManager;
import pp.sheero.permapiola.hurricane.DeathStateManager;
import pp.sheero.permapiola.utils.ColorUtils;
import pp.sheero.permapiola.utils.LuckPermsUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;

public class ReTeamCommand {

    private static final Pattern VALID_CHARS = Pattern.compile("^[a-zA-Z0-9_ ]+$");

    public static void register(Commands commands, PermaPiola plugin, LanguageManager lang) {
        var reteamNode = Commands.literal("reteam")
                .executes(context -> executeHelp(context, lang));

        reteamNode.then(Commands.literal("help").executes(context -> executeHelp(context, lang)));

        // ========================================== #
        //               ZONA USUARIO                 #
        // ========================================== #

        reteamNode.then(Commands.literal("invite")
                .then(Commands.argument("targetTeam", StringArgumentType.word())
                        .suggests((context, builder) -> {
                            if (!(context.getSource().getExecutor() instanceof Player)) return builder.buildFuture();
                            Player p = (Player) context.getSource().getExecutor();
                            PiolaTeam myTeam = TeamManager.getTeam(p);

                            if (myTeam != null && myTeam.isLeader(p.getUniqueId()) && !ReTeamManager.hasReTeam(p)) {
                                String input = builder.getRemaining().toLowerCase();
                                for (PiolaTeam t : TeamManager.getAllTeams()) {
                                    if (t.getTeamId().equals(myTeam.getTeamId())) continue;

                                    Player enemyLeader = Bukkit.getPlayer(t.getLeader());
                                    if (enemyLeader != null && !ReTeamManager.hasReTeam(enemyLeader)) {
                                        if (t.getName().startsWith(input)) {
                                            builder.suggest(t.getName());
                                        }
                                    }
                                }
                            }
                            return builder.buildFuture();
                        })
                        .executes(context -> executeInvite(context, plugin, lang))
                )
        );

        reteamNode.then(Commands.literal("accept")
                .then(Commands.argument("targetTeam", StringArgumentType.word())
                        .suggests((context, builder) -> {
                            if (!(context.getSource().getExecutor() instanceof Player)) return builder.buildFuture();
                            Player p = (Player) context.getSource().getExecutor();
                            String input = builder.getRemaining().toLowerCase();

                            for (PiolaTeam t : TeamManager.getAllTeams()) {
                                Player enemyLeader = Bukkit.getPlayer(t.getLeader());
                                if (enemyLeader != null && ReTeamManager.hasInvite(enemyLeader, TeamManager.getTeam(p).getName())) {
                                    if (t.getName().startsWith(input)) builder.suggest(t.getName());
                                }
                            }
                            return builder.buildFuture();
                        })
                        .executes(context -> executeAccept(context, plugin, lang))
                )
        );

        reteamNode.then(Commands.literal("info").executes(context -> executeInfo(context, plugin, lang)));

        reteamNode.then(Commands.literal("tag")
                .then(Commands.argument("newTag", StringArgumentType.greedyString())
                        .executes(context -> executeTag(context, plugin, lang))
                )
        );

        reteamNode.then(Commands.literal("rename")
                .then(Commands.argument("newName", StringArgumentType.greedyString())
                        .executes(context -> executeRename(context, plugin, lang))
                )
        );

        reteamNode.then(Commands.literal("transfer")
                .then(Commands.argument("target", StringArgumentType.word())
                        .suggests((context, builder) -> {
                            if (!(context.getSource().getExecutor() instanceof Player)) return builder.buildFuture();
                            Player p = (Player) context.getSource().getExecutor();
                            PiolaReTeam reteam = ReTeamManager.getReTeam(p);

                            if (reteam != null && reteam.isLeader(p.getUniqueId())) {
                                String input = builder.getRemaining().toLowerCase();
                                for (UUID memberId : reteam.getMembers()) {
                                    if (memberId.equals(p.getUniqueId())) continue;

                                    Player memberPlayer = Bukkit.getPlayer(memberId);
                                    String name = memberPlayer != null ? memberPlayer.getName() : Bukkit.getOfflinePlayer(memberId).getName();

                                    if (name != null && name.toLowerCase().startsWith(input)) {
                                        builder.suggest(name);
                                    }
                                }
                            }
                            return builder.buildFuture();
                        })
                        .executes(context -> executeTransfer(context, plugin, lang))
                )
        );

        // ========================================== #
        //               ZONA STAFF                   #
        // ========================================== #

        var systemNode = Commands.literal("system")
                .requires(source -> source.getSender().hasPermission("permapiola.admin.team.system"))
                .then(Commands.literal("on").executes(context -> executeSystem(context, "on", plugin, lang)))
                .then(Commands.literal("off").executes(context -> executeSystem(context, "off", plugin, lang)));
        reteamNode.then(systemNode);

        reteamNode.then(Commands.literal("list")
                .requires(source -> source.getSender().hasPermission("permapiola.admin.team.list"))
                .executes(context -> executeList(context, 1, plugin, lang))
                .then(Commands.argument("page", IntegerArgumentType.integer(1))
                        .executes(context -> executeList(context, context.getArgument("page", Integer.class), plugin, lang))
                )
        );

        reteamNode.then(Commands.literal("disband")
                .requires(source -> source.getSender().hasPermission("permapiola.admin.team.delete"))
                .then(Commands.argument("teamName", StringArgumentType.greedyString())
                        .suggests((context, builder) -> {
                            for (PiolaReTeam t : ReTeamManager.getAllReTeams()) {
                                if (t.getName().startsWith(builder.getRemaining().toLowerCase())) {
                                    builder.suggest(t.getName());
                                }
                            }
                            return builder.buildFuture();
                        })
                        .executes(context -> executeDisband(context, plugin, lang))
                )
        );

        commands.register(reteamNode.build(), "Sistema de Alianzas y Reteam de PermaPiola");
    }

    // ========================================================================
    //                         MÉTODOS UTILITARIOS
    // ========================================================================

    private static void sendFramedMessage(CommandSender sender, String rawMessage, LanguageManager lang) {
        String line = lang.getMsg(sender, "commands.generic.line");
        sender.sendMessage(ColorUtils.format(line));
        for (String splitLine : rawMessage.split("\n")) {
            sender.sendMessage(ColorUtils.format(splitLine));
        }
        sender.sendMessage(ColorUtils.format(line));
    }

    private static Player getPlayerOrError(CommandContext<CommandSourceStack> context) {
        if (!(context.getSource().getExecutor() instanceof Player)) return null;
        return (Player) context.getSource().getExecutor();
    }

    private static String checkRestrictedColors(Player player, String text) {
        boolean isStaff = player.hasPermission("permapiola.admin") || player.hasPermission("permapiola.staff");
        if (isStaff) return null;

        boolean isGold = false;
        boolean isDiamond = false;
        boolean isNetherite = false;

        try {
            net.luckperms.api.LuckPerms api = net.luckperms.api.LuckPermsProvider.get();
            net.luckperms.api.model.user.User user = api.getUserManager().getUser(player.getUniqueId());
            if (user != null) {
                isGold = user.getInheritedGroups(user.getQueryOptions()).stream().anyMatch(g -> g.getName().equalsIgnoreCase("gold"));
                isDiamond = user.getInheritedGroups(user.getQueryOptions()).stream().anyMatch(g -> g.getName().equalsIgnoreCase("diamond"));
                isNetherite = user.getInheritedGroups(user.getQueryOptions()).stream().anyMatch(g -> g.getName().equalsIgnoreCase("netherite"));
            }
        } catch (Exception ignored) {}

        List<Character> restrictedColors = new ArrayList<>(Arrays.asList('r', 'l', 'o', 'n', 'm', 'k', '4', 'c', '5', 'b', '6'));
        String langPath = "teams.create.restricted-colors.default";

        if (isNetherite) {
            langPath = "teams.create.restricted-colors.netherite";
        } else if (isDiamond) {
            langPath = "teams.create.restricted-colors.diamond";
        } else if (isGold) {
            langPath = "teams.create.restricted-colors.gold";
        }

        if (isGold) restrictedColors.remove((Character) '6');
        if (isDiamond) restrictedColors.remove((Character) 'b');
        if (isNetherite) restrictedColors.remove((Character) '5');

        for (int i = 0; i < text.length() - 1; i++) {
            if (text.charAt(i) == '&') {
                char colorChar = Character.toLowerCase(text.charAt(i + 1));
                if (restrictedColors.contains(colorChar)) return langPath;
            }
        }
        return null;
    }

    private static String formatPlayerForList(UUID memberUuid, CommandSender viewer, LanguageManager lang) {
        Player memberObj = Bukkit.getPlayer(memberUuid);
        String playerName;
        if (memberObj != null) {
            playerName = memberObj.getName();
        } else {
            @SuppressWarnings("deprecation")
            org.bukkit.OfflinePlayer op = Bukkit.getOfflinePlayer(memberUuid);
            playerName = op.getName() != null ? op.getName() : lang.getMsg(viewer, "teams.memberlist.unknown-player");
        }

        String icon = DeathStateManager.isDead(memberUuid)
                ? lang.getMsg(viewer, "teams.memberlist.icons.dead")
                : (memberObj != null && memberObj.isOnline() ? lang.getMsg(viewer, "teams.memberlist.icons.online") : lang.getMsg(viewer, "teams.memberlist.icons.offline"));

        String finalPrefix = (memberObj != null) ? LuckPermsUtils.getPrefix(memberObj) : LuckPermsUtils.getPrefixForOffline(playerName);
        if (finalPrefix == null) finalPrefix = "";

        return lang.getMsg(viewer, "teams.memberlist.player-format")
                .replace("%status%", icon)
                .replace("%player_prefix%", finalPrefix)
                .replace("%player%", playerName);
    }

    private static int executeHelp(CommandContext<CommandSourceStack> context, LanguageManager lang) {
        CommandSender sender = context.getSource().getSender();
        List<String> helpLines = new ArrayList<>(lang.getMsgList(sender, "reteam.help.user"));
        if (sender.hasPermission("permapiola.admin")) {
            helpLines.add(" ");
            helpLines.addAll(lang.getMsgList(sender, "reteam.help.staff"));
        }
        helpLines.forEach(line -> sender.sendMessage(ColorUtils.format(line)));
        return Command.SINGLE_SUCCESS;
    }

    private static int executeInvite(CommandContext<CommandSourceStack> context, PermaPiola plugin, LanguageManager lang) {
        Player player = getPlayerOrError(context);
        if (player == null) return Command.SINGLE_SUCCESS;

        if (!ReTeamManager.isReteamSystemEnabled() && !player.hasPermission("permapiola.admin")) {
            sendFramedMessage(player, lang.getMsg(player, "reteam.system-disabled"), lang);
            return Command.SINGLE_SUCCESS;
        }

        PiolaTeam myTeam = TeamManager.getTeam(player);
        if (myTeam == null) { sendFramedMessage(player, lang.getMsg(player, "teams.not-in-team"), lang); return Command.SINGLE_SUCCESS; }
        if (!myTeam.isLeader(player.getUniqueId())) { sendFramedMessage(player, lang.getMsg(player, "teams.not-leader"), lang); return Command.SINGLE_SUCCESS; }
        if (ReTeamManager.hasReTeam(player)) { sendFramedMessage(player, lang.getMsg(player, "reteam.already-in-reteam"), lang); return Command.SINGLE_SUCCESS; }

        String targetTeamName = context.getArgument("targetTeam", String.class);
        PiolaTeam targetTeam = TeamManager.getTeamByName(targetTeamName);

        if (targetTeam == null) { sendFramedMessage(player, lang.getMsg(player, "teams.staff.team-not-found"), lang); return Command.SINGLE_SUCCESS; }
        if (targetTeam.equals(myTeam)) { sendFramedMessage(player, lang.getMsg(player, "reteam.invite.cant-invite-self"), lang); return Command.SINGLE_SUCCESS; }

        Player targetLeader = Bukkit.getPlayer(targetTeam.getLeader());
        if (targetLeader == null || !targetLeader.isOnline()) {
            sendFramedMessage(player, lang.getMsg(player, "reteam.invite.leader-offline"), lang);
            return Command.SINGLE_SUCCESS;
        }

        if (ReTeamManager.hasReTeam(targetLeader)) { sendFramedMessage(player, lang.getMsg(player, "reteam.invite.target-already-reteamed"), lang); return Command.SINGLE_SUCCESS; }
        if (ReTeamManager.hasInvite(player, targetTeam.getName())) { sendFramedMessage(player, lang.getMsg(player, "reteam.invite.already-invited"), lang); return Command.SINGLE_SUCCESS; }

        int myAlive = ReTeamManager.getAliveMembersCount(myTeam);
        int targetAlive = ReTeamManager.getAliveMembersCount(targetTeam);
        int totalSum = myAlive + targetAlive;

        if (totalSum > 6) {
            sendFramedMessage(player, lang.getMsg(player, "reteam.invite.too-many-players").replace("%sum%", String.valueOf(totalSum)), lang);
            return Command.SINGLE_SUCCESS;
        }

        sendFramedMessage(player, lang.getMsg(player, "reteam.invite.sent").replace("%team%", targetTeam.getDisplayName()), lang);

        String lineStr = lang.getMsg(targetLeader, "commands.generic.line");
        targetLeader.sendMessage(ColorUtils.format(lineStr));

        String receivedMsg = lang.getMsg(targetLeader, "reteam.invite.received")
                .replace("%team%", myTeam.getDisplayName())
                .replace("%sum%", String.valueOf(totalSum));

        String hoverRaw = lang.getMsg(targetLeader, "reteam.invite.hover")
                .replace("%team%", myTeam.getName())
                .replace("\\n", "\n");
        Component hoverComp = LegacyComponentSerializer.legacySection().deserialize(ColorUtils.format(hoverRaw));

        String[] lines = receivedMsg.split("\\n");
        for (int i = 0; i < lines.length; i++) {
            Component textComp = LegacyComponentSerializer.legacySection().deserialize(ColorUtils.format(lines[i]));
            if (i == lines.length - 1) {
                textComp = textComp.clickEvent(ClickEvent.runCommand("/reteam accept " + myTeam.getName()))
                        .hoverEvent(net.kyori.adventure.text.event.HoverEvent.showText(hoverComp));
            }
            targetLeader.sendMessage(textComp);
        }
        targetLeader.sendMessage(ColorUtils.format(lineStr));

        int taskId = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (ReTeamManager.hasInvite(player, targetTeam.getName())) {
                ReTeamManager.removeInvite(player, targetTeam.getName());
                if (player.isOnline()) sendFramedMessage(player, lang.getMsg(player, "reteam.invite.expired-sender").replace("%target%", targetTeam.getDisplayName()), lang);
                if (targetLeader.isOnline()) sendFramedMessage(targetLeader, lang.getMsg(targetLeader, "reteam.invite.expired-target").replace("%player%", myTeam.getDisplayName()), lang);
            }
        }, 1200L).getTaskId();

        ReTeamManager.addInvite(player, targetTeam.getName(), taskId);

        return Command.SINGLE_SUCCESS;
    }

    private static int executeAccept(CommandContext<CommandSourceStack> context, PermaPiola plugin, LanguageManager lang) {
        Player player = getPlayerOrError(context);
        if (player == null) return Command.SINGLE_SUCCESS;

        PiolaTeam myTeam = TeamManager.getTeam(player);
        if (myTeam == null) { sendFramedMessage(player, lang.getMsg(player, "teams.not-in-team"), lang); return Command.SINGLE_SUCCESS; }
        if (!myTeam.isLeader(player.getUniqueId())) { sendFramedMessage(player, lang.getMsg(player, "teams.not-leader"), lang); return Command.SINGLE_SUCCESS; }
        if (ReTeamManager.hasReTeam(player)) { sendFramedMessage(player, lang.getMsg(player, "reteam.already-in-reteam"), lang); return Command.SINGLE_SUCCESS; }

        String inviterTeamName = context.getArgument("targetTeam", String.class);
        PiolaTeam inviterTeam = TeamManager.getTeamByName(inviterTeamName);

        if (inviterTeam == null) { sendFramedMessage(player, lang.getMsg(player, "teams.staff.team-not-found"), lang); return Command.SINGLE_SUCCESS; }

        Player inviterLeader = Bukkit.getPlayer(inviterTeam.getLeader());
        if (inviterLeader == null || !ReTeamManager.hasInvite(inviterLeader, myTeam.getName())) {
            sendFramedMessage(player, lang.getMsg(player, "teams.accept.no-invite"), lang);
            return Command.SINGLE_SUCCESS;
        }

        int myAlive = ReTeamManager.getAliveMembersCount(myTeam);
        int inviterAlive = ReTeamManager.getAliveMembersCount(inviterTeam);
        if (myAlive + inviterAlive > 6) {
            sendFramedMessage(player, lang.getMsg(player, "reteam.accept.math-changed"), lang);
            ReTeamManager.removeInvite(inviterLeader, myTeam.getName());
            return Command.SINGLE_SUCCESS;
        }

        ReTeamManager.removeInvite(inviterLeader, myTeam.getName());

        List<String> originalTeamsList = Arrays.asList(inviterTeam.getDisplayName(), myTeam.getDisplayName());
        String temporaryName = "Alianza_" + (int)(Math.random() * 9999);
        String temporaryTag = "&8[&dAlianza&8]";

        PiolaReTeam nuevaAlianza = ReTeamManager.createReTeam(inviterLeader, temporaryName, "Nueva Alianza", temporaryTag, originalTeamsList);

        for (UUID memberId : inviterTeam.getMembers()) {
            if (!DeathStateManager.isDead(memberId)) {
                ReTeamManager.addPlayerToReTeam(memberId, nuevaAlianza);
            }
        }
        for (UUID memberId : myTeam.getMembers()) {
            if (!DeathStateManager.isDead(memberId)) {
                ReTeamManager.addPlayerToReTeam(memberId, nuevaAlianza);
            }
        }

        String successMsg = lang.getMsg(player, "reteam.accept.success");
        String broadcastMsg = lang.getMsg(player, "reteam.accept.broadcast");

        sendFramedMessage(player, successMsg, lang);
        sendFramedMessage(inviterLeader, successMsg, lang);

        for (UUID memberUuid : nuevaAlianza.getMembers()) {
            Player member = Bukkit.getPlayer(memberUuid);
            if (member != null && member.isOnline()) {
                member.sendMessage(ColorUtils.format(lang.getMsg(player, "commands.generic.line")));
                member.sendMessage(ColorUtils.format(broadcastMsg));
                member.sendMessage(ColorUtils.format(lang.getMsg(player, "commands.generic.line")));
                TabManager.updatePlayerTab(member, plugin);
            }
        }

        return Command.SINGLE_SUCCESS;
    }

    private static int executeInfo(CommandContext<CommandSourceStack> context, PermaPiola plugin, LanguageManager lang) {
        Player player = getPlayerOrError(context);
        if (player == null) return Command.SINGLE_SUCCESS;

        PiolaReTeam reteam = ReTeamManager.getReTeam(player);
        if (reteam == null) { sendFramedMessage(player, lang.getMsg(player, "reteam.not-in-reteam"), lang); return Command.SINGLE_SUCCESS; }

        String formattedLeader = "";
        List<String> formattedMembers = new ArrayList<>();

        for (UUID memberUuid : reteam.getMembers()) {
            String pFormat = formatPlayerForList(memberUuid, player, lang);
            if (memberUuid.equals(reteam.getLeader())) formattedLeader = pFormat;
            else formattedMembers.add(pFormat);
        }

        player.sendMessage(ColorUtils.format(lang.getMsg(player, "commands.generic.line")));
        player.sendMessage(ColorUtils.format(lang.getMsg(player, "reteam.info.team-name").replace("%team%", reteam.getDisplayName())));
        player.sendMessage(ColorUtils.format(lang.getMsg(player, "reteam.info.tag").replace("%tag%", reteam.getTag())));

        String origTeams = String.join(" &8- &f", reteam.getOriginalTeams());
        player.sendMessage(ColorUtils.format(lang.getMsg(player, "reteam.info.original-teams").replace("%teams%", origTeams)));
        player.sendMessage(" ");

        player.sendMessage(ColorUtils.format(lang.getMsg(player, "teams.memberlist.leader").replace("%leader_info%", formattedLeader)));

        if (!formattedMembers.isEmpty()) {
            String memPrefix = lang.getMsg(player, "teams.memberlist.members-prefix");
            String separator = lang.getMsg(player, "teams.memberlist.members-separator");
            player.sendMessage(ColorUtils.format(memPrefix + String.join(separator, formattedMembers)));
        }

        player.sendMessage(ColorUtils.format(lang.getMsg(player, "commands.generic.line")));
        return Command.SINGLE_SUCCESS;
    }

    private static int executeTag(CommandContext<CommandSourceStack> context, PermaPiola plugin, LanguageManager lang) {
        Player player = getPlayerOrError(context);
        if (player == null) return Command.SINGLE_SUCCESS;

        PiolaReTeam reteam = ReTeamManager.getReTeam(player);
        if (reteam == null) { sendFramedMessage(player, lang.getMsg(player, "reteam.not-in-reteam"), lang); return Command.SINGLE_SUCCESS; }
        if (!reteam.isLeader(player.getUniqueId())) { sendFramedMessage(player, lang.getMsg(player, "reteam.not-leader"), lang); return Command.SINGLE_SUCCESS; }

        String newTag = context.getArgument("newTag", String.class);
        String cleanTag = ColorUtils.stripColors(newTag);
        boolean isDonator = player.hasPermission("permapiola.donor");

        String restrictedPath = checkRestrictedColors(player, newTag);
        if (restrictedPath != null) { sendFramedMessage(player, lang.getMsg(player, restrictedPath), lang); return Command.SINGLE_SUCCESS; }

        if (!VALID_CHARS.matcher(cleanTag).matches()) { sendFramedMessage(player, lang.getMsg(player, "teams.create.invalid-chars"), lang); return Command.SINGLE_SUCCESS; }

        int maxLength = isDonator ? 10 : 5;
        if (cleanTag.length() > maxLength) { sendFramedMessage(player, lang.getMsg(player, "teams.tag.too-long"), lang); return Command.SINGLE_SUCCESS; }

        if (!isDonator && newTag.contains("#")) { sendFramedMessage(player, lang.getMsg(player, "teams.create.no-hex-colors"), lang); return Command.SINGLE_SUCCESS; }

        String formattedTag = "&8[" + newTag + "&8]";
        reteam.setTag(formattedTag);

        String successMsg = lang.getMsg(player, "reteam.tag.success").replace("%tag%", formattedTag);
        for (UUID memberUuid : reteam.getMembers()) {
            Player member = Bukkit.getPlayer(memberUuid);
            if (member != null && member.isOnline()) {
                TabManager.updatePlayerTab(member, plugin);
                sendFramedMessage(member, successMsg, lang);
            }
        }
        return Command.SINGLE_SUCCESS;
    }

    private static int executeRename(CommandContext<CommandSourceStack> context, PermaPiola plugin, LanguageManager lang) {
        Player player = getPlayerOrError(context);
        if (player == null) return Command.SINGLE_SUCCESS;

        PiolaReTeam reteam = ReTeamManager.getReTeam(player);
        if (reteam == null) { sendFramedMessage(player, lang.getMsg(player, "reteam.not-in-reteam"), lang); return Command.SINGLE_SUCCESS; }
        if (!reteam.isLeader(player.getUniqueId())) { sendFramedMessage(player, lang.getMsg(player, "reteam.not-leader"), lang); return Command.SINGLE_SUCCESS; }

        String newName = context.getArgument("newName", String.class);
        String cleanName = ColorUtils.stripColors(newName);
        boolean isDonator = player.hasPermission("permapiola.donor");

        String restrictedPath = checkRestrictedColors(player, newName);
        if (restrictedPath != null) { sendFramedMessage(player, lang.getMsg(player, restrictedPath), lang); return Command.SINGLE_SUCCESS; }

        if (!VALID_CHARS.matcher(cleanName).matches()) { sendFramedMessage(player, lang.getMsg(player, "teams.create.invalid-chars"), lang); return Command.SINGLE_SUCCESS; }
        if (cleanName.length() < 3) { sendFramedMessage(player, lang.getMsg(player, "teams.create.name-too-short"), lang); return Command.SINGLE_SUCCESS; }

        int maxLength = isDonator ? 16 : 8;
        if (cleanName.length() > maxLength) { sendFramedMessage(player, lang.getMsg(player, isDonator ? "teams.create.name-too-long" : "teams.create.name-too-long-default"), lang); return Command.SINGLE_SUCCESS; }

        if (!isDonator && newName.contains("#")) { sendFramedMessage(player, lang.getMsg(player, "teams.create.no-hex-colors"), lang); return Command.SINGLE_SUCCESS; }

        reteam.setDisplayName(newName);

        String successMsg = lang.getMsg(player, "reteam.rename.success").replace("%name%", newName);
        for (UUID memberUuid : reteam.getMembers()) {
            Player member = Bukkit.getPlayer(memberUuid);
            if (member != null && member.isOnline()) {
                TabManager.updatePlayerTab(member, plugin);
                sendFramedMessage(member, successMsg, lang);
            }
        }
        return Command.SINGLE_SUCCESS;
    }

    private static int executeTransfer(CommandContext<CommandSourceStack> context, PermaPiola plugin, LanguageManager lang) {
        Player player = getPlayerOrError(context);
        if (player == null) return Command.SINGLE_SUCCESS;

        PiolaReTeam reteam = ReTeamManager.getReTeam(player);
        if (reteam == null) { sendFramedMessage(player, lang.getMsg(player, "reteam.not-in-reteam"), lang); return Command.SINGLE_SUCCESS; }
        if (!reteam.isLeader(player.getUniqueId())) { sendFramedMessage(player, lang.getMsg(player, "reteam.not-leader"), lang); return Command.SINGLE_SUCCESS; }

        String targetName = context.getArgument("target", String.class);
        if (player.getName().equalsIgnoreCase(targetName)) { sendFramedMessage(player, lang.getMsg(player, "teams.transfer.self"), lang); return Command.SINGLE_SUCCESS; }

        UUID targetUuid = null;
        String finalTargetName = targetName;

        for (UUID memberId : reteam.getMembers()) {
            Player p = Bukkit.getPlayer(memberId);
            String mName = (p != null) ? p.getName() : Bukkit.getOfflinePlayer(memberId).getName();
            if (mName != null && mName.equalsIgnoreCase(targetName)) {
                targetUuid = memberId;
                finalTargetName = mName;
                break;
            }
        }

        if (targetUuid == null) { sendFramedMessage(player, lang.getMsg(player, "teams.transfer.not-in-team"), lang); return Command.SINGLE_SUCCESS; }

        reteam.setLeader(targetUuid);

        Player onlineTarget = Bukkit.getPlayer(targetUuid);
        String targetPrefix = onlineTarget != null ? LuckPermsUtils.getPrefix(onlineTarget) : LuckPermsUtils.getPrefixForOffline(finalTargetName);
        if (targetPrefix == null) targetPrefix = "";

        String formattedName = targetPrefix + finalTargetName;
        String successMsg = lang.getMsg(player, "reteam.transfer.success").replace("%player%", formattedName);

        for (UUID memberUuid : reteam.getMembers()) {
            Player member = Bukkit.getPlayer(memberUuid);
            if (member != null && member.isOnline()) { sendFramedMessage(member, successMsg, lang); }
        }
        return Command.SINGLE_SUCCESS;
    }

    // ========================================================================
    //                         EJECUCIÓN STAFF
    // ========================================================================

    private static int executeSystem(CommandContext<CommandSourceStack> context, String state, PermaPiola plugin, LanguageManager lang) {
        CommandSender sender = context.getSource().getSender();
        boolean currentState = ReTeamManager.isReteamSystemEnabled();

        if (state.equals("on")) {
            if (currentState) { sendFramedMessage(sender, lang.getMsg(sender, "teams.staff.system-already-on"), lang); return Command.SINGLE_SUCCESS; }
            plugin.getConfig().set("teams.reteam", true);
            plugin.saveConfig();
            ReTeamManager.loadConfigCache(plugin);
            sendFramedMessage(sender, lang.getMsg(sender, "reteam.staff.system-status").replace("%status%", lang.getMsg(sender, "teams.staff.system-on")), lang);
        } else {
            if (!currentState) { sendFramedMessage(sender, lang.getMsg(sender, "teams.staff.system-already-off"), lang); return Command.SINGLE_SUCCESS; }
            plugin.getConfig().set("teams.reteam", false);
            plugin.saveConfig();
            ReTeamManager.loadConfigCache(plugin);
            sendFramedMessage(sender, lang.getMsg(sender, "reteam.staff.system-status").replace("%status%", lang.getMsg(sender, "teams.staff.system-off")), lang);
        }
        return Command.SINGLE_SUCCESS;
    }

    private static int executeDisband(CommandContext<CommandSourceStack> context, PermaPiola plugin, LanguageManager lang) {
        CommandSender sender = context.getSource().getSender();
        String teamName = context.getArgument("teamName", String.class);

        PiolaReTeam targetTeam = null;
        for (PiolaReTeam reteam : ReTeamManager.getAllReTeams()) {
            if (reteam.getName().equalsIgnoreCase(teamName)) {
                targetTeam = reteam; break;
            }
        }

        if (targetTeam == null) { sendFramedMessage(sender, lang.getMsg(sender, "reteam.staff.reteam-not-found"), lang); return Command.SINGLE_SUCCESS; }

        String disbandMsg = lang.getMsg(sender, "reteam.staff.disband-broadcast");
        List<UUID> formerMembers = new ArrayList<>(targetTeam.getMembers());

        ReTeamManager.deleteReTeamForcefully(targetTeam);

        for (UUID memberUuid : formerMembers) {
            Player member = Bukkit.getPlayer(memberUuid);
            if (member != null && member.isOnline()) {
                sendFramedMessage(member, disbandMsg, lang);
                TabManager.updatePlayerTab(member, plugin);
            }
        }

        sendFramedMessage(sender, lang.getMsg(sender, "reteam.staff.disband-success").replace("%team%", targetTeam.getDisplayName()), lang);
        return Command.SINGLE_SUCCESS;
    }

    private static int executeList(CommandContext<CommandSourceStack> context, int page, PermaPiola plugin, LanguageManager lang) {
        CommandSender sender = context.getSource().getSender();
        List<PiolaReTeam> allTeams = new ArrayList<>(ReTeamManager.getAllReTeams());
        if (allTeams.isEmpty()) { sendFramedMessage(sender, lang.getMsg(sender, "teams.staff.no-teams"), lang); return Command.SINGLE_SUCCESS; }

        int teamsPerPage = 5;
        int maxPages = (int) Math.ceil((double) allTeams.size() / teamsPerPage);

        if (page < 1 || page > maxPages) {
            sendFramedMessage(sender, lang.getMsg(sender, "teams.staff.invalid-page").replace("%max_page%", String.valueOf(maxPages)), lang);
            return Command.SINGLE_SUCCESS;
        }

        int startIndex = (page - 1) * teamsPerPage;
        List<PiolaReTeam> pageTeams = allTeams.subList(startIndex, Math.min(startIndex + teamsPerPage, allTeams.size()));

        sender.sendMessage(ColorUtils.format(lang.getMsg(sender, "commands.generic.line")));
        sender.sendMessage(ColorUtils.format(lang.getMsg(sender, "teams.staff.lists-header").replace("%page%", String.valueOf(page)).replace("%max_page%", String.valueOf(maxPages))));
        sender.sendMessage(" ");

        int index = startIndex + 1;
        for (PiolaReTeam t : pageTeams) {
            String formattedLeader = "";
            List<String> formattedMembers = new ArrayList<>();

            for (UUID memberUuid : t.getMembers()) {
                String pFormat = formatPlayerForList(memberUuid, sender, lang);
                if (memberUuid.equals(t.getLeader())) formattedLeader = pFormat;
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
                Component prevBtn = LegacyComponentSerializer.legacySection().deserialize(ColorUtils.format(lang.getMsg(sender, "teams.staff.lists-btn-prev")))
                        .clickEvent(ClickEvent.runCommand("/reteam list " + (page - 1)));
                navRow = navRow.append(prevBtn).append(Component.text("   "));
            }

            if (page < maxPages) {
                Component nextBtn = LegacyComponentSerializer.legacySection().deserialize(ColorUtils.format(lang.getMsg(sender, "teams.staff.lists-btn-next")))
                        .clickEvent(ClickEvent.runCommand("/reteam list " + (page + 1)));
                navRow = navRow.append(nextBtn);
            }
            sender.sendMessage(navRow);
        }

        sender.sendMessage(ColorUtils.format(lang.getMsg(sender, "commands.generic.line")));
        return Command.SINGLE_SUCCESS;
    }
}