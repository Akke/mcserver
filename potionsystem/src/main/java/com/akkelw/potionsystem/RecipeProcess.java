package com.akkelw.potionsystem;

import org.bukkit.*;
import org.bukkit.block.data.BlockData;
import org.bukkit.event.Listener;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.bukkit.block.Block;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;

import com.akkelw.potionsystem.gui.BrewingOptionsGui;
import com.akkelw.potionsystem.gui.BrewingOptionsGui.ActionType;

import net.wesjd.anvilgui.AnvilGUI;

public class RecipeProcess implements Listener {
    private final Plugin plugin;
    private final Player player;
    private final Block cauldron;
    private final String elixirCode;
    private  final List<Map<?, ?>> actions;
    private final ConfigurationSection config;
    private int step = 0;
    private boolean locked = false;
    private boolean hasWater = false;
    private int stirCountCw = 0;                   
    private int stirCountCcw = 0;
    private final CauldronManager cauldronManager;
    private final UUID playerId;
    private final Location cauldronLoc;

    public RecipeProcess(Plugin plgn, Player p, String elxCode, Block cauldron) {
        this.plugin = plgn;
        this.cauldron = cauldron;
        this.elixirCode = elxCode;
        this.config = this.plugin.getConfig().getConfigurationSection("elixirs." + this.elixirCode);
        this.player = p;
        assert this.config != null;
        this.actions = this.config.getMapList("actions");
        this.cauldronManager = plgn.getCauldronManager();
        this.playerId = player.getUniqueId();
        this.cauldronLoc = CauldronManager.normalize(cauldron);
    }

    public UUID getPlayerId() { return playerId; }
    public Location getCauldronLoc() { return cauldronLoc; }

    public boolean isLocked() { return this.locked; }

    private void lock(BrewingOptionsGui.ActionType action) {
        this.locked = true;
    }

    private void unlock() {
        this.locked = false;
    }

    public void start() {
        if(this.config == null) return;

        Bukkit.getPluginManager().registerEvents(this, this.plugin);

        UUID id = this.player.getUniqueId();

        hasWater = false;

        int requiredRank = this.config.getInt("required_rank");
        int playerRank = this.plugin.getPotionLevel(id);

        if(playerRank < requiredRank) {
            this.player.sendMessage(String.format("Your Potion Making skill is too low, requires %2d, you are: %2d", requiredRank, playerRank));
            return;
        }

        this.plugin.removeLastBrewingAction(this.player);

        this.player.sendMessage("Started potion making process for recipe '" + this.elixirCode + "'");

        this.step = 0;
        this.stirCountCw = 0;
        this.stirCountCcw = 0;

        nextStep();
        this.player.closeInventory();
    }

    public void reward() {
        ConfigurationSection result = this.config.getConfigurationSection("result");
        if (result != null) {
            String type = result.getString("type", "POTION"); // e.g. "POTION" or "SPLASH_POTION"
            Material mat = Material.matchMaterial(type.toUpperCase());
            if (mat == null) {
                mat = Material.POTION; // fallback
            }

            ItemStack potion = new ItemStack(mat, 1);
            ItemMeta im = potion.getItemMeta();

            if(im instanceof PotionMeta meta) {
                String effect = result.getString("potion_effect", "SPEED");
                int duration = result.getInt("duration", 30);
                int amplifier = result.getInt("amplifier", 0);

                PotionEffectType effectType = Registry.EFFECT.get(NamespacedKey.minecraft(effect.toLowerCase()));

                if (effectType != null) {
                    meta.addCustomEffect(new PotionEffect(effectType, duration * 20, amplifier), true);
                }

                meta.setDisplayName("§b" + this.config.getString("name"));
                potion.setItemMeta(meta);
            }

            /* Double Drop Rate logic */
            int playerRank = this.plugin.getPotionLevel(this.player.getUniqueId());
            int itemAmount = 1;
            double baseChance = 0.005; // 0.5% per rank
            double chance = baseChance * playerRank;
            chance = Math.min(chance, 1.0); // cap at 100%

            if (Math.random() < chance) {
                itemAmount *= 2;
            }

            for(int i = 0; i < itemAmount; i++) {
                this.player.getInventory().addItem(potion);
            }
        }
    }

    public void finish() {
        reward();
        this.plugin.removeLastBrewingAction(this.player);
        this.player.closeInventory();
        unlock();
        this.player.sendMessage("You finished brewing " + this.elixirCode);
        this.stirCountCw = 0;
        this.stirCountCcw = 0;
        cauldronManager.setWaterLevel(cauldron,0);
        cauldronManager.stopUsing(this.player.getUniqueId());
        org.bukkit.event.HandlerList.unregisterAll(this);
    }

