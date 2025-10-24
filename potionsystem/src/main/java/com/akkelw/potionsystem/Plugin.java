package com.akkelw.potionsystem;

import java.util.UUID;
import java.util.logging.Logger;

import org.bukkit.entity.Player;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

import com.akkelw.potionsystem.gui.BrewingOptionsGui;
import com.akkelw.potionsystem.gui.BrewingOptionsGui.ActionType;
import com.akkelw.potionsystem.gui.RecipesGui;
import com.akkelw.potionsystem.listeners.BlockClickListener;
import com.akkelw.professions.api.ProfessionsAPI;

/*
 * potionsystem java plugin
 * note to self: remember to track what player started the recipe process so people who didnt start it cant steal cauldron
 */
public class Plugin extends JavaPlugin
{
    private static final Logger LOGGER=Logger.getLogger("potionsystem");
    private ProfessionsAPI professionsAPI;
    public BrewingOptionsGui brewingOptionsGui;

    @Override
    public void onEnable()
    {
        saveDefaultConfig();

        LOGGER.info("potionsystem enabled");

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
        
        brewingOptionsGui = new BrewingOptionsGui(this, (player, action, type, amount) -> {
            RecipeProcess proc = RecipeProcess.get(player);
            if (proc != null) {
                proc.onBrewAction(action, type, amount);
            }
        });

        getServer().getPluginManager().registerEvents(brewingOptionsGui, this);
    }

    public void openBrewingOptions(Player player) {
        brewingOptionsGui.open(player);
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
}
