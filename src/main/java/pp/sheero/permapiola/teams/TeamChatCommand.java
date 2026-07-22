package pp.sheero.permapiola.teams;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import pp.sheero.permapiola.PermaPiola;
import pp.sheero.permapiola.managers.LanguageManager;
import pp.sheero.permapiola.utils.ColorUtils;
import pp.sheero.permapiola.utils.DeathStateManager;

import java.util.ArrayList;
import java.util.List;

public class TeamChatCommand implements CommandExecutor, TabCompleter {

    private final PermaPiola plugin;
    private final LanguageManager languageManager;

    public TeamChatCommand(PermaPiola plugin, LanguageManager languageManager) {
        this.plugin = plugin;
        this.languageManager = languageManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) return true;
        Player player = (Player) sender;

        boolean isDead = DeathStateManager.isDead(player.getUniqueId());
        boolean isStaff = player.hasPermission("permapiola.admin") || player.hasPermission("permapiola.staff");

        if (isDead && !isStaff) {
            player.sendMessage(ColorUtils.format(languageManager.getMsg(player, "commands.chat.dead-restricted")));
            return true;
        }

        if (!TeamManager.isTeamsEnabled()) {
            player.sendMessage(ColorUtils.format(languageManager.getMsg(player, "teams.system-disabled")));
            return true;
        }

        if (!TeamManager.hasTeam(player)) {
            player.sendMessage(ColorUtils.format(languageManager.getMsg(player, "teams.not-in-team")));
            return true;
        }

        if (args.length == 0) {
            player.sendMessage(ColorUtils.format(languageManager.getMsg(player, "commands.teamchat.usage")));
            return true;
        }

        String message = String.join(" ", args);
        TeamManager.sendTeamChatMessage(player, message, languageManager);
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        return new ArrayList<>();
    }
}