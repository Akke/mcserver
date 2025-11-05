package com.akkelw.potionsystem.gui;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.akkelw.potionsystem.CauldronManager;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.configuration.ConfigurationSection;
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
import org.bukkit.persistence.PersistentDataType;

import com.akkelw.potionsystem.CauldronHolder;
import com.akkelw.potionsystem.Plugin;
import com.akkelw.potionsystem.RecipeProcess;

public class RecipesGui implements Listener {
    private final Map<UUID, Inventory> open = new HashMap<>();
    private final Plugin plugin;

    private static final int[] CONTENT_SLOTS = {
        10,11,12,13,14,15,16,
        19,20,21,22,23,24,25,
        28,29,30,31,32,33,34,
        37,38,39,40,41,42,43
    };

    private static final int ITEMS_PER_PAGE = CONTENT_SLOTS.length; // 28

    private final Map<UUID, List<ItemStack>> itemsByPlayer = new HashMap<>();
    private final Map<UUID, Integer> pageByPlayer = new HashMap<>();

    private final CauldronManager cauldronManager;

    public RecipesGui(Plugin plugin) {
        this.plugin = plugin;
        this.cauldronManager = plugin.getCauldronManager();
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        Inventory top = e.getView().getTopInventory();
        if (!(top.getHolder() instanceof CauldronHolder)) return; // not our GUI

        e.setCancelled(true);

        HumanEntity h = e.getWhoClicked();

        if (e.getClickedInventory() == null || e.getClickedInventory() != top) return;

        Block cauldron = ((CauldronHolder) top.getHolder()).getCauldron();

        int slot = e.getRawSlot(); // 0..53

        if (slot == 49) { h.closeInventory(); return; } // close
        if (slot == 48) {                    // prev
            int page = pageByPlayer.getOrDefault(h.getUniqueId(), 0);
            if (page > 0) {
                pageByPlayer.put(h.getUniqueId(), page - 1);
                renderPage((Player) h);
            }
            return;
        }
        if (slot == 50) {                    // next
            int page = pageByPlayer.getOrDefault(h.getUniqueId(), 0);
            List<ItemStack> items = itemsByPlayer.getOrDefault(h.getUniqueId(), List.of());
            int totalPages = Math.max(1, (int) Math.ceil(items.size() / (double) ITEMS_PER_PAGE));
            if (page < totalPages - 1) {
                pageByPlayer.put(h.getUniqueId(), page + 1);
                renderPage((Player) h);
            }
            return;
        }

        // content click → map slot back to global index
        int pos = -1;
        for (int i = 0; i < CONTENT_SLOTS.length; i++) if (CONTENT_SLOTS[i] == slot) { pos = i; break; }
        if (pos == -1) return;

        int page = pageByPlayer.getOrDefault(h.getUniqueId(), 0);
        int globalIndex = page * ITEMS_PER_PAGE + pos;
        List<ItemStack> items = itemsByPlayer.getOrDefault(h.getUniqueId(), List.of());
        if (globalIndex >= 0 && globalIndex < items.size()) {
            ItemStack clicked = items.get(globalIndex);
            NamespacedKey key = new NamespacedKey(plugin, "elixir_code");

            ItemMeta meta = clicked.getItemMeta();
            if (meta != null) {
                String elixirCode = meta.getPersistentDataContainer().get(key, PersistentDataType.STRING);
                if (elixirCode != null) {
                    Player player = (Player) h;
                    RecipeProcess recipeProcess = new RecipeProcess(plugin, player, elixirCode, cauldron);
                    recipeProcess.start();
                }
            }
        }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent e) {
        UUID id = e.getPlayer().getUniqueId();
        Inventory inv = open.get(id);
        if (inv != null && inv.equals(e.getInventory())) {
            open.remove(id);
            itemsByPlayer.remove(id);
            pageByPlayer.remove(id);
        }
    }

