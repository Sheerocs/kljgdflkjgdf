package pp.sheero.permapiola.utilidad.commands;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import pp.sheero.permapiola.PermaPiola;
import pp.sheero.permapiola.managers.EmoteManager;
import pp.sheero.permapiola.managers.LanguageManager;
import pp.sheero.permapiola.teams.TeamManager;
import pp.sheero.permapiola.utils.ColorUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public class PermaPiolaCommand implements CommandExecutor, TabCompleter {

    private final PermaPiola plugin;
    private final LanguageManager lang;
    private final EmoteManager emoteManager;

    private static final List<String> MAIN_ARGS = Collections.singletonList("reload");
    private static final List<String> RELOAD_FILES = Arrays.asList("config.yml", "emotes.yml", "es_ES.yml", "en_US.yml");

    public PermaPiolaCommand(PermaPiola plugin, LanguageManager lang, EmoteManager emoteManager) {
        this.plugin = plugin;
        this.lang = lang;
        this.emoteManager = emoteManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("permapiola.admin.reload")) {
            sender.sendMessage(ColorUtils.format(lang.getMsg(sender, "commands.generic.no-permission")));
            return true;
        }

        if (args.length == 0 || !args[0].equalsIgnoreCase("reload")) {
            sender.sendMessage(ColorUtils.format(lang.getMsg(sender, "commands.permapiola.usage")));
            return true;
        }

        if (args.length == 1) {
            plugin.reloadConfig();
            lang.loadLocales();
            emoteManager.loadEmotes();

            if (plugin.getDementialWheelManager() != null) plugin.getDementialWheelManager().loadConfigCache();
            if (plugin.getHurricaneManager() != null) plugin.getHurricaneManager().loadConfigCache();
            if (plugin.getAfkManager() != null) plugin.getAfkManager().loadConfigCache();
            if (plugin.getTotemListener() != null) plugin.getTotemListener().loadConfigCache();
            if (plugin.getDiscordManager() != null) plugin.getDiscordManager().loadConfigCache();
            if (plugin.getChatManager() != null) plugin.getChatManager().loadConfigCache();

            TeamManager.loadConfigCache(plugin);

            for (Player p : Bukkit.getOnlinePlayers()) {
                p.updateCommands();
            }

            sender.sendMessage(ColorUtils.format(lang.getMsg(sender, "commands.permapiola.reload-all")));
            return true;
        }

        String fileToReload = args[1].toLowerCase();

        switch (fileToReload) {
            case "config.yml":
                plugin.reloadConfig();
                if (plugin.getDementialWheelManager() != null) plugin.getDementialWheelManager().loadConfigCache();
                if (plugin.getHurricaneManager() != null) plugin.getHurricaneManager().loadConfigCache();
                if (plugin.getAfkManager() != null) plugin.getAfkManager().loadConfigCache();
                if (plugin.getTotemListener() != null) plugin.getTotemListener().loadConfigCache();
                if (plugin.getDiscordManager() != null) plugin.getDiscordManager().loadConfigCache();
                if (plugin.getChatManager() != null) plugin.getChatManager().loadConfigCache();

                TeamManager.loadConfigCache(plugin);

                sender.sendMessage(ColorUtils.format(lang.getMsg(sender, "commands.permapiola.reload-file").replace("%file%", "config.yml")));
                break;
            case "emotes.yml":
                emoteManager.loadEmotes();
                sender.sendMessage(ColorUtils.format(lang.getMsg(sender, "commands.permapiola.reload-file").replace("%file%", "emotes.yml")));
                break;
            case "es_es.yml":
            case "en_us.yml":
                lang.loadLocales();
                sender.sendMessage(ColorUtils.format(lang.getMsg(sender, "commands.permapiola.reload-file").replace("%file%", args[1])));
                break;
            default:
                sender.sendMessage(ColorUtils.format(lang.getMsg(sender, "commands.permapiola.invalid-file")));
                break;
        }

        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission("permapiola.admin.reload")) return new ArrayList<>();

        if (args.length == 1) {
            return MAIN_ARGS.stream()
                    .filter(c -> c.toLowerCase().startsWith(args[0].toLowerCase()))
                    .collect(Collectors.toList());
        } else if (args.length == 2 && args[0].equalsIgnoreCase("reload")) {
            return RELOAD_FILES.stream()
                    .filter(c -> c.toLowerCase().startsWith(args[1].toLowerCase()))
                    .collect(Collectors.toList());
        }

        return new ArrayList<>();
    }
}