package com.akkelw.potionsystem;

import org.bukkit.Location;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class CauldronManager {
    private final Map<Location, UUID> cauldronToPlayer = new HashMap<>();
    private final Map<UUID, Location> playerToCauldron = new HashMap<>();

    private static Location blockLoc(Location loc) {
        return loc.getBlock().getLocation();
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

    public boolean startUsing(UUID playerId, Location loc) {
        loc = blockLoc(loc);
        UUID current = cauldronToPlayer.get(loc);

        if (current != null && !current.equals(playerId)) {
            return false;
        }

        Location existing = playerToCauldron.get(playerId);
        if (existing != null && !existing.equals(loc)) {
            cauldronToPlayer.remove(existing);
        }
        cauldronToPlayer.put(loc, playerId);
        playerToCauldron.put(playerId, loc);
        return true;
    }

    public void stopUsing(UUID playerId) {
        Location loc = playerToCauldron.remove(playerId);
        if (loc != null) cauldronToPlayer.remove(loc);
    }

    public void stopUsing(Location loc) {
        loc = blockLoc(loc);
        UUID user = cauldronToPlayer.remove(loc);
        if (user != null) playerToCauldron.remove(user);
    }

    public void clearForWorld(String worldName) {
        cauldronToPlayer.keySet().removeIf(l -> {
            if (!l.getWorld().getName().equals(worldName)) return false;
            UUID u = cauldronToPlayer.get(l);
            if (u != null) playerToCauldron.remove(u);
            return true;
        });
    }
}