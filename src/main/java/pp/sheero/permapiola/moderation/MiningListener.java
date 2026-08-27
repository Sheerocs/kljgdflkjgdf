package pp.sheero.permapiola.moderation;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import pp.sheero.permapiola.PermaPiola;
import pp.sheero.permapiola.core.LanguageManager;
import pp.sheero.permapiola.utils.ColorUtils;

import java.util.List;

public class MiningListener implements Listener {

    private final PermaPiola plugin;
    private final LanguageManager lang;

    public MiningListener(PermaPiola plugin, LanguageManager lang) {
        this.plugin = plugin;
        this.lang = lang;
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        if (!plugin.getConfig().getBoolean("alerts.mining.enabled")) return;

        Material blockType = event.getBlock().getType();
        List<String> watchedBlocks = plugin.getConfig().getStringList("alerts.mining.blocks");

        if (watchedBlocks.contains(blockType.name())) {
            Player player = event.getPlayer();

            if (player.hasPermission("permapiola.admin.mining")) return;

            String coords = event.getBlock().getX() + ", " + event.getBlock().getY() + ", " + event.getBlock().getZ();
            String blockName = blockType.name().toLowerCase().replace("_", " ");

            for (Player admin : Bukkit.getOnlinePlayers()) {
                if (admin.hasPermission("permapiola.admin.mining")) {
                    if (admin.getGameMode() == GameMode.CREATIVE || admin.getGameMode() == GameMode.SPECTATOR) {

                        String rawMessage = lang.getMsg(admin, "alerts.mining.message")
                                .replace("%player%", player.getName())
                                .replace("%block%", blockName)
                                .replace("%coords%", coords);

                        String hoverText = lang.getMsg(admin, "alerts.mining.hover")
                                .replace("%player%", player.getName());

                        Component message = LegacyComponentSerializer.legacySection().deserialize(ColorUtils.format(rawMessage));

                        Component hoverComponent = LegacyComponentSerializer.legacySection().deserialize(ColorUtils.format(hoverText));

                        message = message
                                .hoverEvent(HoverEvent.showText(hoverComponent))
                                .clickEvent(ClickEvent.runCommand("/tp " + player.getName()));

                        admin.sendMessage(message);
                    }
                }
            }
        }
    }
}