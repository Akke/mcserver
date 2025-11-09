package com.akkelw.potionsystem.util;

import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.function.Predicate;

public final class ParticleUtil {
    private ParticleUtil() {}

    public static Location center(Block b) {
        return b.getLocation().add(0.5, 0.5, 0.5);
    }

    /* ---------- One-shot helpers ---------- */

    /** Quick tiny splash at the surface. */
    public static void splash(Block block) {
        Location c = center(block).add(0, 0.25, 0);
        World w = block.getWorld();
        // upward bubbles + splash droplets
        w.spawnParticle(Particle.BUBBLE_POP, c, 6, 0.20, 0.05, 0.20, 0.01);
        w.spawnParticle(Particle.SPLASH, c, 8, 0.25, 0.05, 0.25, 0.02);
    }

    /** Small steam burst (nice when heat increases). */
    public static void steam(Block block) {
        Location c = center(block).add(0, 0.35, 0);
        World w = block.getWorld();
        w.spawnParticle(Particle.CLOUD, c, 8, 0.18, 0.12, 0.18, 0.02);
        w.spawnParticle(Particle.SMALL_FLAME, c, 2, 0.05, 0.02, 0.05, 0.0);
    }

    /** Arcane flicker (if you want magical stir/heating). */
    public static void arcane(Block block) {
        Location c = center(block).add(0, 0.30, 0);
        block.getWorld().spawnParticle(Particle.ENCHANT, c, 12, 0.3, 0.15, 0.3, 0.0);
    }

    /* ---------- Looping “boil” effect ---------- */

    /**
     * Loop bubbling for `seconds`. Returns the task so you can cancel early.
     * @param intervalTicks how often to pulse (e.g., 5–10 ticks looks lively)
     */
    public static BukkitTask loopBoil(Plugin plugin, Block block, int seconds, int intervalTicks) {
        Location base = block.getLocation().add(0.5, 0.25, 0.5);
        World w = block.getWorld();
        long endAt = System.currentTimeMillis() + seconds * 1000L;

        Particle.DustOptions blue = new Particle.DustOptions(Color.fromRGB(0, 180, 255), 1.5f);
        Particle.DustTransition transition = new Particle.DustTransition(
                Color.fromRGB(0, 120, 255),
                Color.fromRGB(180, 255, 255),
                1.6f
        );

        return new BukkitRunnable() {
            @Override public void run() {
                if (System.currentTimeMillis() >= endAt
                        || !org.bukkit.Tag.CAULDRONS.isTagged(block.getType())) {
                    cancel();
                    return;
                }

                for (int i = 0; i < 4; i++) {
                    double ox = (Math.random() - 0.5) * 0.4;
                    double oz = (Math.random() - 0.5) * 0.4;
                    double oy = Math.random() * 0.3;
                    Location pLoc = base.clone().add(ox, oy, oz);

                    // Core blue glow bubble
                    w.spawnParticle(Particle.DUST, pLoc, 1, 0, 0, 0, 0, blue);
                    // Rising blue mist (transition)
                    w.spawnParticle(Particle.DUST_COLOR_TRANSITION, pLoc.clone().add(0, 0.1, 0),
                            1, 0.05, 0.05, 0.05, 0, transition);
                    // occasional splash
                    if (Math.random() < 0.3)
                        w.spawnParticle(Particle.SPLASH, pLoc.clone().add(0, 0.05, 0), 2, 0.1, 0.02, 0.1, 0.0);
                }

                // soft steam puff above
                if (Math.random() < 0.4)
                    w.spawnParticle(Particle.CLOUD, base.clone().add(0, 0.4, 0), 1, 0.15, 0.05, 0.15, 0.01);
            }
        }.runTaskTimer(plugin, 0L, Math.max(1, intervalTicks));
    }
}