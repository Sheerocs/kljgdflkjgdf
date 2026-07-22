package pp.sheero.permapiola.utils;

import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.entity.Player;

public class ParticleUtils {
    public static void spawnGamemodeParticles(Player player, GameMode mode) {
        Location loc = player.getLocation().add(0, 1.5, 0);
        switch (mode) {
            case SURVIVAL: player.getWorld().spawnParticle(Particle.COMPOSTER, loc, 25, 0, 0, 0, 1); break;
            case CREATIVE:
                player.getWorld().spawnParticle(Particle.LARGE_SMOKE, loc, 25, 0, 0, 0, 1);
                player.getWorld().spawnParticle(Particle.CRIMSON_SPORE, loc, 25, 0, 0, 0, 1);
                break;
            case ADVENTURE: player.getWorld().spawnParticle(Particle.END_ROD, loc, 25, 0, 0, 0, 1); break;
            case SPECTATOR: player.getWorld().spawnParticle(Particle.POOF, loc, 25, 0, 0, 0, 1); break;
        }
    }
}