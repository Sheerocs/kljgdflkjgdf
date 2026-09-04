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
import java.util.UUID;

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

                    boolean shouldGlow = false;
                    PiolaReTeam reteam = ReTeamManager.getReTeam(observer);

                    if (reteam != null) {
                        shouldGlow = reteam.hasMember(target.getUniqueId());
                    } else {
                        PiolaTeam observerTeam = TeamManager.getTeam(observer);
                        if (observerTeam != null) {
                            shouldGlow = observerTeam.hasMember(target.getUniqueId());
                        }
                    }

                    if (!shouldGlow) return;

                    PacketContainer clonedPacket = originalPacket.deepClone();

                    List<WrappedDataValue> dataValues = clonedPacket.getDataValueCollectionModifier().readSafely(0);
                    if (dataValues == null) dataValues = new ArrayList<>();

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

    public static void updateGlowFor(Player observer) {
        ProtocolManager pm = ProtocolLibrary.getProtocolManager();
        if (pm == null) return;

        List<UUID> targetMembers = new ArrayList<>();
        PiolaReTeam reteam = ReTeamManager.getReTeam(observer);

        if (reteam != null) {
            targetMembers.addAll(reteam.getMembers());
        } else {
            PiolaTeam team = TeamManager.getTeam(observer);
            if (team != null) {
                targetMembers.addAll(team.getMembers());
            }
        }

        if (targetMembers.isEmpty()) return;

        for (UUID memberId : targetMembers) {
            if (memberId.equals(observer.getUniqueId())) continue;

            Player member = Bukkit.getPlayer(memberId);
            if (member != null && member.isOnline()) {
                try {
                    WrappedDataWatcher watcher = WrappedDataWatcher.getEntityWatcher(member);

                    PacketContainer packet = pm.createPacket(PacketType.Play.Server.ENTITY_METADATA);
                    packet.getIntegers().write(0, member.getEntityId());
                    packet.getDataValueCollectionModifier().write(0, watcher.toDataValueCollection());

                    pm.sendServerPacket(observer, packet);
                } catch (Exception ignored) {
                }
            }
        }
    }
}