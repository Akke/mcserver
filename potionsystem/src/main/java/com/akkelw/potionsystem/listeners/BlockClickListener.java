package com.akkelw.potionsystem.listeners;

import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;

import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

import com.akkelw.potionsystem.Plugin;
import com.akkelw.potionsystem.RecipeProcess;
import com.akkelw.potionsystem.gui.BrewingOptionsGui.ActionType;

import net.md_5.bungee.api.ChatColor;

import com.akkelw.potionsystem.gui.BrewingOptionsGui;
import com.akkelw.potionsystem.gui.RecipesGui;

public class BlockClickListener implements Listener {
    private final Plugin plugin;

    private final RecipesGui gui;

    public BlockClickListener(Plugin plugin, RecipesGui gui) {
        this.plugin = plugin;
        this.gui = gui;
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        if(event.getHand() != EquipmentSlot.HAND) return;

        Action action = event.getAction();

        if(action == Action.RIGHT_CLICK_BLOCK) {
            Block blockClicked = event.getClickedBlock();

            if(blockClicked != null) {
                if(blockClicked.getType() == Material.CAULDRON) {
                    Player player = event.getPlayer();

                    /*RecipeProcess proc = RecipeProcess.getProcess((Player) h);
                    if (proc != null && proc.isLocked()) {
                        ((Player) h).sendMessage("§cBusy: " + proc.getRunningAction());
                        return;
                    }*/

                    if(!RecipeProcess.hasActive(player)) {
                        // Recipe not in progress
                        gui.open(player, blockClicked);
                    } else {
                        RecipeProcess proc = RecipeProcess.get(player);
                        if(proc != null) {
                            if(proc.isLocked()) {
                                return;
                            }
                        }

                        ActionType lastBrewingAction = this.plugin.getLastBrewingAction(player);
                        if(lastBrewingAction == null) {
                            this.plugin.openBrewingOptions(player);
                        } else {
                            ItemStack item = player.getInventory().getItemInMainHand();

                            if(item != null) {
                                Material type = item.getType();
                                int amount = item.getAmount();
                                
                                if(proc != null) {
                                    if(!proc.isLocked()) {
                                        proc.processStep(lastBrewingAction, type, amount, blockClicked);
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
