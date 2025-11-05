package com.akkelw.potionsystem;

import org.bukkit.Location;
import org.bukkit.block.Block;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class CauldronManager {
    private final Map<Location, UUID> cauldronToPlayer = new HashMap<>();
    private final Map<UUID, Location> playerToCauldron = new HashMap<>();
    private final Map<Location, RecipeProcess> cauldronToProcess = new HashMap<>();

    private static Location blockLoc(Location loc) {
        return normalize(loc.getBlock());
    }

    public boolean isInUse(Location loc) {
        return cauldronToPlayer.containsKey(blockLoc(loc));
    }

    public UUID getUser(Location loc) {
        return cauldronToPlayer.get(blockLoc(loc));
    }

    public Location getCauldron(UUID playerId) {
        return playerToCauldron.get(playerId);
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

    public boolean startUsing(UUID playerId, Location loc) {
        loc = blockLoc(loc);
        UUID current = cauldronToPlayer.get(loc);
        if (current != null && !current.equals(playerId)) return false; // someone else

        // If player already had another cauldron claimed and you allow only one, free the old:
        Location old = playerToCauldron.get(playerId);
        if (old != null && !old.equals(loc)) {
            cauldronToPlayer.remove(old);
            // also stop any process on the old cauldron
            stopProcess(old);
        }

        cauldronToPlayer.put(loc, playerId);
        playerToCauldron.put(playerId, loc);
        return true;
    }

    public boolean stopUsing(UUID playerId) {
        Location loc = playerToCauldron.remove(playerId);
        if (loc == null) return false;
        cauldronToPlayer.remove(loc);
        stopProcess(loc);
        return true;
    }

    public boolean stopUsing(Location loc) {
        loc = blockLoc(loc);
        UUID user = cauldronToPlayer.remove(loc);
        if (user != null) playerToCauldron.remove(user);
        stopProcess(loc);
        return user != null;
    }

    public void clearForWorld(String worldName) {
        cauldronToPlayer.keySet().removeIf(l -> {
            if (!l.getWorld().getName().equals(worldName)) return false;
            UUID u = cauldronToPlayer.get(l);
            if (u != null) playerToCauldron.remove(u);
            return true;
        });
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
        if (old != null) old.cancel();
        proc.start();
        return true;
    }

    /** Stop and remove the process for this cauldron if present. */
    public boolean stopProcess(Location loc) {
        loc = blockLoc(loc);
        RecipeProcess p = cauldronToProcess.remove(loc);
        if (p != null) { p.cancel(); return true; }
        return false;
    }

    /** Stop all processes for a player (e.g., on quit). */
    public void stopAllForPlayer(UUID playerId) {
        // If you allow one cauldron per player, this is just stopUsing(playerId).
        // If you later allow multiple, iterate:
        cauldronToProcess.entrySet().removeIf(e -> {
            RecipeProcess p = e.getValue();
            if (p.getPlayerId().equals(playerId)) { p.cancel(); return true; }
            return false;
        });
        stopUsing(playerId);
    }

    /** Stop all processes on a given cauldron (on break/unload). */
    public void stopAllForCauldron(Location loc) {
        stopProcess(loc);
        stopUsing(loc);
    }
}