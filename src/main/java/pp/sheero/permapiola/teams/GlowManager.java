package pp.sheero.permapiola.teams;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.ProtocolManager;
import com.comphenix.protocol.events.PacketAdapter;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.events.PacketEvent;
import com.comphenix.protocol.wrappers.WrappedDataValue;
import com.comphenix.protocol.wrappers.WrappedDataWatcher;
import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import pp.sheero.permapiola.PermaPiola;

import java.util.ArrayList;
import java.util.List;

public class GlowManager {

    public static void registerGlowPacketListener(PermaPiola plugin) {
        Bukkit.getScheduler().runTask(plugin, () -> {
            ProtocolManager protocolManager = ProtocolLibrary.getProtocolManager();

            if (protocolManager == null) {
                plugin.getLogger().severe("¡No se pudo conectar con ProtocolLib! El Glow de equipos estará desactivado.");
                return;
            }

            protocolManager.addPacketListener(new PacketAdapter(plugin, PacketType.Play.Server.ENTITY_METADATA) {
                @Override
                public void onPacketSending(PacketEvent event) {
                    Player observer = event.getPlayer();

                    if (!TeamManager.hasGlowEnabled(observer)) return;

                    PacketContainer originalPacket = event.getPacket();
                    Entity entity = originalPacket.getEntityModifier(observer.getWorld()).readSafely(0);

                    if (!(entity instanceof Player)) return;
                    Player target = (Player) entity;

                    if (observer.equals(target)) return;

                    org.bukkit.scoreboard.Team observerTeam = TeamManager.getTeam(observer);
                    if (observerTeam == null || !observerTeam.hasEntry(target.getName())) return;

                    PacketContainer clonedPacket = originalPacket.deepClone();

                    List<WrappedDataValue> dataValues = clonedPacket.getDataValueCollectionModifier().readSafely(0);
                    if (dataValues == null) return;

                    List<WrappedDataValue> newDataValues = new ArrayList<>();
                    boolean indexZeroFound = false;

                    for (WrappedDataValue value : dataValues) {
                        if (value.getIndex() == 0) {
                            byte bitmask = (byte) value.getValue();
                            bitmask |= 0x40;
                            newDataValues.add(new WrappedDataValue(0, WrappedDataWatcher.Registry.get(Byte.class), bitmask));
                            indexZeroFound = true;
                        } else {
                            newDataValues.add(value);
                        }
                    }

                    if (!indexZeroFound) {
                        newDataValues.add(new WrappedDataValue(0, WrappedDataWatcher.Registry.get(Byte.class), (byte) 0x40));
                    }

                    clonedPacket.getDataValueCollectionModifier().write(0, newDataValues);

                    event.setPacket(clonedPacket);
                }
            });
        });
    }
}