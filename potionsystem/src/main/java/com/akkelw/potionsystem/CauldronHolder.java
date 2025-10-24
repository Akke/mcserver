package com.akkelw.potionsystem;

import org.bukkit.block.Block;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

public final class CauldronHolder implements InventoryHolder {
    private final Block cauldron;

    public CauldronHolder(Block cauldron) { this.cauldron = cauldron; }
    public Block getCauldron() { return cauldron; }

    @Override public Inventory getInventory() { return null; } // not used
}
