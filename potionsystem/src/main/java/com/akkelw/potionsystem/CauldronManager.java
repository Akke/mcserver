package com.akkelw.potionsystem;

import com.akkelw.potionsystem.gui.RecipesGui;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import javax.annotation.Nullable;
import java.util.*;

public final class CauldronManager {
    private final Plugin plugin;
    private final Map<Location, UUID> cauldronToPlayer = new HashMap<>();
    private final Map<Location, RecipeProcess> cauldronToProcess = new HashMap<>();
    private final Map<UUID, LinkedHashSet<Location>> playerToCauldrons = new HashMap<>();

    public CauldronManager(Plugin plugin) {
        this.plugin = plugin;
    }

    private static Location blockLoc(Location loc) {
        if (loc == null) return null;

        Location normalized = loc.clone();
        normalized.setX(loc.getBlockX());
        normalized.setY(loc.getBlockY());
        normalized.setZ(loc.getBlockZ());
        normalized.setYaw(0);
        normalized.setPitch(0);
        return normalized;
    }

    public int getActiveCount(UUID playerId) { return countClaims(playerId); }

    public boolean isInUse(Location loc) {
        return cauldronToPlayer.containsKey(blockLoc(loc));
    }

    public UUID getUser(Location loc) {
        return cauldronToPlayer.get(blockLoc(loc));
    }

    public @Nullable Location getAnyCauldron(UUID playerId) {
        LinkedHashSet<Location> set = playerToCauldrons.get(playerId);
        return (set == null || set.isEmpty()) ? null : set.iterator().next();
    }

    public Set<Location> getCauldrons(UUID playerId) {
        LinkedHashSet<Location> set = playerToCauldrons.get(playerId);
        return (set == null) ? Collections.emptySet()
                : Collections.unmodifiableSet(set);
    }

    public static Location normalize(Block block) {
        Location loc = block.getLocation();
        return new Location(
                loc.getWorld(),
                loc.getBlockX(),
                loc.getBlockY(),
                loc.getBlockZ()
        );
    }

    public int getMaxCauldrons(int level) {
        if (level >= 99) return 5;
        return Math.min(4, 1 + (level / 20));
    }

    private int countClaims(UUID playerId) {
        int n = 0;
        for (UUID owner : cauldronToPlayer.values()) if (playerId.equals(owner)) n++;
        return n;
    }

    private boolean isAtCap(UUID playerId) {
        int level = plugin.getPotionLevel(playerId);
        int max = getMaxCauldrons(level);
        return countClaims(playerId) >= max;
    }

    public boolean startUsing(UUID playerId, Location rawLoc) {
        Player p = Bukkit.getPlayer(playerId);

        Location loc = blockLoc(rawLoc);

        UUID owner = cauldronToPlayer.get(loc);
        if (owner != null && !owner.equals(playerId)) {
            // someone else
            assert p != null;
            p.sendMessage(ChatColor.RED + "Someone is already using this cauldron!.");
            return false;
        }

        // If player already owns THIS cauldron, allow (not a new slot)
        if (owner != null && owner.equals(playerId)) {
            return true;
        }

        int level = plugin.getPotionLevel(playerId);
        int max = getMaxCauldrons(level);

        if (countClaims(playerId) >= max) {
            if (p != null) p.sendMessage(ChatColor.RED + "You’ve reached your cauldron limit (" + max + ").");
            return false; // no claim added, so others can still use it
        }

        playerToCauldrons.computeIfAbsent(playerId, k -> new LinkedHashSet<>()).add(loc);
        cauldronToPlayer.put(loc, playerId);

        return true;
    }

    public void onProcessEnded(Location rawLoc) {
        Location loc = blockLoc(rawLoc);

        // drop process
        cauldronToProcess.remove(loc);

        // free ownership
        UUID owner = cauldronToPlayer.remove(loc);
        if (owner != null) {
            var set = playerToCauldrons.get(owner);
            if (set != null) {
                set.remove(loc);
                if (set.isEmpty()) playerToCauldrons.remove(owner);
            }
        }
    }

    public boolean stopUsing(UUID playerId) {
        // stop ALL cauldrons for this player
        LinkedHashSet<Location> set = playerToCauldrons.remove(playerId);
        if (set == null || set.isEmpty()) return false;

        for (Location loc : new ArrayList<>(set)) {
            Location b = blockLoc(loc);
            cauldronToPlayer.remove(b);
            stopProcess(b); // cancels if running; onProcessEnded should clean remaining maps too
        }
        return true;
    }

    public boolean stopUsing(UUID playerId, Location rawLoc) {
        Location loc = blockLoc(rawLoc);
        LinkedHashSet<Location> set = playerToCauldrons.get(playerId);
        if (set == null || !set.remove(loc)) return false;

        cauldronToPlayer.remove(loc);
        if (set.isEmpty()) playerToCauldrons.remove(playerId);

        stopProcess(loc); // safe no-op if none
        return true;
    }

