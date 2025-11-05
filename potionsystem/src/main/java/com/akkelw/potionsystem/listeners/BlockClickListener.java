package com.akkelw.potionsystem.listeners;

import com.akkelw.potionsystem.CauldronManager;
import org.bukkit.Location;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;

import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.block.CauldronLevelChangeEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

import com.akkelw.potionsystem.Plugin;
import com.akkelw.potionsystem.RecipeProcess;
import com.akkelw.potionsystem.gui.BrewingOptionsGui.ActionType;

import net.md_5.bungee.api.ChatColor;

import com.akkelw.potionsystem.gui.BrewingOptionsGui;
import com.akkelw.potionsystem.gui.RecipesGui;

import java.util.UUID;

public class BlockClickListener implements Listener {
    private final Plugin plugin;

    private final RecipesGui gui;

    private final CauldronManager cauldronManager;

    public BlockClickListener(Plugin plugin, RecipesGui gui) {
        this.plugin = plugin;
        this.gui = gui;
        this.cauldronManager = plugin.getCauldronManager();
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        if(event.getHand() != EquipmentSlot.HAND) return;

        Action action = event.getAction();

        if(action == Action.RIGHT_CLICK_BLOCK) {
            Block blockClicked = event.getClickedBlock();

            if(blockClicked != null) {
                if(!blockClicked.getType().name().endsWith("_CAULDRON") && !blockClicked.getType().name().equals("CAULDRON")) return;

                Player player = event.getPlayer();

                UUID id = player.getUniqueId();
                Location loc = blockClicked.getLocation();

                if(cauldronManager.isInUse(loc) && !id.equals(cauldronManager.getUser(loc))) {
                    player.sendMessage(ChatColor.RED + "That cauldron’s already in use!");
                    event.setCancelled(true);
                    return;
                }

                ItemStack inHand = (event.getHand() == EquipmentSlot.HAND)
                        ? player.getInventory().getItemInMainHand()
                        : player.getInventory().getItemInOffHand();

                RecipeProcess proc = cauldronManager.getProcess(loc);
                if(proc == null) {
                    if(inHand.getType() != Material.AIR) {
                        event.setCancelled(true);
                        player.sendMessage(ChatColor.GRAY + "Empty your hand to interact with the cauldron.");
                        return;
                    }

                    gui.open(player, blockClicked);
                    return;
                }

                if(proc.isLocked()) {
                    player.sendMessage(ChatColor.YELLOW + "That brewing process is locked.");
                    return;
                }

                // Otherwise, continue the active process step
                ActionType lastBrewingAction = plugin.getLastBrewingAction(player);
                if(lastBrewingAction != null && lastBrewingAction != ActionType.ADD) {
                    if(inHand.getType() != Material.AIR) {
                        event.setCancelled(true);
                        player.sendMessage(ChatColor.GRAY + "Empty your hand to interact with the cauldron.");
                        return;
                    }
                }

                if(lastBrewingAction == null) {
                    if(inHand.getType() != Material.AIR) {
                        event.setCancelled(true);
                        player.sendMessage(ChatColor.GRAY + "Empty your hand to interact with the cauldron.");
                        return;
                    }

                    plugin.openBrewingOptions(player, blockClicked);
                    return;
                }

                ItemStack item = player.getInventory().getItemInMainHand();
                if (item == null) return;

                Material type = item.getType();
                int amount = item.getAmount();

                // Now safely process the step
                proc.processStep(lastBrewingAction, type, amount, blockClicked);
            }
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGHEST)
    public void onUseCauldron(PlayerInteractEvent e) {
        if (e.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (e.getHand() != EquipmentSlot.HAND) return;
        Block b = e.getClickedBlock(); if (b == null) return;

        Material t = b.getType();
        boolean isAnyCauldron = (t == Material.CAULDRON) || t.name().endsWith("_CAULDRON");
        if (!isAnyCauldron) return;

        ItemStack used = e.getItem();
        if (used != null && (used.getType() == Material.BUCKET || used.getType() == Material.GLASS_BOTTLE)) {
            e.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGHEST)
    public void onCauldronChange(CauldronLevelChangeEvent e) {
        Material type = e.getBlock().getType();
        // Only target real cauldrons
        if (!(type == Material.CAULDRON || type.name().endsWith("_CAULDRON"))) return;

        // Cancel ALL automatic fluid level changes unless explicitly allowed
        switch (e.getReason()) {
            // Bucket and bottle interactions
            case BOTTLE_FILL:
            case BOTTLE_EMPTY:
            case BUCKET_FILL:
            case BUCKET_EMPTY:

                // Snow and powder snow interactions
            case EXTINGUISH:
            case EVAPORATE:

                // Misc fill sources
            case UNKNOWN:
                e.setCancelled(true);
                break;

            default:
                break;
        }
    }

}
