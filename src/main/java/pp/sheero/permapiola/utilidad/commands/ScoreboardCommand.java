package pp.sheero.permapiola.utilidad.commands;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import pp.sheero.permapiola.managers.LanguageManager;
import pp.sheero.permapiola.managers.ScoreboardManager;
import pp.sheero.permapiola.utils.ColorUtils;

import java.util.ArrayList;
import java.util.List;

public class ScoreboardCommand implements CommandExecutor, TabCompleter {

    private final ScoreboardManager scoreboardManager;
    private final LanguageManager lang;

    public ScoreboardCommand(ScoreboardManager scoreboardManager, LanguageManager lang) {
        this.scoreboardManager = scoreboardManager;
        this.lang = lang;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(ColorUtils.format(lang.getMsg(sender, "commands.generic.console-only")));
            return true;
        }

        Player player = (Player) sender;

        scoreboardManager.toggle(player);

        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        return new ArrayList<>();
    }
}