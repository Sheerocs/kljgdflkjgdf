package pp.sheero.permapiola.managers;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scoreboard.Team;
import org.bukkit.util.Vector;
import pp.sheero.permapiola.PermaPiola;
import pp.sheero.permapiola.dementialwheel.DementialEventType;
import pp.sheero.permapiola.teams.TeamManager;
import pp.sheero.permapiola.utils.ColorUtils;
import pp.sheero.permapiola.utils.LuckPermsUtils;
import pp.sheero.permapiola.utils.fastboard.FastBoard;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class ScoreboardManager implements Listener {

    private final PermaPiola plugin;
    private final LanguageManager lang;
    private final Map<UUID, FastBoard> boards = new ConcurrentHashMap<>();
    private final Set<UUID> disabledPlayers = Collections.newSetFromMap(new ConcurrentHashMap<>());

    private static final LegacyComponentSerializer SECTION = LegacyComponentSerializer.legacySection();
    private static final PlainTextComponentSerializer PLAIN = PlainTextComponentSerializer.plainText();

    private static final Map<DementialEventType, String> EVENT_NAMES_CACHE = new EnumMap<>(DementialEventType.class);

    static {
        for (DementialEventType type : DementialEventType.values()) {
            if (type == DementialEventType.NONE) continue;
            String rawName = type.name().replace('_', ' ').toLowerCase();
            StringBuilder sb = new StringBuilder("&c");
            for (String word : rawName.split(" ")) {
                sb.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1)).append(" ");
            }
            EVENT_NAMES_CACHE.put(type, sb.toString().trim());
        }
    }

    public ScoreboardManager(PermaPiola plugin, LanguageManager lang) {
        this.plugin = plugin;
        this.lang = lang;
        Bukkit.getScheduler().runTaskTimer(plugin, this::updateBoards, 10L, 10L);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            Player player = e.getPlayer();
            if (player != null && player.isOnline()) {
                if (isEnabled(player.getUniqueId())) {
                    addBoard(player);
                }
            }
        }, 10L);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        removeBoard(e.getPlayer());
    }

    public void toggle(Player player) {
        UUID uuid = player.getUniqueId();
        if (disabledPlayers.contains(uuid)) {
            disabledPlayers.remove(uuid);
            addBoard(player);
            player.sendMessage(SECTION.deserialize(ColorUtils.format(lang.getMsg(player, "commands.scoreboard.enabled"))));
        } else {
            disabledPlayers.add(uuid);
            removeBoard(player);
            player.sendMessage(SECTION.deserialize(ColorUtils.format(lang.getMsg(player, "commands.scoreboard.disabled"))));
        }
    }

    public void setScoreboardState(Player player, boolean enable) {
        UUID uuid = player.getUniqueId();

        if (enable && disabledPlayers.contains(uuid)) {
            disabledPlayers.remove(uuid);
            addBoard(player);
            player.sendMessage(SECTION.deserialize(ColorUtils.format(lang.getMsg(player, "commands.scoreboard.enabled"))));

        } else if (!enable && !disabledPlayers.contains(uuid)) {
            disabledPlayers.add(uuid);
            removeBoard(player);
            player.sendMessage(SECTION.deserialize(ColorUtils.format(lang.getMsg(player, "commands.scoreboard.disabled"))));
        }
    }

    public boolean isEnabled(UUID uuid) {
        return !disabledPlayers.contains(uuid);
    }

    private String getDirectionArrow(Player player, Player target) {
        if (!player.getWorld().equals(target.getWorld())) return "&c✖";

        Vector playerLookDir = player.getLocation().getDirection().setY(0).normalize();
        Vector targetDir = target.getLocation().toVector().subtract(player.getLocation().toVector()).setY(0).normalize();

        double angle = Math.toDegrees(playerLookDir.angle(targetDir));
        if ((playerLookDir.getX() * targetDir.getZ()) - (playerLookDir.getZ() * targetDir.getX()) > 0) {
            angle = 360.0 - angle;
        }

        String arrow = (angle <= 45.0 || angle > 315.0) ? "⮝" :
                (angle > 45.0 && angle <= 135.0) ? "⮜" :
                        (angle > 135.0 && angle <= 225.0) ? "⮟" : "⮞";

        double yDiff = target.getLocation().getY() - player.getLocation().getY();
        String color = (yDiff >= 2.0) ? "&a" : (yDiff <= -2.0) ? "&c" : "&7";

        return color + arrow;
    }

    public void addBoard(Player player) {
        FastBoard board = new FastBoard(player);
        boards.put(player.getUniqueId(), board);

        org.bukkit.scoreboard.Scoreboard sb = player.getScoreboard();

        if (sb.getObjective("tabHealth") == null) {
            org.bukkit.scoreboard.Objective tabHealth = sb.registerNewObjective("tabHealth", org.bukkit.scoreboard.Criteria.HEALTH, Component.empty());
            tabHealth.setDisplaySlot(org.bukkit.scoreboard.DisplaySlot.PLAYER_LIST);
            tabHealth.setRenderType(org.bukkit.scoreboard.RenderType.HEARTS);
        }

        if (sb.getObjective("nameHealth") == null) {
            org.bukkit.scoreboard.Objective nameHealth = sb.registerNewObjective("nameHealth", org.bukkit.scoreboard.Criteria.HEALTH, Component.text("❤").color(net.kyori.adventure.text.format.NamedTextColor.RED));
            nameHealth.setDisplaySlot(org.bukkit.scoreboard.DisplaySlot.BELOW_NAME);
            nameHealth.setRenderType(org.bukkit.scoreboard.RenderType.HEARTS);
        }

        updateBoard(player, board);
    }

    public void removeBoard(Player player) {
        FastBoard board = boards.remove(player.getUniqueId());
        if (board != null) board.delete();
    }

    private void updateBoards() {
        for (FastBoard board : boards.values()) {
            Player player = board.getPlayer();
            if (player != null && player.isOnline()) {
                updateBoard(player, board);
            }
        }
    }

    private void updateBoard(Player player, FastBoard board) {
        List<Component> lines = new ArrayList<>();

        String day = String.valueOf(plugin.getDayManager().getCurrentDay());
        String rankStr = ColorUtils.format(getLuckPermsRank(player));

        double tpsVal = Bukkit.getServer().getTPS()[0];
        String tpsStr = String.valueOf(Math.round(Math.min(20.0, tpsVal) * 100.0) / 100.0);
        String finalTps = ((tpsVal >= 18.0) ? "&a" : (tpsVal >= 15.0) ? "&e" : "&c") + tpsStr;

        Team playerTeam = TeamManager.getTeam(player);
        String teamName = (playerTeam != null) ? PLAIN.serialize(playerTeam.displayName()) : "";

        board.updateTitle(SECTION.deserialize(ColorUtils.format(lang.getMsg(player, "scoreboard.title"))));

        List<String> configLines = lang.getMsgList(player, "scoreboard.lines");

        for (String configLine : configLines) {
            if (configLine.contains("%teammates_section%")) {
                if (playerTeam != null) {
                    String memberFormat = lang.getMsg(player, "scoreboard.teammate-format");
                    for (String memberName : playerTeam.getEntries()) {
                        if (memberName.equals(player.getName())) continue;

                        Player tm = Bukkit.getPlayerExact(memberName);
                        if (tm != null && tm.isOnline()) {
                            double hpVal = Math.round(tm.getHealth() * 10.0) / 10.0;
                            int ping = tm.getPing();

                            String pColor = getPingHexColor(ping);

                            lines.add(SECTION.deserialize(ColorUtils.format(memberFormat
                                    .replace("%arrow%", getDirectionArrow(player, tm))
                                    .replace("%player%", tm.getName())
                                    .replace("%hp%", String.valueOf(hpVal))
                                    .replace("%ping_color%", pColor)
                                    .replace("%ping%", String.valueOf(ping)))));
                        }
                    }
                }
                continue;
            }

            if (configLine.contains("%demential_events%")) {
                List<String> activeEventNames = new ArrayList<>();
                for (DementialEventType type : EVENT_NAMES_CACHE.keySet()) {
                    if (plugin.getDementialWheelManager().hasEvent(type)) {
                        activeEventNames.add(EVENT_NAMES_CACHE.get(type));
                    }
                }

                if (!activeEventNames.isEmpty()) {
                    lines.add(SECTION.deserialize(ColorUtils.format(lang.getMsg(player, "scoreboard.event-header"))));

                    String formatDouble = lang.getMsg(player, "scoreboard.event-format-double");
                    String formatSingle = lang.getMsg(player, "scoreboard.event-format-single");

                    for (int i = 0; i < activeEventNames.size(); i += 2) {
                        if (i + 1 < activeEventNames.size()) {
                            lines.add(SECTION.deserialize(ColorUtils.format(formatDouble
                                    .replace("%event1%", activeEventNames.get(i))
                                    .replace("%event2%", activeEventNames.get(i + 1)))));
                        } else {
                            lines.add(SECTION.deserialize(ColorUtils.format(formatSingle
                                    .replace("%event%", activeEventNames.get(i)))));
                        }
                    }
                }
                continue;
            }

            if (configLine.contains("%team%") && playerTeam == null) continue;

            lines.add(SECTION.deserialize(ColorUtils.format(configLine
                    .replace("%day%", day)
                    .replace("%rank%", rankStr)
                    .replace("%team%", teamName)
                    .replace("%tps%", finalTps))));
        }

        board.updateLines(lines);
    }

    private String getLuckPermsRank(Player player) {
        try {
            net.luckperms.api.LuckPerms api = net.luckperms.api.LuckPermsProvider.get();
            net.luckperms.api.model.user.User user = api.getUserManager().getUser(player.getUniqueId());

            if (user != null) {
                String groupName = user.getPrimaryGroup();
                net.luckperms.api.model.group.Group group = api.getGroupManager().getGroup(groupName);

                if (group != null && group.getDisplayName() != null && !group.getDisplayName().trim().isEmpty()) {
                    return group.getDisplayName();
                }
            }
        } catch (Exception ignored) {}

        String prefix = LuckPermsUtils.getPrefix(player);
        return (prefix != null && !prefix.trim().isEmpty() && !prefix.trim().equals("&7")) ? prefix : "";
    }

    private String getPingHexColor(int latency) {
        float hue;
        if (latency <= 50) {
            hue = 0.33333334F;
        } else if (latency >= 250) {
            hue = 0.0F;
        } else {
            float ratio = (float) (latency - 50) / 200.0F;
            hue = 0.33333334F - ratio * 0.33333334F;
        }

        int argb = hsbToArgb(hue, 0.85F, 1.0F);

        return String.format("#%06X", (argb & 0xFFFFFF));
    }

    private int hsbToArgb(float hue, float saturation, float brightness) {
        int r, g, b;
        if (saturation == 0.0F) {
            r = g = b = (int) (brightness * 255.0F + 0.5F);
        } else {
            float h = (hue - (float) Math.floor((double) hue)) * 6.0F;
            int sector = (int) h;
            float f = h - (float) sector;
            float p = brightness * (1.0F - saturation);
            float q = brightness * (1.0F - saturation * f);
            float t = brightness * (1.0F - saturation * (1.0F - f));
            switch (sector) {
                case 0: r = v(brightness); g = v(t); b = v(p); break;
                case 1: r = v(q); g = v(brightness); b = v(p); break;
                case 2: r = v(p); g = v(brightness); b = v(t); break;
                case 3: r = v(p); g = v(q); b = v(brightness); break;
                case 4: r = v(t); g = v(p); b = v(brightness); break;
                default: r = v(brightness); g = v(p); b = v(q);
            }
        }
        return -16777216 | r << 16 | g << 8 | b;
    }

    private int v(float component) {
        return (int) (component * 255.0F + 0.5F);
    }
}