    public void fail() {
        this.plugin.removeLastBrewingAction(this.player);
        this.player.closeInventory();
        unlock();
        this.player.sendMessage("Oops! You did something wrong and the potion exploded! (" + this.elixirCode + ")");
        this.stirCountCw = 0;
        this.stirCountCcw = 0;
        cauldronManager.setWaterLevel(cauldron,0);
        cauldronManager.stopUsing(this.player.getUniqueId());
        org.bukkit.event.HandlerList.unregisterAll(this);
    }

    public void cancel() {
        this.plugin.removeLastBrewingAction(this.player);
        this.player.closeInventory();
        unlock();
        this.player.sendMessage("Brewing cancelled.");
        this.stirCountCw = 0;
        this.stirCountCcw = 0;
        cauldronManager.setWaterLevel(cauldron,0);
        cauldronManager.stopUsing(this.player.getUniqueId());
        org.bukkit.event.HandlerList.unregisterAll(this);
    }

    public void processStep(ActionType action, Material material, int materialAmount, Block blockClicked) {
        Map<?, ?> current = this.actions.get(this.step-1);

        Integer amount;
        Integer duration;

        String expected = (String) current.get("action");
        ActionType expectedAction = ActionType.fromString(expected);

        if(expectedAction != action) {
            fail();
            return;
        }

        if(!hasWater && (action == ActionType.ADD && material != Material.WATER_BUCKET)) {
            this.player.sendMessage(ChatColor.YELLOW + "There's no water in the cauldron, water is required!");
            fail();
            return;
        }

        switch(action) {
            case ADD: // have to lock since we are waiting for the player to add ingredients by clicking on the cauldron with them (so GUI wont open)
                lock(action);

                plugin.getLogger().info("Material is " + material);
                plugin.getLogger().info("materialAmount is " + materialAmount);

                if(addIngredient(material, materialAmount)) {
                    unlock();
                    nextStep();
                } else {
                    fail();
                }

                return;
            case SET_TEMPERATURE:
                Integer tempAmnt = (Integer) current.get("amount");
                amount = (tempAmnt != null) ? tempAmnt : -1;

                askNumberInput("Set Temperature", amount, () -> {
                    nextStep();
                });

                return;
            case BOIL: // have to lock to prevent multiple timers from being started
                Integer boilDur = (Integer) current.get("duration");
                duration = (boilDur != null) ? boilDur : -1;

                askNumberInput("Set Boil Duration", duration, () -> {
                    lock(action);

                    startBlockTimer(this.plugin, blockClicked, duration, () -> {
                        unlock();
                        nextStep();
                    });
                });

                return;
            case STIR_CW:
                Integer cwStirTimes = (Integer) current.get("amount");
                amount = (cwStirTimes != null) ? cwStirTimes : -1;

                lock(action);

                startBlockTimer(this.plugin, blockClicked, 2, () -> {
                    if(this.stirCountCcw > 0) {
                        fail();
                        return;
                    }

                    unlock();
                    this.plugin.removeLastBrewingAction(this.player);
                    this.stirCountCw++;
                    if(this.stirCountCw == amount) {
                        nextStep();
                    }
                });

                return;
            case STIR_CCW:
                Integer ccwStirTimes = (Integer) current.get("amount");
                amount = (ccwStirTimes != null) ? ccwStirTimes : -1;

                lock(action);

                startBlockTimer(this.plugin, blockClicked, 2, () -> {
                    if(this.stirCountCw > 0) {
                        fail();
                        return;
                    }

                    unlock();
                    this.plugin.removeLastBrewingAction(this.player);
                    this.stirCountCcw++;
                    if(this.stirCountCcw == amount) {
                        nextStep();
                    }
                });

                return;
            case WAIT:
                Integer waitDur = (Integer) current.get("duration");
                duration = (waitDur != null) ? waitDur : -1;

                askNumberInput("Set Wait Duration", duration, () -> {
                    lock(action);

                    startBlockTimer(this.plugin, blockClicked, duration, () -> {
                        unlock();
                        nextStep();
                    });
                });
                return;
            case CLOSE:
                // nothing
                return;
        }
    }

    public void nextStep() {
        this.step++;

        if((this.step-1) >= this.actions.size()) {
            finish();
            return;
        }

        this.stirCountCcw = 0;
        this.stirCountCw = 0;

        this.plugin.removeLastBrewingAction(this.player);
    }

    public void onBrewAction(ActionType action, Material type, Integer amount) {
        if(this.locked) {
            fail();
            return;
        }

        this.player.closeInventory();
        
        // we want to open the anvil UI when they click buttons that uses it
        if(action == BrewingOptionsGui.ActionType.BOIL || action == BrewingOptionsGui.ActionType.SET_TEMPERATURE || action == BrewingOptionsGui.ActionType.STIR_CCW || action == BrewingOptionsGui.ActionType.STIR_CW || action == BrewingOptionsGui.ActionType.WAIT) {
            ActionType lastBrewingAction = this.plugin.getLastBrewingAction(this.player);
            this.processStep(lastBrewingAction, type, amount, this.cauldron);
        }
    }

