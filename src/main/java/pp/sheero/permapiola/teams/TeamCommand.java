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
import pp.sheero.permapiola.utils.LuckPermsUtils;
import pp.sheero.permapiola.utils.TimeUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
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

        teamNode.then(Commands.literal("info").executes(context -> executeInfo(context, plugin, lang)));

        teamNode.then(Commands.literal("location").executes(context -> executeLocation(context, plugin, lang)));

        teamNode.then(Commands.literal("invite")
                .then(Commands.argument("target", StringArgumentType.word())
                        .suggests((context, builder) -> {
                            if (!(context.getSource().getExecutor() instanceof Player)) return builder.buildFuture();
                            Player p = (Player) context.getSource().getExecutor();
                            PiolaTeam team = TeamManager.getTeam(p);
                            String input = builder.getRemaining().toLowerCase();

                            for (Player online : Bukkit.getOnlinePlayers()) {
                                if (online.equals(p)) continue;
                                if (team != null && team.hasMember(online.getUniqueId())) continue;

                                if (online.getName().toLowerCase().startsWith(input)) {
                                    builder.suggest(online.getName());
                                }
                            }
                            return builder.buildFuture();
                        })
                        .executes(context -> executeInvite(context, plugin, lang))
                )
        );

        teamNode.then(Commands.literal("accept")
                .then(Commands.argument("target", StringArgumentType.word())
                        .suggests((context, builder) -> {
                            if (!(context.getSource().getExecutor() instanceof Player)) return builder.buildFuture();
                            Player p = (Player) context.getSource().getExecutor();
                            String input = builder.getRemaining().toLowerCase();

                            for (Player online : Bukkit.getOnlinePlayers()) {
                                if (TeamManager.hasInvite(online, p)) {
                                    if (online.getName().toLowerCase().startsWith(input)) {
                                        builder.suggest(online.getName());
                                    }
                                }
                            }
                            return builder.buildFuture();
                        })
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
                .then(Commands.argument("target", StringArgumentType.word())
                        .suggests((context, builder) -> {
                            if (!(context.getSource().getExecutor() instanceof Player)) return builder.buildFuture();
                            Player p = (Player) context.getSource().getExecutor();
                            PiolaTeam team = TeamManager.getTeam(p);

                            if (team != null && team.isLeader(p.getUniqueId())) {
                                String input = builder.getRemaining().toLowerCase();
                                for (java.util.UUID memberId : team.getMembers()) {
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
                                .suggests((context, builder) -> {
                                    String input = builder.getRemaining().toLowerCase();
                                    for (Player p : Bukkit.getOnlinePlayers()) {
                                        // Sugiere solo jugadores que NO tengan equipo
                                        if (!TeamManager.hasTeam(p) && p.getName().toLowerCase().startsWith(input)) {
                                            builder.suggest(p.getName());
                                        }
                                    }
                                    return builder.buildFuture();
                                })
                                .executes(context -> executeForceJoin(context, plugin, lang))
                        )
                )
        );

        teamNode.then(Commands.literal("forceleave")
                .requires(source -> source.getSender().hasPermission("permapiola.admin.team.forceleave"))
                .then(Commands.argument("target", ArgumentTypes.player())
                        .suggests((context, builder) -> {
                            String input = builder.getRemaining().toLowerCase();
                            for (Player p : Bukkit.getOnlinePlayers()) {
                                // Sugiere solo jugadores que SÍ tengan equipo
                                if (TeamManager.hasTeam(p) && p.getName().toLowerCase().startsWith(input)) {
                                    builder.suggest(p.getName());
                                }
                            }
                            return builder.buildFuture();
                        })
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
                if (restrictedColors.contains(colorChar)) {
                    return langPath;
                }
            }
        }
        return null;
    }

    private static String formatPlayerForList(UUID memberUuid, CommandSender viewer, PermaPiola plugin, LanguageManager lang) {
        Player memberObj = Bukkit.getPlayer(memberUuid);
        String playerName;
        if (memberObj != null) {
            playerName = memberObj.getName();
        } else {
            @SuppressWarnings("deprecation")
            org.bukkit.OfflinePlayer op = Bukkit.getOfflinePlayer(memberUuid);
            playerName = op.getName() != null ? op.getName() : lang.getMsg(viewer, "teams.memberlist.unknown-player");
        }

        String icon;
        String finalPrefix;

        if (pp.sheero.permapiola.hurricane.DeathStateManager.isDead(memberUuid)) {
            icon = lang.getMsg(viewer, "teams.memberlist.icons.dead");
            finalPrefix = (memberObj != null) ? LuckPermsUtils.getPrefix(memberObj) : LuckPermsUtils.getPrefixForOffline(playerName);
        } else if (memberObj != null && memberObj.isOnline()) {
            icon = lang.getMsg(viewer, "teams.memberlist.icons.online");
            finalPrefix = LuckPermsUtils.getPrefix(memberObj);
        } else {
            icon = lang.getMsg(viewer, "teams.memberlist.icons.offline");
            finalPrefix = LuckPermsUtils.getPrefixForOffline(playerName);
        }

        if (finalPrefix == null) finalPrefix = "";

        return lang.getMsg(viewer, "teams.memberlist.player-format")
                .replace("%status%", icon)
                .replace("%player_prefix%", finalPrefix)
                .replace("%player%", playerName);
    }

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

        if (cleanName.length() < 3) {
            sendFramedMessage(player, lang.getMsg(player, "teams.create.name-too-short"), lang);
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

        TabManager.updatePlayerTab(player, plugin);

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

        String successMsg = lang.getMsg(player, "teams.tag.success").replace("%tag%", formattedTag);
        for (UUID memberUuid : team.getMembers()) {
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
        if (cleanName.length() < 3) {
            sendFramedMessage(player, lang.getMsg(player, "teams.create.name-too-short"), lang);
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

        String successMsg = lang.getMsg(player, "teams.rename.success").replace("%name%", newName);
        for (UUID memberUuid : team.getMembers()) {
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

        PiolaTeam team = TeamManager.getTeam(player);
        if (team == null) { sendFramedMessage(player, lang.getMsg(player, "teams.not-in-team"), lang); return Command.SINGLE_SUCCESS; }
        if (!team.isLeader(player.getUniqueId())) { sendFramedMessage(player, lang.getMsg(player, "teams.not-leader"), lang); return Command.SINGLE_SUCCESS; }

        String targetName = context.getArgument("target", String.class);

        if (player.getName().equalsIgnoreCase(targetName)) {
            sendFramedMessage(player, lang.getMsg(player, "teams.transfer.self"), lang);
            return Command.SINGLE_SUCCESS;
        }

        UUID targetUuid = null;
        String finalTargetName = targetName;

        for (UUID memberId : team.getMembers()) {
            Player p = Bukkit.getPlayer(memberId);
            String mName = (p != null) ? p.getName() : Bukkit.getOfflinePlayer(memberId).getName();

            if (mName != null && mName.equalsIgnoreCase(targetName)) {
                targetUuid = memberId;
                finalTargetName = mName;
                break;
            }
        }

        if (targetUuid == null) {
            sendFramedMessage(player, lang.getMsg(player, "teams.transfer.not-in-team"), lang);
            return Command.SINGLE_SUCCESS;
        }

        team.setLeader(targetUuid);

        Player onlineTarget = Bukkit.getPlayer(targetUuid);
        String targetPrefix = onlineTarget != null ? LuckPermsUtils.getPrefix(onlineTarget) : LuckPermsUtils.getPrefixForOffline(finalTargetName);
        if (targetPrefix == null) targetPrefix = "";

        String formattedName = targetPrefix + finalTargetName;
        String successMsg = lang.getMsg(player, "teams.transfer.success").replace("%player%", formattedName);

        for (UUID memberUuid : team.getMembers()) {
            Player member = Bukkit.getPlayer(memberUuid);
            if (member != null && member.isOnline()) {
                sendFramedMessage(member, successMsg, lang);
            }
        }

        return Command.SINGLE_SUCCESS;
    }

    private static int executeInfo(CommandContext<CommandSourceStack> context, PermaPiola plugin, LanguageManager lang) {
        Player player = getPlayerOrError(context);
        if (player == null) return Command.SINGLE_SUCCESS;

        PiolaTeam team = TeamManager.getTeam(player);
        if (team == null) { sendFramedMessage(player, lang.getMsg(player, "teams.not-in-team"), lang); return Command.SINGLE_SUCCESS; }

        String formattedLeader = "";
        List<String> formattedMembers = new ArrayList<>();

        for (UUID memberUuid : team.getMembers()) {
            String pFormat = formatPlayerForList(memberUuid, player, plugin, lang);
            if (memberUuid.equals(team.getLeader())) formattedLeader = pFormat;
            else formattedMembers.add(pFormat);
        }

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

    private static int executeLocation(CommandContext<CommandSourceStack> context, PermaPiola plugin, LanguageManager lang) {
        Player player = getPlayerOrError(context);
        if (player == null) return Command.SINGLE_SUCCESS;

        boolean inReTeam = ReTeamManager.hasReTeam(player);
        if (!TeamManager.hasTeam(player) && !inReTeam) {
            sendFramedMessage(player, lang.getMsg(player, "teams.not-in-team"), lang);
            return Command.SINGLE_SUCCESS;
        }

        int x = player.getLocation().getBlockX();
        int y = player.getLocation().getBlockY();
        int z = player.getLocation().getBlockZ();

        String rawWorldName = player.getWorld().getName();
        String worldDisplay;

        if (rawWorldName.equals("world_permapiola_fallen_memories")) {
            worldDisplay = "Fallen Memories";
        } else if (rawWorldName.endsWith("_nether")) {
            worldDisplay = "Nether";
        } else if (rawWorldName.endsWith("_the_end")) {
            worldDisplay = "End";
        } else {
            worldDisplay = "Over";
        }

        String coordsMsg = x + " " + y + " " + z + " " + worldDisplay;
        String chatFormat = plugin.getConfig().getString("chat.team-format", "&8[&bTeamChat&8] %player_prefix%%player%&f: %message%");

        String playerPrefix = LuckPermsUtils.getPrefix(player);
        if (playerPrefix == null) playerPrefix = "";

        String formattedMessage = ColorUtils.format(chatFormat
                .replace("%player_prefix%", playerPrefix)
                .replace("%player%", player.getName())
                .replace("%message%", coordsMsg));

        if (inReTeam) {
            PiolaReTeam reteam = ReTeamManager.getReTeam(player);
            for (UUID memberUuid : reteam.getMembers()) {
                Player member = Bukkit.getPlayer(memberUuid);
                if (member != null && member.isOnline()) {
                    member.sendMessage(formattedMessage);
                }
            }
        } else {
            PiolaTeam team = TeamManager.getTeam(player);
            for (UUID memberUuid : team.getMembers()) {
                Player member = Bukkit.getPlayer(memberUuid);
                if (member != null && member.isOnline()) {
                    member.sendMessage(formattedMessage);
                }
            }
        }
        return Command.SINGLE_SUCCESS;
    }

    private static int executeInvite(CommandContext<CommandSourceStack> context, PermaPiola plugin, LanguageManager lang) {
        Player player = getPlayerOrError(context);
        if (player == null) return Command.SINGLE_SUCCESS;

        PiolaTeam inviterTeam = TeamManager.getTeam(player);
        if (inviterTeam == null) { sendFramedMessage(player, lang.getMsg(player, "teams.not-in-team"), lang); return Command.SINGLE_SUCCESS; }

        String targetName = context.getArgument("target", String.class);
        Player target = Bukkit.getPlayerExact(targetName);

        if (target == null) {
            sendFramedMessage(player, lang.getMsg(player, "commands.generic.player-offline"), lang);
            return Command.SINGLE_SUCCESS;
        }

        if (target.equals(player)) { sendFramedMessage(player, lang.getMsg(player, "teams.invite.cant-invite-self"), lang); return Command.SINGLE_SUCCESS; }
        if (inviterTeam.hasMember(target.getUniqueId())) { sendFramedMessage(player, lang.getMsg(player, "teams.invite.already-in-your-team").replace("%target%", target.getName()), lang); return Command.SINGLE_SUCCESS; }
        if (TeamManager.hasTeam(target)) { sendFramedMessage(player, lang.getMsg(player, "teams.invite.target-already-in-team"), lang); return Command.SINGLE_SUCCESS; }
        if (TeamManager.hasInvite(player, target)) { sendFramedMessage(player, lang.getMsg(player, "teams.invite.already-invited"), lang); return Command.SINGLE_SUCCESS; }
        if (inviterTeam.isFull()) { sendFramedMessage(player, lang.getMsg(player, "teams.team-full").replace("%size%", String.valueOf(inviterTeam.getMaxSize())), lang); return Command.SINGLE_SUCCESS; }

        final String targetPrefix = LuckPermsUtils.getPrefix(target) != null ? LuckPermsUtils.getPrefix(target) : "";

        sendFramedMessage(player, lang.getMsg(player, "teams.invite.sent")
                .replace("%target_prefix%", targetPrefix)
                .replace("%target%", target.getName()), lang);

        final String playerPrefix = LuckPermsUtils.getPrefix(player) != null ? LuckPermsUtils.getPrefix(player) : "";

        String lineStr = lang.getMsg(target, "commands.generic.line");
        target.sendMessage(ColorUtils.format(lineStr));

        String receivedMsg = lang.getMsg(target, "teams.invite.received")
                .replace("%player_prefix%", playerPrefix)
                .replace("%player%", player.getName());

        String hoverRaw = lang.getMsg(target, "teams.invite.hover")
                .replace("%player%", player.getName())
                .replace("\\n", "\n");

        Component hoverComp = LegacyComponentSerializer.legacySection().deserialize(ColorUtils.format(hoverRaw));

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
                if (player.isOnline()) {
                    sendFramedMessage(player, lang.getMsg(player, "teams.invite.expired-sender")
                            .replace("%target_prefix%", targetPrefix)
                            .replace("%target%", target.getName()), lang);
                }
                if (target.isOnline()) {
                    sendFramedMessage(target, lang.getMsg(target, "teams.invite.expired-target")
                            .replace("%player_prefix%", playerPrefix)
                            .replace("%player%", player.getName()), lang);
                }
            }
        }, 1200L).getTaskId();

        TeamManager.addInvite(player, target, taskId);

        return Command.SINGLE_SUCCESS;
    }

    private static int executeAccept(CommandContext<CommandSourceStack> context, PermaPiola plugin, LanguageManager lang) {
        Player player = getPlayerOrError(context);
        if (player == null) return Command.SINGLE_SUCCESS;

        if (TeamManager.hasTeam(player)) { sendFramedMessage(player, lang.getMsg(player, "teams.already-in-team"), lang); return Command.SINGLE_SUCCESS; }

        String targetName = context.getArgument("target", String.class);
        Player inviter = Bukkit.getPlayerExact(targetName);

        if (inviter == null) {
            sendFramedMessage(player, lang.getMsg(player, "commands.generic.player-offline"), lang);
            return Command.SINGLE_SUCCESS;
        }

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

        TabManager.updatePlayerTab(player, plugin);

        String inviterPrefix = LuckPermsUtils.getPrefix(inviter);
        if (inviterPrefix == null) inviterPrefix = "";

        sendFramedMessage(player, lang.getMsg(player, "teams.accept.success")
                .replace("%player_prefix%", inviterPrefix)
                .replace("%player%", inviter.getName()), lang);

        String playerPrefix = LuckPermsUtils.getPrefix(player);
        if (playerPrefix == null) playerPrefix = "";

        String broadcastMsg = lang.getMsg(player, "teams.accept.broadcast")
                .replace("%new_member_prefix%", playerPrefix)
                .replace("%new_member%", player.getName());

        for (java.util.UUID memberUuid : team.getMembers()) {
            if (!memberUuid.equals(player.getUniqueId())) {
                Player member = Bukkit.getPlayer(memberUuid);
                if (member != null && member.isOnline()) {
                    sendFramedMessage(member, broadcastMsg, lang);
                }
            }
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

        if (!TeamManager.hasTeam(player) && !ReTeamManager.hasReTeam(player)) {
            sendFramedMessage(player, lang.getMsg(player, "teams.not-in-team"), lang);
            return Command.SINGLE_SUCCESS;
        }

        if (state.equals("on")) {
            if (TeamManager.hasGlowEnabled(player)) { sendFramedMessage(player, lang.getMsg(player, "teams.glow.already-on"), lang); return Command.SINGLE_SUCCESS; }

            TeamManager.toggleGlow(player);
            GlowManager.updateGlowFor(player);

            sendFramedMessage(player, lang.getMsg(player, "teams.glow.toggled-on"), lang);
        } else {
            if (!TeamManager.hasGlowEnabled(player)) { sendFramedMessage(player, lang.getMsg(player, "teams.glow.already-off"), lang); return Command.SINGLE_SUCCESS; }

            TeamManager.toggleGlow(player);
            GlowManager.updateGlowFor(player);

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

        String disbandMsg = lang.getMsg(sender, "teams.leave.disbanded");
        List<UUID> formerMembers = new ArrayList<>(team.getMembers());

        TeamManager.deleteTeamForcefully(team);

        for (UUID memberUuid : formerMembers) {
            Player member = Bukkit.getPlayer(memberUuid);
            if (member != null && member.isOnline()) {
                sendFramedMessage(member, disbandMsg, lang);
                TabManager.updatePlayerTab(member, plugin);
            }
        }

        sendFramedMessage(sender, lang.getMsg(sender, "teams.staff.delete-success").replace("%team%", team.getDisplayName()), lang);
        return Command.SINGLE_SUCCESS;
    }

    private static int executeForceJoin(CommandContext<CommandSourceStack> context, PermaPiola plugin, LanguageManager lang) {
        CommandSender sender = context.getSource().getSender();
        try {
            PiolaTeam team = TeamManager.getTeamByName(context.getArgument("teamName", String.class));
            if (team == null) { sendFramedMessage(sender, lang.getMsg(sender, "teams.staff.team-not-found"), lang); return Command.SINGLE_SUCCESS; }

            Player target = context.getArgument("target", PlayerSelectorArgumentResolver.class).resolve(context.getSource()).get(0);

            String targetPrefix = LuckPermsUtils.getPrefix(target);
            if (targetPrefix == null) targetPrefix = "";

            if (TeamManager.hasTeam(target)) {
                PiolaTeam targetTeam = TeamManager.getTeam(target);
                sendFramedMessage(sender, lang.getMsg(sender, "teams.staff.forcejoin-other-already-different")
                        .replace("%player_prefix%", targetPrefix)
                        .replace("%player%", target.getName())
                        .replace("%current_team%", targetTeam.getDisplayName()), lang);
                return Command.SINGLE_SUCCESS;
            }

            TeamManager.addPlayerToTeam(target, team);
            TabManager.updatePlayerTab(target, plugin);

            sendFramedMessage(target, lang.getMsg(target, "teams.staff.forcejoin-target").replace("%team%", team.getDisplayName()), lang);

            String broadcastMsg = lang.getMsg(sender, "teams.staff.forcejoin-broadcast")
                    .replace("%player_prefix%", targetPrefix)
                    .replace("%player%", target.getName());

            for (UUID memberUuid : team.getMembers()) {
                if (!memberUuid.equals(target.getUniqueId())) {
                    Player member = Bukkit.getPlayer(memberUuid);
                    if (member != null && member.isOnline()) {
                        sendFramedMessage(member, broadcastMsg, lang);
                    }
                }
            }

            if (!sender.equals(target)) {
                sendFramedMessage(sender, lang.getMsg(sender, "teams.staff.forcejoin-success")
                        .replace("%player_prefix%", targetPrefix)
                        .replace("%player%", target.getName())
                        .replace("%team%", team.getDisplayName()), lang);
            }

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

            String targetPrefix = LuckPermsUtils.getPrefix(target);
            if (targetPrefix == null) targetPrefix = "";

            if (team == null) {
                sendFramedMessage(sender, lang.getMsg(sender, "teams.staff.forceleave-other-no-team")
                        .replace("%player_prefix%", targetPrefix)
                        .replace("%player%", target.getName()), lang);
                return Command.SINGLE_SUCCESS;
            }

            if (team.isLeader(target.getUniqueId())) {
                List<UUID> formerMembers = new ArrayList<>(team.getMembers());
                TeamManager.deleteTeamForcefully(team);

                for (UUID memberUuid : formerMembers) {
                    Player member = Bukkit.getPlayer(memberUuid);
                    if (member != null && member.isOnline()) {
                        if (!memberUuid.equals(target.getUniqueId())) {
                            sendFramedMessage(member, lang.getMsg(member, "teams.leave.disbanded"), lang);
                        }
                        TabManager.updatePlayerTab(member, plugin);
                    }
                }
            } else {
                for (UUID memberUuid : team.getMembers()) {
                    if (!memberUuid.equals(target.getUniqueId())) {
                        Player member = Bukkit.getPlayer(memberUuid);
                        if (member != null && member.isOnline()) {
                            String broadcastMsg = lang.getMsg(member, "teams.staff.forceleave-broadcast")
                                    .replace("%player_prefix%", targetPrefix)
                                    .replace("%player%", target.getName());
                            sendFramedMessage(member, broadcastMsg, lang);
                        }
                    }
                }
                TeamManager.removePlayerFromTeam(target);
                TabManager.updatePlayerTab(target, plugin);
            }

            sendFramedMessage(target, lang.getMsg(target, "teams.staff.forceleave-target"), lang);

            if (!sender.equals(target)) {
                sendFramedMessage(sender, lang.getMsg(sender, "teams.staff.forceleave-success")
                        .replace("%player_prefix%", targetPrefix)
                        .replace("%player%", target.getName()), lang);
            }

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
            String formattedLeader = "";
            List<String> formattedMembers = new ArrayList<>();

            for (UUID memberUuid : t.getMembers()) {
                String pFormat = formatPlayerForList(memberUuid, sender, plugin, lang);
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