    public boolean stopUsing(Location rawLoc) {
        Location loc = blockLoc(rawLoc);

        // Don’t unclaim if a process is still running; let onProcessEnded do it.
        if (cauldronToProcess.containsKey(loc)) return false;

        UUID owner = cauldronToPlayer.remove(loc);
        if (owner == null) return false;

        var set = playerToCauldrons.get(owner);
        if (set != null) {
            set.remove(loc);
            if (set.isEmpty()) playerToCauldrons.remove(owner);
        }
        return true;
    }

    public void clearForWorld(String worldName) {
        // Collect first to avoid concurrent modification
        List<Location> toClear = new ArrayList<>();
        for (Location loc : cauldronToPlayer.keySet()) {
            if (loc.getWorld() != null && loc.getWorld().getName().equals(worldName)) {
                toClear.add(loc);
            }
        }

        for (Location raw : toClear) {
            Location loc = blockLoc(raw);
            UUID owner = cauldronToPlayer.remove(loc);

            // stop the process (if running). onProcessEnded should also remove mappings, but we’re already removing safely here.
            stopProcess(loc);

            if (owner != null) {
                LinkedHashSet<Location> set = playerToCauldrons.get(owner);
                if (set != null) {
                    set.remove(loc);
                    if (set.isEmpty()) playerToCauldrons.remove(owner);
                }
            }
        }
    }

    public void setWaterLevel(Block cauldron, Integer waterLevel) {
        if(waterLevel == 0) {
            cauldron.setType(Material.CAULDRON);
            return;
        }

        cauldron.setType(Material.WATER_CAULDRON, false);
        BlockData data = cauldron.getBlockData();
        if (data instanceof org.bukkit.block.data.Levelled) {
            org.bukkit.block.data.Levelled lvl = (org.bukkit.block.data.Levelled) data;
            lvl.setLevel(waterLevel);   // 3
            cauldron.setBlockData(lvl, false);
        }
    }

    public void emitSound(Block cauldron, Sound sound, float volume, boolean randomPitch) {
        for (Player p : cauldron.getWorld().getPlayers()) {
            if (p.getLocation().distanceSquared(cauldron.getLocation()) < 64) { // 8 blocks radius
                float pitch = 1.0f;

                if(randomPitch) {
                    pitch = 0.9f + (float)(Math.random() * 0.2); // random between 0.9 and 1.1
                }

                p.playSound(cauldron.getLocation(), sound, volume, pitch);
            }
        }
    }

    public BukkitTask loopSound(Block cauldron, Sound sound, float volume, boolean randomPitch, int duration, int interval) {
        Location loc = cauldron.getLocation();
        World world = cauldron.getWorld();
        long endTick = System.currentTimeMillis() + (duration * 1000L);
        int intervalTicks = interval * 20;

        return new BukkitRunnable() {
            @Override
            public void run() {
                if(System.currentTimeMillis() >= endTick) {
                    Bukkit.getLogger().info("4");
                    cancel();
                    return;
                }

                emitSound(cauldron, sound, volume, randomPitch);
            }
        }.runTaskTimer(plugin, 0L, intervalTicks);
    }

    /* -------------------- Processes -------------------- */

    /** Get the active process for this cauldron, if any. */
    public RecipeProcess getProcess(Location loc) {
        return cauldronToProcess.get(blockLoc(loc));
    }

    /** Start (or replace) the process for this cauldron. Ensures occupancy matches the process owner. */
    public boolean startProcess(RecipeProcess proc) {
        Location loc = blockLoc(proc.getCauldronLoc());
        UUID owner = getUser(loc);
        if (owner == null || !owner.equals(proc.getPlayerId())) {
            // refuse to start a process unless the cauldron is properly claimed by that player
            return false;
        }
        RecipeProcess old = cauldronToProcess.put(loc, proc);
        if (old != null) { Bukkit.getLogger().info("1"); old.cancel(); }
        proc.start();
        return true;
    }

    /** Stop and remove the process for this cauldron if present. */
    public boolean stopProcess(Location rawLoc) {
        Location loc = blockLoc(rawLoc);
        RecipeProcess proc = cauldronToProcess.get(loc);
        if (proc == null) return false;

        // Idempotent cancel: safe if already ended
        if (!proc.isEnded()) {
            proc.cancel();          // This should invoke onProcessEnded(loc) when it actually ends
        }
        return true;
    }

    /** Stop all processes for a player (e.g., on quit). */
    public void stopAllForPlayer(UUID playerId) {
        var set = playerToCauldrons.get(playerId);
        if (set == null) return;
        for (Location loc : new ArrayList<>(set)) stopProcess(loc); // your stop -> calls onProcessEnded
    }

    /** Stop all processes on a given cauldron (on break/unload). */
    public void stopAllForCauldron(Location rawLoc) {
        Location loc = blockLoc(rawLoc);
        // Cancel the process; onProcessEnded will clean maps.
        if (!stopProcess(loc)) {
            // No process? Then it’s safe to unclaim.
            stopUsing(loc);
        }
    }
}