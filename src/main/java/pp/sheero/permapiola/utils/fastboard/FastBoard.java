package pp.sheero.permapiola.utils.fastboard;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.Criteria;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class FastBoard {

    private final Player player;
    private final Scoreboard scoreboard;
    private final Objective objective;
    private Component title = Component.empty();
    private final List<Component> lines = new ArrayList<>();
    private boolean deleted = false;

    public FastBoard(Player player) {
        this.player = Objects.requireNonNull(player, "player");
        this.scoreboard = Bukkit.getScoreboardManager().getNewScoreboard();
        this.objective = this.scoreboard.registerNewObjective("fastboard", Criteria.DUMMY, Component.empty());
        this.objective.setDisplaySlot(DisplaySlot.SIDEBAR);
        player.setScoreboard(this.scoreboard);
    }

    public void updateTitle(Component title) {
        this.title = Objects.requireNonNull(title, "title");
        this.objective.displayName(title);
    }

    public void updateLines(Component... lines) { updateLines(List.of(lines)); }

    public void updateLines(List<Component> newLines) {
        Objects.requireNonNull(newLines, "lines");
        if (newLines.size() > 15) throw new IllegalArgumentException("La Scoreboard no puede tener más de 15 líneas.");

        for (int i = newLines.size(); i < this.lines.size(); i++) {
            this.scoreboard.resetScores(getLineName(i));
        }

        for (int i = 0; i < newLines.size(); i++) {
            Component line = newLines.get(i);
            int score = newLines.size() - i - 1;

            String name = getLineName(i);
            org.bukkit.scoreboard.Team team = this.scoreboard.getTeam(name);
            if (team == null) {
                team = this.scoreboard.registerNewTeam(name);
                team.addEntry(name);
            }

            team.prefix(line);
            this.objective.getScore(name).setScore(score);
            this.objective.getScore(name).numberFormat(io.papermc.paper.scoreboard.numbers.NumberFormat.blank());
        }

        this.lines.clear();
        this.lines.addAll(newLines);
    }

    private String getLineName(int index) {
        return org.bukkit.ChatColor.values()[index].toString() + org.bukkit.ChatColor.RESET;
    }

    public Player getPlayer() { return player; }

    public void delete() {
        if (this.deleted) return;
        this.player.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
        this.deleted = true;
    }

    public boolean isDeleted() { return deleted; }
}