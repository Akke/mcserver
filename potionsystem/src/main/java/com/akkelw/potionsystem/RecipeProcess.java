package com.akkelw.potionsystem;

import java.net.http.WebSocket.Listener;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import com.akkelw.potionsystem.gui.BrewingOptionsGui;
import com.akkelw.potionsystem.gui.BrewingOptionsGui.ActionType;

import net.wesjd.anvilgui.AnvilGUI;

public class RecipeProcess implements Listener {
    // shared across all RecipeProcess instances
    private static final Map<UUID, RecipeProcess> active = new HashMap<>();
    private final Map<UUID, RecipeProcess> currentTask = new HashMap<>();

    private Plugin plugin;
    private Player player;
    private final Block cauldron;
    private String elixirCode;
    private List<Map<?, ?>> actions;
    private final ConfigurationSection config;
    private int step = 0;
    private boolean locked = false;           
    private int stirCountCw = 0;                   
    private int stirCountCcw = 0;                   

    public RecipeProcess(Plugin plgn, Player p, String elxCode, Block cauldron) {
        this.plugin = plgn;
        this.cauldron = cauldron;
        this.elixirCode = elxCode;
        this.config = this.plugin.getConfig().getConfigurationSection("elixirs." + this.elixirCode);
        this.player = p;
        this.actions = this.config.getMapList("actions");
        this.stirCountCw = stirCountCw;
        this.stirCountCcw = stirCountCcw;
    }

    public static RecipeProcess get(Player p) {
        return active.get(p.getUniqueId());
    }

    public boolean isLocked() { return this.locked; }

    private void lock(BrewingOptionsGui.ActionType action) {
        this.currentTask.put(this.player.getUniqueId(), this);
        this.locked = true;
    }

    private void unlock() {
        this.locked = false;
        this.currentTask.remove(this.player.getUniqueId());
    }

    public void start() {
        if(this.config == null) return;

        UUID id = this.player.getUniqueId();

        if(active.containsKey(id)) {
            this.player.sendMessage("You're already brewing something!");
            return;
        }

        int requiredRank = this.config.getInt("required_rank");
        int playerRank = this.plugin.getPotionLevel(id);

        if(playerRank < requiredRank) {
            this.player.sendMessage(String.format("Your Potion Making skill is too low, requires %2d, you are: %2d", requiredRank, playerRank));
            return;
        }

        this.plugin.removeLastBrewingAction(this.player); // remove last action just in case

        active.put(id, this);
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

            if(im instanceof PotionMeta) {
                PotionMeta meta = (PotionMeta) im;
                String effect = result.getString("potion_effect", "SPEED");
                int duration = result.getInt("duration", 30);
                int amplifier = result.getInt("amplifier", 0);

                PotionEffectType effectType = PotionEffectType.getByName(effect.toUpperCase());
                if (effectType != null) {
                    meta.addCustomEffect(new PotionEffect(effectType, duration * 20, amplifier), true);
                }

                meta.setDisplayName("§b" + this.config.getString("name"));
                potion.setItemMeta(meta);
            }

            this.player.getInventory().addItem(potion);
        }
    }

    public void finish() {
        reward();
        active.remove(this.player.getUniqueId());
        this.plugin.removeLastBrewingAction(this.player);
        this.player.closeInventory();
        unlock();
        this.player.sendMessage("You finished brewing " + this.elixirCode);
        this.stirCountCw = 0;
        this.stirCountCcw = 0;
    }

    public void fail(String s) {
        active.remove(this.player.getUniqueId());
        this.plugin.removeLastBrewingAction(this.player);
        this.player.closeInventory();
        unlock();
        this.player.sendMessage(s);
        this.player.sendMessage("Oops! You did something wrong and the potion exploded! (" + this.elixirCode + ")");
        this.stirCountCw = 0;
        this.stirCountCcw = 0;
    }

    public void cancel() {
        active.remove(this.player.getUniqueId());
        this.plugin.removeLastBrewingAction(this.player);
        this.player.closeInventory();
        unlock();
        this.player.sendMessage("Brewing cancelled.");
        this.stirCountCw = 0;
        this.stirCountCcw = 0;
    }

    public static boolean hasActive(Player p) {
        return active.containsKey(p.getUniqueId());
    }

    public void processStep(ActionType action, Material material, int materialAmount, Block blockClicked) {
        Map<?, ?> current = this.actions.get(this.step-1);

        Integer amount;
        Integer duration;

        String expected = (String) current.get("action");
        ActionType expectedAction = ActionType.fromString(expected);

        switch(action) {
            case ADD: // have to lock since we are waiting for the player to add ingredients by clicking on the cauldron with them (so GUI wont open)
                lock(action);

                if(addIngredient(material, materialAmount)) {
                    unlock();
                    nextStep();
                } else {
                    fail("1");
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
                    if(this.stirCountCcw > 0 || expectedAction == BrewingOptionsGui.ActionType.STIR_CCW) {
                        fail("8");
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
                    if(this.stirCountCw > 0 || expectedAction == BrewingOptionsGui.ActionType.STIR_CW) {
                        fail("9");
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
            fail("2");
            return;
        }

        /*Map<?, ?> current = this.actions.get(this.step-1);
        String supposedAction = (String) current.get("action");
        ActionType expectedAction = ActionType.fromString(supposedAction);

        if(expectedAction == null) {
            fail("7");
            return;
        }

        // we should only fail instantly upon clicking on these following brewing options
        // (other options such as anvil inputs fail when you enter wrong data)
        if(action == BrewingOptionsGui.ActionType.ADD) {
            if (expectedAction != action) {
                fail("3"); 
                return;
            }
        }*/

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
            ItemStack toRemove = new ItemStack(ingredient, amount); // we remove all quantity of what they're holding instead of just the required amount
            this.player.getInventory().removeItem(toRemove);

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
                    fail("6");
                } else {
                    if(expectedAmount == temp) {
                        if(callback == null) {
                            nextStep();
                        } else {
                            callback.run();
                        }
                    } else {
                        fail("4");
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
        RecipeProcess p = active.remove(e.getPlayer().getUniqueId());
        if(p != null) p.cancel();
    }
}
