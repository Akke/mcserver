package com.akkelw.potionsystem;

import java.util.UUID;
import java.util.logging.Logger;

import com.akkelw.potionsystem.commands.CauldronDebugCommand;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.material.Cauldron;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

import com.akkelw.potionsystem.gui.BrewingOptionsGui;
import com.akkelw.potionsystem.gui.BrewingOptionsGui.ActionType;
import com.akkelw.potionsystem.gui.RecipesGui;
import com.akkelw.potionsystem.listeners.BlockClickListener;
import com.akkelw.professions.api.ProfessionsAPI;

/*
 * potionsystem java plugin
 * note to self: remember to track what player started the recipe process so people who didn't start it cant steal cauldron
 */
public class Plugin extends JavaPlugin
{
    private static final Logger LOGGER=Logger.getLogger("potion system");
    private CauldronManager cauldronManager;
    private ProfessionsAPI professionsAPI;
    public BrewingOptionsGui brewingOptionsGui;

    @Override
    public void onEnable()
    {
        this.cauldronManager = new CauldronManager(this);

        saveDefaultConfig();

        LOGGER.info("potion system enabled");

        RegisteredServiceProvider<ProfessionsAPI> reg = getServer().getServicesManager().getRegistration(ProfessionsAPI.class);
        if (reg != null) {
            professionsAPI = reg.getProvider();
            getLogger().info("Hooked into Professions API.");
        } else {
            getLogger().warning("Professions API not found, defaulting to level 0.");
        }

        RecipesGui gui = new RecipesGui(this);

        getServer().getPluginManager().registerEvents(gui, this);
        getServer().getPluginManager().registerEvents(new BlockClickListener(this, gui), this);

        getCommand("debugcauldronoccupant").setExecutor(new CauldronDebugCommand(cauldronManager));
        
        brewingOptionsGui = new BrewingOptionsGui(this, (player, loc, action, type, amount) -> {
            RecipeProcess proc = cauldronManager.getProcess(loc);
            if (proc != null) {
                proc.onBrewAction(action, type, amount);
            }
        });

        getServer().getPluginManager().registerEvents(brewingOptionsGui, this);
    }

    public void openBrewingOptions(Player player, Block cauldronBlock) {
        brewingOptionsGui.open(player, cauldronBlock);
    }

    public ActionType getLastBrewingAction(Player player) {
        ActionType action = brewingOptionsGui.getLastAction(player);
        return action;
    }

    public void removeLastBrewingAction(Player player) {
        brewingOptionsGui.removeLastAction(player);
    }

    public ProfessionsAPI getProfessionsAPI() {
        return professionsAPI;
    }

    public int getPotionLevel(UUID id) {
        return (professionsAPI != null) ? professionsAPI.getPotionMakingLevel(id) : 0;
    }

    public CauldronManager getCauldronManager() {
        return cauldronManager;
    }
}
