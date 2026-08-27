package pp.sheero.permapiola.moderation;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerCommandSendEvent;
import pp.sheero.permapiola.PermaPiola;
import pp.sheero.permapiola.core.LanguageManager;
import pp.sheero.permapiola.utils.ColorUtils;

import java.util.ArrayList;
import java.util.List;

public class CommandBlockerListener implements Listener {

    private final PermaPiola plugin;
    private final LanguageManager lang;

    public CommandBlockerListener(PermaPiola plugin, LanguageManager lang) {
        this.plugin = plugin;
        this.lang = lang;
    }

    private List<String> getDynamicWhitelist(Player player) {
        List<String> allowed = new ArrayList<>(plugin.getConfig().getStringList("command-blocker.default-commands"));

        if (player.hasPermission("permapiola.donor")) {
            allowed.addAll(plugin.getConfig().getStringList("command-blocker.donor-commands"));
        }

        if (player.hasPermission("permapiola.staff")) {
            allowed.addAll(plugin.getConfig().getStringList("command-blocker.staff-commands"));
        }

        allowed.replaceAll(String::toLowerCase);

        return allowed;
    }

    @EventHandler
    public void onCommandSend(PlayerCommandSendEvent event) {
        Player player = event.getPlayer();

        if (!player.hasPermission("permapiola.admin")) {
            List<String> allowedCommands = getDynamicWhitelist(player);
            event.getCommands().removeIf(command -> !allowedCommands.contains(command.toLowerCase()));
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onCommandPreprocess(PlayerCommandPreprocessEvent event) {
        Player player = event.getPlayer();
        String fullMessage = event.getMessage();

        String command = fullMessage.split(" ")[0].substring(1).toLowerCase();

        if (!player.hasPermission("permapiola.admin")) {
            List<String> allowedCommands = getDynamicWhitelist(player);

            if (command.contains(":") || !allowedCommands.contains(command)) {
                event.setCancelled(true);

                String typedText = fullMessage.substring(1);

                String line1 = lang.getMsg(player, "commands.generic.unknown-command-line1");
                String pointer = lang.getMsg(player, "commands.generic.unknown-command-pointer");

                Component messageComponent = LegacyComponentSerializer.legacySection().deserialize(ColorUtils.format(line1));

                Component typedPart = Component.text(typedText)
                        .color(NamedTextColor.RED)
                        .decorate(TextDecoration.UNDERLINED)
                        .clickEvent(ClickEvent.suggestCommand(fullMessage));

                Component pointerPart = LegacyComponentSerializer.legacySection().deserialize(ColorUtils.format(pointer))
                        .decorate(TextDecoration.ITALIC)
                        .clickEvent(ClickEvent.suggestCommand(fullMessage));

                Component finalMessage = messageComponent.append(typedPart).append(pointerPart);

                player.sendMessage(finalMessage);
            }
        }
    }
}