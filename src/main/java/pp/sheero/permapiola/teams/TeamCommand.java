package pp.sheero.permapiola.teams;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.command.brigadier.argument.ArgumentTypes;
import io.papermc.paper.command.brigadier.argument.resolvers.selector.PlayerSelectorArgumentResolver;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import pp.sheero.permapiola.PermaPiola;
import pp.sheero.permapiola.core.LanguageManager;
import pp.sheero.permapiola.utils.ColorUtils;
import pp.sheero.permapiola.utils.TimeUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

public class TeamCommand {

    private static final Pattern VALID_CHARS = Pattern.compile("^[a-zA-Z0-9_ ]+$");

    public static void register(Commands commands, PermaPiola plugin, LanguageManager lang) {

        var teamNode = Commands.literal("team")
                .executes(context -> executeHelp(context, lang));

        teamNode.then(Commands.literal("help").executes(context -> executeHelp(context, lang)));

        // ========================================== #
        //               ZONA USUARIO                 #
        // ========================================== #

        teamNode.then(Commands.literal("create")
                .then(Commands.argument("name", StringArgumentType.greedyString())
                        .executes(context -> executeCreate(context, plugin, lang))
                )
        );

        teamNode.then(Commands.literal("leave").executes(context -> executeLeave(context, plugin, lang)));

        teamNode.then(Commands.literal("info").executes(context -> executeInfo(context, plugin, lang)));

        teamNode.then(Commands.literal("location").executes(context -> executeLocation(context, plugin, lang)));

        teamNode.then(Commands.literal("invite")
                .then(Commands.argument("target", ArgumentTypes.player())
                        .executes(context -> executeInvite(context, plugin, lang))
                )
        );

        teamNode.then(Commands.literal("accept")
                .then(Commands.argument("target", ArgumentTypes.player())
                        .executes(context -> executeAccept(context, plugin, lang))
                )
        );

        teamNode.then(Commands.literal("tag")
                .then(Commands.argument("newTag", StringArgumentType.greedyString())
                        .executes(context -> executeTag(context, plugin, lang))
                )
        );

        teamNode.then(Commands.literal("rename")
                .then(Commands.argument("newName", StringArgumentType.greedyString())
                        .executes(context -> executeRename(context, plugin, lang))
                )
        );

        teamNode.then(Commands.literal("transfer")
                .then(Commands.argument("target", ArgumentTypes.player())
                        .executes(context -> executeTransfer(context, plugin, lang))
                )
        );

        teamNode.then(Commands.literal("glow")
                .then(Commands.literal("on").executes(context -> executeGlow(context, "on", plugin, lang)))
                .then(Commands.literal("off").executes(context -> executeGlow(context, "off", plugin, lang)))
        );

        // ========================================== #
        //               ZONA STAFF                   #
        // ========================================== #

        var systemNode = Commands.literal("system")
                .requires(source -> source.getSender().hasPermission("permapiola.admin.team.system"))
                .then(Commands.literal("on").executes(context -> executeSystem(context, "on", plugin, lang)))
                .then(Commands.literal("off").executes(context -> executeSystem(context, "off", plugin, lang)));
        teamNode.then(systemNode);

        teamNode.then(Commands.literal("reset")
                .requires(source -> source.getSender().hasPermission("permapiola.admin.team.reset"))
                .executes(context -> executeReset(context, lang))
        );

        teamNode.then(Commands.literal("size")
                .requires(source -> source.getSender().hasPermission("permapiola.admin.team.size"))
                .then(Commands.argument("amount", IntegerArgumentType.integer(1, 100))
                        .executes(context -> executeSize(context, plugin, lang))
                )
        );

        teamNode.then(Commands.literal("delete")
                .requires(source -> source.getSender().hasPermission("permapiola.admin.team.delete"))
                .then(Commands.argument("teamName", StringArgumentType.greedyString())
                        .suggests((context, builder) -> {
                            for (PiolaTeam t : TeamManager.getAllTeams()) {
                                if (t.getName().startsWith(builder.getRemaining().toLowerCase())) {
                                    builder.suggest(t.getName());
                                }
                            }
                            return builder.buildFuture();
                        })
                        .executes(context -> executeDelete(context, plugin, lang))
                )
        );

        teamNode.then(Commands.literal("forcejoin")
                .requires(source -> source.getSender().hasPermission("permapiola.admin.team.forcejoin"))
                .then(Commands.argument("teamName", StringArgumentType.word())
                        .suggests((context, builder) -> {
                            for (PiolaTeam t : TeamManager.getAllTeams()) builder.suggest(t.getName());
                            return builder.buildFuture();
                        })
                        .then(Commands.argument("target", ArgumentTypes.player())
                                .executes(context -> executeForceJoin(context, plugin, lang))
                        )
                )
        );

        teamNode.then(Commands.literal("forceleave")
                .requires(source -> source.getSender().hasPermission("permapiola.admin.team.forceleave"))
                .then(Commands.argument("target", ArgumentTypes.player())
                        .executes(context -> executeForceLeave(context, plugin, lang))
                )
        );

        teamNode.then(Commands.literal("list")
                .requires(source -> source.getSender().hasPermission("permapiola.admin.team.list"))
                .executes(context -> executeList(context, 1, plugin, lang))
                .then(Commands.argument("page", IntegerArgumentType.integer(1))
                        .executes(context -> executeList(context, context.getArgument("page", Integer.class), plugin, lang))
                )
        );

        teamNode.then(Commands.literal("spy")
                .requires(source -> source.getSender().hasPermission("permapiola.admin.team.spy"))
                .executes(context -> executeSpy(context, plugin, lang))
        );

        commands.register(teamNode.build(), "Gestión del sistema de equipos de PermaPiola");
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

        List<Character> restrictedColors = new ArrayList<>(java.util.Arrays.asList('r', 'l', 'o', 'n', 'm', 'k', '4', 'c', '5', 'b', '6'));

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
                if (restrictedColors.contains(colorChar)) {
                    return langPath;
                }
            }
        }
        return null;
    }

    // ========================================================================
    //                         EJECUCIÓN USUARIOS
    // ========================================================================

    private static int executeHelp(CommandContext<CommandSourceStack> context, LanguageManager lang) {
        CommandSender sender = context.getSource().getSender();
        List<String> helpLines = new ArrayList<>(lang.getMsgList(sender, "teams.help.user"));
        if (sender.hasPermission("permapiola.admin")) {
            helpLines.add(" ");
            helpLines.addAll(lang.getMsgList(sender, "teams.help.staff"));
        }
        helpLines.forEach(line -> sender.sendMessage(ColorUtils.format(line)));
        return Command.SINGLE_SUCCESS;
    }

    private static int executeCreate(CommandContext<CommandSourceStack> context, PermaPiola plugin, LanguageManager lang) {
        Player player = getPlayerOrError(context);
        if (player == null) return Command.SINGLE_SUCCESS;

        if (!TeamManager.isTeamsEnabled() && !player.hasPermission("permapiola.admin")) {
            sendFramedMessage(player, lang.getMsg(player, "teams.system-disabled"), lang);
            return Command.SINGLE_SUCCESS;
        }

        if (TeamManager.hasTeam(player)) {
            sendFramedMessage(player, lang.getMsg(player, "teams.already-in-team"), lang);
            return Command.SINGLE_SUCCESS;
        }

        String rawName = context.getArgument("name", String.class);
        String cleanName = ColorUtils.stripColors(rawName);

        boolean isDonator = player.hasPermission("permapiola.donor");

        String restrictedPath = checkRestrictedColors(player, rawName);
        if (restrictedPath != null) {
            sendFramedMessage(player, lang.getMsg(player, restrictedPath), lang);
            return Command.SINGLE_SUCCESS;
        }

        if (!VALID_CHARS.matcher(cleanName).matches()) {
            sendFramedMessage(player, lang.getMsg(player, "teams.create.invalid-chars"), lang);
            return Command.SINGLE_SUCCESS;
        }

        if (!isDonator && rawName.contains("#")) {
            sendFramedMessage(player, lang.getMsg(player, "teams.create.no-hex-colors"), lang);
            return Command.SINGLE_SUCCESS;
        }

        int maxLength = isDonator ? 16 : 8;
        if (cleanName.length() > maxLength) {
            sendFramedMessage(player, lang.getMsg(player, isDonator ? "teams.create.name-too-long" : "teams.create.name-too-long-default"), lang);
            return Command.SINGLE_SUCCESS;
        }

        String internalName = cleanName.replace(" ", "").toLowerCase();

        if (TeamManager.isTeamNameTaken(internalName)) {
            sendFramedMessage(player, lang.getMsg(player, "teams.create.name-taken"), lang);
            return Command.SINGLE_SUCCESS;
        }

        String rawTagStr = internalName.length() >= 3 ? internalName.substring(0, 3).toUpperCase() : internalName.toUpperCase();
        String generatedTag = "&8[&e" + rawTagStr + "&8]";

        PiolaTeam newTeam = TeamManager.createTeam(player, internalName, rawName, generatedTag);

        sendFramedMessage(player, lang.getMsg(player, "teams.create.success").replace("%team%", newTeam.getDisplayName()), lang);
        return Command.SINGLE_SUCCESS;
    }

    private static int executeTag(CommandContext<CommandSourceStack> context, PermaPiola plugin, LanguageManager lang) {
        Player player = getPlayerOrError(context);
        if (player == null) return Command.SINGLE_SUCCESS;

        PiolaTeam team = TeamManager.getTeam(player);
        if (team == null) { sendFramedMessage(player, lang.getMsg(player, "teams.not-in-team"), lang); return Command.SINGLE_SUCCESS; }
        if (!team.isLeader(player.getUniqueId())) { sendFramedMessage(player, lang.getMsg(player, "teams.not-leader"), lang); return Command.SINGLE_SUCCESS; }

        String newTag = context.getArgument("newTag", String.class);
        String cleanTag = ColorUtils.stripColors(newTag);
        boolean isDonator = player.hasPermission("permapiola.donor");

        String restrictedPath = checkRestrictedColors(player, newTag);
        if (restrictedPath != null) {
            sendFramedMessage(player, lang.getMsg(player, restrictedPath), lang);
            return Command.SINGLE_SUCCESS;
        }

        if (!VALID_CHARS.matcher(cleanTag).matches()) {
            sendFramedMessage(player, lang.getMsg(player, "teams.create.invalid-chars"), lang);
            return Command.SINGLE_SUCCESS;
        }

        int maxLength = isDonator ? 10 : 5;
        if (cleanTag.length() > maxLength) {
            sendFramedMessage(player, lang.getMsg(player, "teams.tag.too-long"), lang);
            return Command.SINGLE_SUCCESS;
        }

        if (!isDonator && newTag.contains("#")) {
            sendFramedMessage(player, lang.getMsg(player, "teams.create.no-hex-colors"), lang);
            return Command.SINGLE_SUCCESS;
        }

        String formattedTag = "&8[" + newTag + "&8]";
        team.setTag(formattedTag);
        team.broadcast(lang.getMsg(player, "teams.tag.success").replace("%tag%", formattedTag));

        return Command.SINGLE_SUCCESS;
    }

    private static int executeRename(CommandContext<CommandSourceStack> context, PermaPiola plugin, LanguageManager lang) {
        Player player = getPlayerOrError(context);
        if (player == null) return Command.SINGLE_SUCCESS;

        PiolaTeam team = TeamManager.getTeam(player);
        if (team == null) { sendFramedMessage(player, lang.getMsg(player, "teams.not-in-team"), lang); return Command.SINGLE_SUCCESS; }
        if (!team.isLeader(player.getUniqueId())) { sendFramedMessage(player, lang.getMsg(player, "teams.not-leader"), lang); return Command.SINGLE_SUCCESS; }

        String newName = context.getArgument("newName", String.class);
        String cleanName = ColorUtils.stripColors(newName);
        boolean isDonator = player.hasPermission("permapiola.donor");

        String restrictedPath = checkRestrictedColors(player, newName);
        if (restrictedPath != null) {
            sendFramedMessage(player, lang.getMsg(player, restrictedPath), lang);
            return Command.SINGLE_SUCCESS;
        }

        if (!VALID_CHARS.matcher(cleanName).matches()) {
            sendFramedMessage(player, lang.getMsg(player, "teams.create.invalid-chars"), lang);
            return Command.SINGLE_SUCCESS;
        }

        int maxLength = isDonator ? 16 : 8;
        if (cleanName.length() > maxLength) {
            sendFramedMessage(player, lang.getMsg(player, isDonator ? "teams.create.name-too-long" : "teams.create.name-too-long-default"), lang);
            return Command.SINGLE_SUCCESS;
        }

        if (!isDonator && newName.contains("#")) {
            sendFramedMessage(player, lang.getMsg(player, "teams.create.no-hex-colors"), lang);
            return Command.SINGLE_SUCCESS;
        }

        team.setDisplayName(newName);
        team.broadcast(lang.getMsg(player, "teams.rename.success").replace("%name%", newName));
        return Command.SINGLE_SUCCESS;
    }

    private static int executeTransfer(CommandContext<CommandSourceStack> context, PermaPiola plugin, LanguageManager lang) {
        Player player = getPlayerOrError(context);
        if (player == null) return Command.SINGLE_SUCCESS;

        PiolaTeam team = TeamManager.getTeam(player);
        if (team == null) { sendFramedMessage(player, lang.getMsg(player, "teams.not-in-team"), lang); return Command.SINGLE_SUCCESS; }
        if (!team.isLeader(player.getUniqueId())) { sendFramedMessage(player, lang.getMsg(player, "teams.not-leader"), lang); return Command.SINGLE_SUCCESS; }

        try {
            Player target = context.getArgument("target", PlayerSelectorArgumentResolver.class).resolve(context.getSource()).get(0);
            if (player.equals(target)) { sendFramedMessage(player, lang.getMsg(player, "teams.transfer.self"), lang); return Command.SINGLE_SUCCESS; }
            if (!team.hasMember(target.getUniqueId())) { sendFramedMessage(player, lang.getMsg(player, "teams.transfer.not-in-team"), lang); return Command.SINGLE_SUCCESS; }

            team.setLeader(target.getUniqueId());
            team.broadcast(lang.getMsg(player, "teams.transfer.success").replace("%player%", target.getName()));

        } catch (CommandSyntaxException e) {
            sendFramedMessage(player, lang.getMsg(player, "commands.generic.player-offline"), lang);
        }
        return Command.SINGLE_SUCCESS;
    }

    private static int executeLeave(CommandContext<CommandSourceStack> context, PermaPiola plugin, LanguageManager lang) {
        Player player = getPlayerOrError(context);
        if (player == null) return Command.SINGLE_SUCCESS;

        PiolaTeam team = TeamManager.getTeam(player);
        if (team == null) { sendFramedMessage(player, lang.getMsg(player, "teams.not-in-team"), lang); return Command.SINGLE_SUCCESS; }

        if (team.isLeader(player.getUniqueId())) {
            team.broadcast(lang.getMsg(player, "teams.leave.disbanded"));
        } else {
            team.broadcast(lang.getMsg(player, "teams.leave.broadcast").replace("%player%", player.getName()));
            sendFramedMessage(player, lang.getMsg(player, "teams.leave.success"), lang);
        }

        TeamManager.removePlayerFromTeam(player);
        return Command.SINGLE_SUCCESS;
    }

    private static int executeInfo(CommandContext<CommandSourceStack> context, PermaPiola plugin, LanguageManager lang) {
        Player player = getPlayerOrError(context);
        if (player == null) return Command.SINGLE_SUCCESS;

        PiolaTeam team = TeamManager.getTeam(player);
        if (team == null) { sendFramedMessage(player, lang.getMsg(player, "teams.not-in-team"), lang); return Command.SINGLE_SUCCESS; }

        player.sendMessage(ColorUtils.format(lang.getMsg(player, "commands.generic.line")));
        player.sendMessage(ColorUtils.format(lang.getMsg(player, "teams.memberlist.team-name").replace("%team%", team.getDisplayName())));

        player.sendMessage(ColorUtils.format(lang.getMsg(player, "teams.memberlist.tag").replace("%tag%", team.getTag())));

        String formattedPlaytime = TimeUtils.formatTime(team.getTotalPlaytime(),
                lang.getMsg(player, "playtime.units.week"),
                lang.getMsg(player, "playtime.units.day"),
                lang.getMsg(player, "playtime.units.hour"),
                lang.getMsg(player, "playtime.units.minute"),
                lang.getMsg(player, "playtime.units.second"));
        player.sendMessage(ColorUtils.format(lang.getMsg(player, "teams.memberlist.playtime").replace("%time%", formattedPlaytime)));

        player.sendMessage(ColorUtils.format(lang.getMsg(player, "teams.memberlist.totems").replace("%totems%", String.valueOf(team.getTotalTotems()))));

        Player leader = Bukkit.getPlayer(team.getLeader());
        String leaderName = leader != null ? leader.getName() : lang.getMsg(player, "teams.memberlist.offline-player");
        player.sendMessage(ColorUtils.format(lang.getMsg(player, "teams.memberlist.leader").replace("%leader_info%", leaderName)));

        List<String> memberNames = new ArrayList<>();
        for (java.util.UUID uuid : team.getMembers()) {
            if (!uuid.equals(team.getLeader())) {
                Player m = Bukkit.getPlayer(uuid);
                memberNames.add(m != null ? m.getName() : lang.getMsg(player, "teams.memberlist.unknown-player"));
            }
        }

        if (!memberNames.isEmpty()) {
            String memPrefix = lang.getMsg(player, "teams.memberlist.members-prefix");
            String separator = lang.getMsg(player, "teams.memberlist.members-separator");
            player.sendMessage(ColorUtils.format(memPrefix + String.join(separator, memberNames)));
        }

        player.sendMessage(ColorUtils.format(lang.getMsg(player, "commands.generic.line")));
        return Command.SINGLE_SUCCESS;
    }

    private static int executeLocation(CommandContext<CommandSourceStack> context, PermaPiola plugin, LanguageManager lang) {
        Player player = getPlayerOrError(context);
        if (player == null) return Command.SINGLE_SUCCESS;

        PiolaTeam team = TeamManager.getTeam(player);
        if (team == null) { sendFramedMessage(player, lang.getMsg(player, "teams.not-in-team"), lang); return Command.SINGLE_SUCCESS; }

        String coordsMsg = player.getLocation().getBlockX() + " " + player.getLocation().getBlockY() + " " + player.getLocation().getBlockZ();
        String chatFormat = plugin.getConfig().getString("chat.team-format", "&8[&bTeamChat&8] %player_format%&f: %message%");

        String formattedMessage = ColorUtils.format(chatFormat
                .replace("%player_format%", player.getName())
                .replace("%message%", coordsMsg));

        team.broadcast(formattedMessage);
        return Command.SINGLE_SUCCESS;
    }

    private static int executeInvite(CommandContext<CommandSourceStack> context, PermaPiola plugin, LanguageManager lang) {
        Player player = getPlayerOrError(context);
        if (player == null) return Command.SINGLE_SUCCESS;

        PiolaTeam inviterTeam = TeamManager.getTeam(player);
        if (inviterTeam == null) { sendFramedMessage(player, lang.getMsg(player, "teams.not-in-team"), lang); return Command.SINGLE_SUCCESS; }

        try {
            Player target = context.getArgument("target", PlayerSelectorArgumentResolver.class).resolve(context.getSource()).get(0);

            if (target.equals(player)) { sendFramedMessage(player, lang.getMsg(player, "teams.invite.cant-invite-self"), lang); return Command.SINGLE_SUCCESS; }
            if (inviterTeam.hasMember(target.getUniqueId())) { sendFramedMessage(player, lang.getMsg(player, "teams.invite.already-in-your-team").replace("%target%", target.getName()), lang); return Command.SINGLE_SUCCESS; }
            if (TeamManager.hasTeam(target)) { sendFramedMessage(player, lang.getMsg(player, "teams.invite.target-already-in-team"), lang); return Command.SINGLE_SUCCESS; }
            if (TeamManager.hasInvite(player, target)) { sendFramedMessage(player, lang.getMsg(player, "teams.invite.already-invited"), lang); return Command.SINGLE_SUCCESS; }
            if (inviterTeam.isFull()) { sendFramedMessage(player, lang.getMsg(player, "teams.team-full").replace("%size%", String.valueOf(inviterTeam.getMaxSize())), lang); return Command.SINGLE_SUCCESS; }

            sendFramedMessage(player, lang.getMsg(player, "teams.invite.sent").replace("%target%", target.getName()), lang);

            String lineStr = lang.getMsg(target, "commands.generic.line");
            target.sendMessage(ColorUtils.format(lineStr));

            String receivedMsg = lang.getMsg(target, "teams.invite.received").replace("%player%", player.getName());
            Component hoverComp = LegacyComponentSerializer.legacySection().deserialize(ColorUtils.format(lang.getMsg(target, "teams.invite.hover").replace("%player%", player.getName()).replace("\\n", "\n")));

            String[] lines = receivedMsg.split("\\n");
            for (int i = 0; i < lines.length; i++) {
                Component textComp = LegacyComponentSerializer.legacySection().deserialize(ColorUtils.format(lines[i]));
                if (i == lines.length - 1) {
                    textComp = textComp.clickEvent(ClickEvent.runCommand("/team accept " + player.getName()))
                            .hoverEvent(net.kyori.adventure.text.event.HoverEvent.showText(hoverComp));
                }
                target.sendMessage(textComp);
            }
            target.sendMessage(ColorUtils.format(lineStr));

            int taskId = Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (TeamManager.hasInvite(player, target)) {
                    TeamManager.removeInvite(player, target);
                    if (player.isOnline()) sendFramedMessage(player, lang.getMsg(player, "teams.invite.expired-sender").replace("%target%", target.getName()), lang);
                    if (target.isOnline()) sendFramedMessage(target, lang.getMsg(target, "teams.invite.expired-target").replace("%player%", player.getName()), lang);
                }
            }, 1200L).getTaskId();

            TeamManager.addInvite(player, target, taskId);

        } catch (CommandSyntaxException e) {
            sendFramedMessage(player, lang.getMsg(player, "commands.generic.player-offline"), lang);
        }
        return Command.SINGLE_SUCCESS;
    }

    private static int executeAccept(CommandContext<CommandSourceStack> context, PermaPiola plugin, LanguageManager lang) {
        Player player = getPlayerOrError(context);
        if (player == null) return Command.SINGLE_SUCCESS;

        if (TeamManager.hasTeam(player)) { sendFramedMessage(player, lang.getMsg(player, "teams.already-in-team"), lang); return Command.SINGLE_SUCCESS; }

        try {
            Player inviter = context.getArgument("target", PlayerSelectorArgumentResolver.class).resolve(context.getSource()).get(0);

            if (!TeamManager.hasInvite(inviter, player)) { sendFramedMessage(player, lang.getMsg(player, "teams.accept.no-invite"), lang); return Command.SINGLE_SUCCESS; }

            PiolaTeam team = TeamManager.getTeam(inviter);
            if (team == null) {
                sendFramedMessage(player, lang.getMsg(player, "teams.accept.no-invite"), lang);
                TeamManager.removeInvite(inviter, player);
                return Command.SINGLE_SUCCESS;
            }

            if (team.isFull()) { sendFramedMessage(player, lang.getMsg(player, "teams.team-full").replace("%size%", String.valueOf(team.getMaxSize())), lang); return Command.SINGLE_SUCCESS; }

            TeamManager.removeInvite(inviter, player);
            TeamManager.addPlayerToTeam(player, team);

            sendFramedMessage(player, lang.getMsg(player, "teams.accept.success").replace("%player%", inviter.getName()), lang);
            team.broadcast(lang.getMsg(player, "teams.accept.broadcast").replace("%new_member%", player.getName()));

        } catch (CommandSyntaxException e) {
            sendFramedMessage(player, lang.getMsg(player, "commands.generic.player-offline"), lang);
        }
        return Command.SINGLE_SUCCESS;
    }

    private static int executeGlow(CommandContext<CommandSourceStack> context, String state, PermaPiola plugin, LanguageManager lang) {
        Player player = getPlayerOrError(context);
        if (player == null) return Command.SINGLE_SUCCESS;

        if (!Bukkit.getPluginManager().isPluginEnabled("ProtocolLib")) {
            sendFramedMessage(player, lang.getMsg(player, "teams.glow.disabled-dependency"), lang);
            return Command.SINGLE_SUCCESS;
        }

        if (!TeamManager.hasTeam(player)) { sendFramedMessage(player, lang.getMsg(player, "teams.not-in-team"), lang); return Command.SINGLE_SUCCESS; }

        if (state.equals("on")) {
            if (TeamManager.hasGlowEnabled(player)) { sendFramedMessage(player, lang.getMsg(player, "teams.glow.already-on"), lang); return Command.SINGLE_SUCCESS; }
            TeamManager.toggleGlow(player);
            sendFramedMessage(player, lang.getMsg(player, "teams.glow.toggled-on"), lang);
        } else {
            if (!TeamManager.hasGlowEnabled(player)) { sendFramedMessage(player, lang.getMsg(player, "teams.glow.already-off"), lang); return Command.SINGLE_SUCCESS; }
            TeamManager.toggleGlow(player);
            sendFramedMessage(player, lang.getMsg(player, "teams.glow.toggled-off"), lang);
        }
        return Command.SINGLE_SUCCESS;
    }

    // ========================================================================
    //                         EJECUCIÓN STAFF
    // ========================================================================

    private static int executeSystem(CommandContext<CommandSourceStack> context, String state, PermaPiola plugin, LanguageManager lang) {
        CommandSender sender = context.getSource().getSender();
        boolean currentState = TeamManager.isTeamsEnabled();

        if (state.equals("on")) {
            if (currentState) { sendFramedMessage(sender, lang.getMsg(sender, "teams.staff.system-already-on"), lang); return Command.SINGLE_SUCCESS; }
            plugin.getConfig().set("teams.enabled", true);
            plugin.saveConfig();
            TeamManager.loadConfigCache(plugin);
            sendFramedMessage(sender, lang.getMsg(sender, "teams.staff.system-status").replace("%status%", lang.getMsg(sender, "teams.staff.system-on")), lang);
        } else {
            if (!currentState) { sendFramedMessage(sender, lang.getMsg(sender, "teams.staff.system-already-off"), lang); return Command.SINGLE_SUCCESS; }
            plugin.getConfig().set("teams.enabled", false);
            plugin.saveConfig();
            TeamManager.loadConfigCache(plugin);
            sendFramedMessage(sender, lang.getMsg(sender, "teams.staff.system-status").replace("%status%", lang.getMsg(sender, "teams.staff.system-off")), lang);
        }
        return Command.SINGLE_SUCCESS;
    }

    private static int executeReset(CommandContext<CommandSourceStack> context, LanguageManager lang) {
        CommandSender sender = context.getSource().getSender();
        sendFramedMessage(sender, lang.getMsg(sender, "teams.staff.reset-start"), lang);
        TeamManager.resetAllTeams();
        sendFramedMessage(sender, lang.getMsg(sender, "teams.staff.reset-complete"), lang);
        return Command.SINGLE_SUCCESS;
    }

    private static int executeSize(CommandContext<CommandSourceStack> context, PermaPiola plugin, LanguageManager lang) {
        CommandSender sender = context.getSource().getSender();
        int newSize = context.getArgument("amount", Integer.class);

        if (newSize == TeamManager.getMaxSize()) {
            sendFramedMessage(sender, lang.getMsg(sender, "teams.staff.size-already-same").replace("%size%", String.valueOf(newSize)), lang);
            return Command.SINGLE_SUCCESS;
        }

        plugin.getConfig().set("teams.max-size", newSize);
        plugin.saveConfig();
        TeamManager.loadConfigCache(plugin);
        sendFramedMessage(sender, lang.getMsg(sender, "teams.staff.size-status").replace("%size%", String.valueOf(newSize)), lang);
        return Command.SINGLE_SUCCESS;
    }

    private static int executeDelete(CommandContext<CommandSourceStack> context, PermaPiola plugin, LanguageManager lang) {
        CommandSender sender = context.getSource().getSender();
        String teamName = context.getArgument("teamName", String.class);

        PiolaTeam team = TeamManager.getTeamByName(teamName);
        if (team == null) { sendFramedMessage(sender, lang.getMsg(sender, "teams.staff.team-not-found"), lang); return Command.SINGLE_SUCCESS; }

        team.broadcast(lang.getMsg(sender, "teams.leave.disbanded"));
        TeamManager.deleteTeamForcefully(team);

        sendFramedMessage(sender, lang.getMsg(sender, "teams.staff.delete-success").replace("%team%", team.getDisplayName()), lang);
        return Command.SINGLE_SUCCESS;
    }

    private static int executeForceJoin(CommandContext<CommandSourceStack> context, PermaPiola plugin, LanguageManager lang) {
        CommandSender sender = context.getSource().getSender();
        try {
            PiolaTeam team = TeamManager.getTeamByName(context.getArgument("teamName", String.class));
            if (team == null) { sendFramedMessage(sender, lang.getMsg(sender, "teams.staff.team-not-found"), lang); return Command.SINGLE_SUCCESS; }

            Player target = context.getArgument("target", PlayerSelectorArgumentResolver.class).resolve(context.getSource()).get(0);

            if (TeamManager.hasTeam(target)) {
                sendFramedMessage(sender, lang.getMsg(sender, "teams.staff.forcejoin-other-already-different").replace("%player%", target.getName()).replace("%current_team%", TeamManager.getTeam(target).getDisplayName()), lang);
                return Command.SINGLE_SUCCESS;
            }

            TeamManager.addPlayerToTeam(target, team);
            sendFramedMessage(target, lang.getMsg(target, "teams.staff.forcejoin-target").replace("%team%", team.getDisplayName()), lang);
            team.broadcast(lang.getMsg(sender, "teams.staff.forcejoin-broadcast").replace("%player%", target.getName()));

        } catch (CommandSyntaxException e) {
            sendFramedMessage(sender, lang.getMsg(sender, "commands.generic.player-offline"), lang);
        }
        return Command.SINGLE_SUCCESS;
    }

    private static int executeForceLeave(CommandContext<CommandSourceStack> context, PermaPiola plugin, LanguageManager lang) {
        CommandSender sender = context.getSource().getSender();
        try {
            Player target = context.getArgument("target", PlayerSelectorArgumentResolver.class).resolve(context.getSource()).get(0);
            PiolaTeam team = TeamManager.getTeam(target);

            if (team == null) {
                sendFramedMessage(sender, lang.getMsg(sender, "teams.staff.forceleave-other-no-team").replace("%player%", target.getName()), lang);
                return Command.SINGLE_SUCCESS;
            }

            if (team.isLeader(target.getUniqueId())) {
                team.broadcast(lang.getMsg(sender, "teams.leave.disbanded"));
            } else {
                team.broadcast(lang.getMsg(sender, "teams.staff.forceleave-broadcast").replace("%player%", target.getName()));
            }

            TeamManager.removePlayerFromTeam(target);
            sendFramedMessage(target, lang.getMsg(target, "teams.staff.forceleave-target"), lang);
            sendFramedMessage(sender, lang.getMsg(sender, "teams.staff.forceleave-success").replace("%player%", target.getName()), lang);

        } catch (CommandSyntaxException e) {
            sendFramedMessage(sender, lang.getMsg(sender, "commands.generic.player-offline"), lang);
        }
        return Command.SINGLE_SUCCESS;
    }

    private static int executeList(CommandContext<CommandSourceStack> context, int page, PermaPiola plugin, LanguageManager lang) {
        CommandSender sender = context.getSource().getSender();
        List<PiolaTeam> allTeams = new ArrayList<>(TeamManager.getAllTeams());
        if (allTeams.isEmpty()) { sendFramedMessage(sender, lang.getMsg(sender, "teams.staff.no-teams"), lang); return Command.SINGLE_SUCCESS; }

        int teamsPerPage = 5;
        int maxPages = (int) Math.ceil((double) allTeams.size() / teamsPerPage);

        if (page < 1 || page > maxPages) {
            sendFramedMessage(sender, lang.getMsg(sender, "teams.staff.invalid-page").replace("%max_page%", String.valueOf(maxPages)), lang);
            return Command.SINGLE_SUCCESS;
        }

        int startIndex = (page - 1) * teamsPerPage;
        List<PiolaTeam> pageTeams = allTeams.subList(startIndex, Math.min(startIndex + teamsPerPage, allTeams.size()));

        sender.sendMessage(ColorUtils.format(lang.getMsg(sender, "commands.generic.line")));
        sender.sendMessage(ColorUtils.format(lang.getMsg(sender, "teams.staff.lists-header").replace("%page%", String.valueOf(page)).replace("%max_page%", String.valueOf(maxPages))));
        sender.sendMessage(" ");

        int index = startIndex + 1;
        for (PiolaTeam t : pageTeams) {
            Player leader = Bukkit.getPlayer(t.getLeader());
            String leaderName = leader != null ? leader.getName() : lang.getMsg(sender, "teams.memberlist.unknown-player");

            sender.sendMessage(ColorUtils.format(lang.getMsg(sender, "teams.staff.lists-team-format").replace("%index%", String.valueOf(index)).replace("%team_name%", t.getDisplayName())));
            sender.sendMessage(ColorUtils.format(lang.getMsg(sender, "teams.staff.lists-leader").replace("%leader%", leaderName)));
            index++;
        }

        if (maxPages > 1 && sender instanceof Player) {
            sender.sendMessage(" ");
            Component navRow = Component.empty();

            if (page > 1) {
                Component prevBtn = LegacyComponentSerializer.legacySection().deserialize(ColorUtils.format(lang.getMsg(sender, "teams.staff.lists-btn-prev")))
                        .clickEvent(ClickEvent.runCommand("/team list " + (page - 1)));
                navRow = navRow.append(prevBtn).append(Component.text("   "));
            }

            if (page < maxPages) {
                Component nextBtn = LegacyComponentSerializer.legacySection().deserialize(ColorUtils.format(lang.getMsg(sender, "teams.staff.lists-btn-next")))
                        .clickEvent(ClickEvent.runCommand("/team list " + (page + 1)));
                navRow = navRow.append(nextBtn);
            }
            sender.sendMessage(navRow);
        }

        sender.sendMessage(ColorUtils.format(lang.getMsg(sender, "commands.generic.line")));
        return Command.SINGLE_SUCCESS;
    }

    private static int executeSpy(CommandContext<CommandSourceStack> context, PermaPiola plugin, LanguageManager lang) {
        Player player = getPlayerOrError(context);
        if (player == null) return Command.SINGLE_SUCCESS;

        TeamManager.toggleSpy(player);
        String path = TeamManager.hasSpyEnabled(player) ? "teams.staff.spy-toggled-on" : "teams.staff.spy-toggled-off";
        sendFramedMessage(player, lang.getMsg(player, path), lang);
        return Command.SINGLE_SUCCESS;
    }
}