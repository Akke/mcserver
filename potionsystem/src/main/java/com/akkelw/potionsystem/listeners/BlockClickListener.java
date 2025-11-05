package com.akkelw.potionsystem.listeners;

import com.akkelw.potionsystem.CauldronManager;
import org.bukkit.Location;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;

import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
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
                if(blockClicked.getType() == Material.CAULDRON) {
                    Player player = event.getPlayer();

                    /*RecipeProcess proc = RecipeProcess.getProcess((Player) h);
                    if (proc != null && proc.isLocked()) {
                        ((Player) h).sendMessage("§cBusy: " + proc.getRunningAction());
                        return;
                    }*/

                    UUID id = player.getUniqueId();
                    Location loc = blockClicked.getLocation();

                    if(cauldronManager.isInUse(loc) && !id.equals(cauldronManager.getUser(loc))) {
                        player.sendMessage(ChatColor.RED + "That cauldron’s already in use!");
                        event.setCancelled(true);
                        return;
                    }

                    RecipeProcess proc = cauldronManager.getProcess(loc);

                    if(proc == null) {
                        gui.open(player, blockClicked);
                        return;
                    }

                    if(proc.isLocked()) {
                        player.sendMessage(ChatColor.YELLOW + "That brewing process is locked.");
                        return;
                    }

                    // Otherwise, continue the active process step
                    ActionType lastBrewingAction = plugin.getLastBrewingAction(player);
                    if (lastBrewingAction == null) {
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
    }
}
