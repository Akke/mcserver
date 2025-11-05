package com.akkelw.potionsystem;

import com.akkelw.potionsystem.gui.BrewingOptionsGui;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class CauldronHolder implements InventoryHolder {
    private final Block cauldron;
    private final Location cauldronLoc;

    public CauldronHolder(Block cauldron) {
        this.cauldron = cauldron;
        this.cauldronLoc = CauldronManager.normalize(cauldron);
    }

    public Block getCauldron() { return cauldron; }

    public Location getCauldronLoc() { return cauldronLoc; }

    @Override public Inventory getInventory() { return null; } // not used
}