    public boolean addIngredient(Material ingredient, Integer amount) {
        Map<?, ?> current = this.actions.get(this.step-1);
        String requiredIngredient = (String) current.get("ingredient_name");
        int requiredAmount = (int) current.get("amount");
        Material requiredIngredientMaterial = Material.matchMaterial((String) requiredIngredient);

        if(requiredIngredientMaterial == ingredient) {
            if(ingredient == Material.WATER_BUCKET) {
                player.getInventory().setItemInMainHand(new ItemStack(Material.BUCKET));
                cauldronManager.setWaterLevel(cauldron,3);
                player.playSound(player.getLocation(), Sound.ITEM_BUCKET_EMPTY, 1f, 1f);
                hasWater = true;
            } else {
                ItemStack toRemove = new ItemStack(ingredient, amount); // we remove all quantity of what they're holding instead of just the required amount
                this.player.getInventory().removeItem(toRemove);
            }

            if(amount == requiredAmount) {
                Bukkit.getLogger().info("Added " + requiredAmount + " of ingredient " + requiredIngredient + ".");
                return true;
            } else {
                Bukkit.getLogger().info("Returning false 1");
                return false;
            }
        } else {
            Bukkit.getLogger().info(requiredIngredient);
            return false;
        }
    }

    public void askNumberInput(String title, int expectedAmount, Runnable  callback) {
        new AnvilGUI.Builder()
            .title(title)            // anvil window title
            .text("0")                          // pre-filled text
            .itemLeft(new ItemStack(Material.GRAY_STAINED_GLASS_PANE)) // optional left item
            .itemRight(new ItemStack(Material.GRAY_STAINED_GLASS_PANE)) // optional left item
            .itemOutput(new ItemStack(Material.GREEN_WOOL)) // optional left item
            .onClick((slot, state) -> {
                // Only handle the OUTPUT slot click (when player confirms)
                if (slot != AnvilGUI.Slot.OUTPUT) return Collections.emptyList();

                String input = state.getText().trim();

                int temp;
                try {
                    temp = Integer.parseInt(input);
                } catch (NumberFormatException ex) {
                    // keep GUI open and show feedback
                    return Arrays.asList(
                        AnvilGUI.ResponseAction.replaceInputText("Numbers only")
                    );
                }

                if(expectedAmount == -1) {
                    fail();
                } else {
                    if(expectedAmount == temp) {
                        if(callback == null) {
                            nextStep();
                        } else {
                            callback.run();
                        }
                    } else {
                        fail();
                    }
                }
                
                return Arrays.asList(AnvilGUI.ResponseAction.close());
            })
            .onClose(state -> {
                // optional: handle cancel/escape
                // player.sendMessage("Temperature input cancelled.");
                this.plugin.removeLastBrewingAction(player); // this allows us to show brewing options UI again after they cancel the anvil input
            })
            .plugin(this.plugin)                      // your JavaPlugin instance
            .open(this.player);
    }

    public BukkitTask startBlockTimer(Plugin plg, Block block, int seconds, Runnable onFinish) {
        Location loc = block.getLocation().add(0.5, 1.2, 0.5);
        ArmorStand stand = (ArmorStand) block.getWorld().spawnEntity(loc, EntityType.ARMOR_STAND);
        stand.setVisible(false);
        stand.setMarker(true);
        stand.setGravity(false);
        stand.setCustomNameVisible(true);

        BukkitRunnable runnable = new BukkitRunnable() {
            int t = seconds;
            @Override public void run() {
                if (t <= 0 || block.getType() != Material.CAULDRON) {
                    stand.remove();
                    cancel();
                    if (onFinish != null) onFinish.run();
                    return;
                }
                stand.setCustomName(ChatColor.YELLOW + "Time left: " + t + "s");
                t--;
            }
        };
        return runnable.runTaskTimer(plg, 0L, 20L);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        cauldronManager.stopAllForPlayer(e.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent e) {
        if (!(e.getInventory().getHolder() instanceof CauldronHolder holder)) return;

        Player player = (Player) e.getPlayer();
        UUID id = player.getUniqueId();
        Location loc = holder.getCauldronLoc();

        RecipeProcess proc = cauldronManager.getProcess(loc);

        if (proc == null) {
            // No brewing started -> make sure there isn't a stale lock (unlikely in your flow)
            if (id.equals(cauldronManager.getUser(loc))) {
                cauldronManager.stopUsing(id);
            }
            return;
        }
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent e) {
        if (!e.getBlock().getType().name().endsWith("_CAULDRON") &&
                e.getBlock().getType() != Material.CAULDRON) {
            return;
        }

        Location loc = e.getBlock().getLocation();
        cauldronManager.stopAllForCauldron(loc);
    }
}
