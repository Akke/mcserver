package com.akkelw.potionsystem.gui;

import java.util.*;
import java.util.stream.IntStream;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import com.akkelw.potionsystem.CauldronHolder;

/**
 * Opens a small menu with brewing controls:
 * - Stir Clockwise
 * - Stir Counterclockwise
 * - Boil
 * - Add Ingredient
 * - Wait
 * - Close
 *
 * Use the ActionHandler callback to react to clicks.
 */
public class BrewingOptionsGui implements Listener {
    public enum ActionType {
        STIR_CW, STIR_CCW, BOIL, SET_TEMPERATURE, ADD, WAIT, CLOSE;

        public static ActionType fromString(String s) {
            try {
                return ActionType.valueOf(s.toUpperCase());
            } catch (IllegalArgumentException | NullPointerException e) {
                return null;
            }
        }
    }

    /** Callback you implement to handle button clicks. */
    public interface ActionHandler {
        void onAction(Player player, ActionType action, Material type, Integer amount);
    }

    private final int SIZE = 27; // 3 rows
    private final String TITLE = ChatColor.DARK_AQUA + "Brewing Controls";

    private final Plugin plugin;
    private final ActionHandler handler;
    private final NamespacedKey actionKey;

    // Track which inventory belongs to which player
    private final Map<UUID, Inventory> open = new HashMap<>();

    // Track the last action of the player
    private final Map<UUID, ActionType> lastAction = new HashMap<>();

    public BrewingOptionsGui(Plugin plugin, ActionHandler handler) {
        this.plugin = plugin;
        this.handler = handler;
        this.actionKey = new NamespacedKey(plugin, "brew_action");
    }

    public ActionType getLastAction(Player player) {
        return lastAction.get(player.getUniqueId());
    }

    public ActionType removeLastAction(Player player) {
        return lastAction.remove(player.getUniqueId());
    }

    /** Open the GUI for a player. */
    public void open(Player player) {
        Inventory inv = Bukkit.createInventory(player, SIZE, TITLE);
        decorate(inv);
        placeButtons(inv);
        open.put(player.getUniqueId(), inv);
        player.openInventory(inv);
    }

    /** Optional: reopen (e.g., after handling something) */
    public void reopen(Player player) {
        Inventory inv = open.get(player.getUniqueId());
        if (inv == null) { open(player); return; }
        inv.clear();
        decorate(inv);
        placeButtons(inv);
        player.openInventory(inv);
    }

    // -------------------- Layout --------------------

    private void decorate(Inventory inv) {
        // Gray border panes
        ItemStack pane = named(Material.GRAY_STAINED_GLASS_PANE, ChatColor.RESET.toString() + " ", List.of());
        for (int i = 0; i < SIZE; i++) {
            int r = i / 9, c = i % 9;
            if (r == 0 || r == 2 || c == 0 || c == 8) inv.setItem(i, pane);
        }
    }

    private void placeButtons(Inventory inv) {
        // Center row slots: 11..15 are nice
        inv.setItem(11, button(Material.COMPASS, ChatColor.GREEN + "Stir ↻ (Clockwise)",
                List.of(ChatColor.WHITE + "Stir the mixture clockwise."), ActionType.STIR_CW));

        inv.setItem(12, button(Material.COMPASS, ChatColor.GREEN + "Stir ↺ (Counterclockwise)",
                List.of(ChatColor.WHITE + "Stir the mixture counterclockwise."), ActionType.STIR_CCW));

        inv.setItem(13, button(Material.LAVA_BUCKET, ChatColor.GOLD + "Boil",
                List.of(ChatColor.WHITE + "Bring the mixture to a boil."), ActionType.BOIL));
        
        inv.setItem(14, button(Material.CAMPFIRE, ChatColor.RED + "Set Temperature",
                List.of(ChatColor.WHITE + "Set the temperature that the cauldron should boil at."), ActionType.SET_TEMPERATURE));

        inv.setItem(15, button(Material.SLIME_BALL, ChatColor.AQUA + "Add Ingredient",
                List.of(ChatColor.WHITE + "Add the selected ingredient."), ActionType.ADD));

        inv.setItem(16, button(Material.CLOCK, ChatColor.YELLOW + "Wait",
                List.of(ChatColor.WHITE + "Let the mixture rest."), ActionType.WAIT));

        // Close in bottom center
        inv.setItem(22, button(Material.BARRIER, ChatColor.RED + "Close",
                List.of(ChatColor.WHITE + "Close this menu."), ActionType.CLOSE));
    }

    // -------------------- Events --------------------

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        HumanEntity h = e.getWhoClicked();
        Inventory top = e.getView().getTopInventory();
        Inventory ours = open.get(h.getUniqueId());

        if (ours == null || !ours.equals(top)) return;
        if (e.getClickedInventory() == null || !top.equals(e.getClickedInventory())) {
            e.setCancelled(true);
            return;
        }

        e.setCancelled(true);

        ItemStack clicked = e.getCurrentItem();
        if (clicked == null || clicked.getType() == Material.AIR) return;

        ActionType action = readAction(clicked);
        if (action == null) return;

        if (!(h instanceof Player)) return;
        Player p = (Player) h;

        if (action == ActionType.CLOSE) {
            p.closeInventory();
            return;
        }

        if (handler != null) {
            lastAction.put(p.getUniqueId(), action);

            ItemStack item = p.getInventory().getItemInMainHand();
            if(item != null) {
                Material type = item.getType();
                int amount = item.getAmount();

                handler.onAction(p, action, type, amount);
            } else {
                handler.onAction(p, action, null, null);
            }
        }
    }


    @EventHandler
    public void onClose(InventoryCloseEvent e) {
        UUID id = e.getPlayer().getUniqueId();
        Inventory inv = open.get(id);
        if (inv != null && inv.equals(e.getInventory())) {
            open.remove(id);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        open.remove(e.getPlayer().getUniqueId());
    }

    // -------------------- Helpers --------------------

    private ItemStack button(Material mat, String name, List<String> lore, ActionType action) {
        ItemStack it = named(mat, name, lore);
        tagAction(it, action);
        return it;
    }

    private ItemStack named(Material mat, String name, List<String> lore) {
        ItemStack it = new ItemStack(mat);
        ItemMeta meta = it.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            if (lore != null && !lore.isEmpty()) meta.setLore(lore);
            it.setItemMeta(meta);
        }
        return it;
    }

    private void tagAction(ItemStack it, ActionType action) {
        ItemMeta meta = it.getItemMeta();
        if (meta == null) return;
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        pdc.set(actionKey, PersistentDataType.STRING, action.name());
        it.setItemMeta(meta);
    }

    private ActionType readAction(ItemStack it) {
        ItemMeta meta = it.getItemMeta();
        if (meta == null) return null;
        String s = meta.getPersistentDataContainer().get(actionKey, PersistentDataType.STRING);
        if (s == null) return null;
        try {
            return ActionType.valueOf(s);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }
}