    public void open(Player player, Block cauldron) {
        Inventory inv = Bukkit.createInventory(new CauldronHolder(cauldron), 54, ChatColor.DARK_AQUA + "Potion Recipes");
        open.put(player.getUniqueId(), inv);
        player.openInventory(inv);

        // build items once
        ConfigurationSection elixirs = plugin.getConfig().getConfigurationSection("elixirs");
        List<ItemStack> order = new ArrayList<>();
        if (elixirs != null) {
            for (String code : elixirs.getKeys(false)) {
                order.add(recipeItem(player, code));
            }
        }
        itemsByPlayer.put(player.getUniqueId(), order);
        pageByPlayer.put(player.getUniqueId(), 0);

        renderPage(player); // draw page 0
    }

    private void renderPage(Player player) {
        UUID id = player.getUniqueId();
        Inventory inv = open.get(id);
        if (inv == null) return;

        List<ItemStack> items = itemsByPlayer.getOrDefault(id, List.of());
        int page = Math.max(0, pageByPlayer.getOrDefault(id, 0));
        int total = items.size();
        int totalPages = Math.max(1, (int) Math.ceil(total / (double) ITEMS_PER_PAGE));
        if (page >= totalPages) page = totalPages - 1;
        pageByPlayer.put(id, page);

        // clear everything first
        inv.clear();

        ItemStack emptyItem = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta meta = emptyItem.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ChatColor.RESET.toString() + " ");
            emptyItem.setItemMeta(meta);
        }

        for (int i = 0; i < 54; i++) {
            int r = i / 9, c = i % 9;
            if (r == 0 || r == 5 || c == 0 || c == 8) {
                inv.setItem(i, emptyItem);
            }
        }

        // === fill this page's content items ===
        int start = page * ITEMS_PER_PAGE;
        for (int i = 0; i < ITEMS_PER_PAGE; i++) {
            int idx = start + i;
            if (idx >= total) break;
            inv.setItem(CONTENT_SLOTS[i], items.get(idx).clone());
        }

        // === nav buttons ===
        inv.setItem(49, helpItem(Material.BARRIER, "Close", List.of("§fClose this window.")));
        inv.setItem(48, (page > 0)
            ? helpItem(Material.SPRUCE_SIGN, "Previous Page", List.of("§fGo to the previous page."))
            : emptyItem);
        inv.setItem(50, (page < totalPages - 1)
            ? helpItem(Material.SPRUCE_SIGN, "Next Page", List.of("§fGo to the next page."))
            : emptyItem);
    }

    private ItemStack recipeItem(Player player, String elixirName) {
        UUID id = player.getUniqueId();
        int playerRank = plugin.getPotionLevel(id);

        ConfigurationSection elixirCfg = plugin.getConfig().getConfigurationSection("elixirs." + elixirName);
        int requiredRank = elixirCfg.getInt("required_rank");
        String name = elixirCfg.getString("name");

        ChatColor loreInfoColor = ChatColor.GREEN;
        if(playerRank < requiredRank) {
            loreInfoColor = ChatColor.RED;
        }

        String loreInfo = loreInfoColor + "Requires Potion Making " + requiredRank + " (Your skill: " + playerRank + ")";

        //Material potionMaterial = Material.matchMaterial((String) name);
        ItemStack it = new ItemStack(Material.POTION);
        ItemMeta m = it.getItemMeta();
        m.setDisplayName(ChatColor.WHITE + name);
        m.setLore(Arrays.asList(
            loreInfo
        ));

        NamespacedKey key = new NamespacedKey(plugin, "elixir_code");

        m.getPersistentDataContainer().set(key, PersistentDataType.STRING, elixirName);

        it.setItemMeta(m);

        return it;
    }

    private ItemStack helpItem(Material materialName, String displayName, List<String> loreList) {
        ItemStack it = new ItemStack(materialName);
        ItemMeta m = it.getItemMeta();
        m.setDisplayName(ChatColor.WHITE+displayName);
        m.setLore(loreList);
        it.setItemMeta(m);
        return it;
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        open.remove(e.getPlayer().getUniqueId());
    }
}