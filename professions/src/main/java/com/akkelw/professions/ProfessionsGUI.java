package com.akkelw.professions;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.*;

public class ProfessionsGUI implements Listener {

    private final Map<UUID, Inventory> open = new HashMap<>();
    private final ProfPlugin plugin;

    public ProfessionsGUI(ProfPlugin plugin) { this.plugin = plugin; }

    public void open(Player p) {
        Inventory inv = Bukkit.createInventory(p, 54, ChatColor.DARK_AQUA + "Professions");
        // Sections: top border + three rows of skills + bottom help.
        // We'll list skills in a RS-style grid, left-to-right by category.
        render(inv, p.getUniqueId());
        open.put(p.getUniqueId(), inv);
        p.openInventory(inv);
    }

    public void refresh(Player p) {
        Inventory inv = open.get(p.getUniqueId());
        if (inv == null) return;
        render(inv, p.getUniqueId());
        p.updateInventory();
    }

    private void render(Inventory inv, UUID id) {
        ProfData data = plugin.dataOf(id);

        // Clear
        for (int i=0;i<inv.getSize();i++) inv.setItem(i, null);

        // Gray background border
        ItemStack border = named(new ItemStack(Material.GRAY_STAINED_GLASS_PANE), " ");
        for (int i =0;i<54;i++) {
            int r=i/9,c=i%9;
            if (r==0||r==5||c==0||c==8) inv.setItem(i,border);
        }

        // Place skills: 3 rows x 6 cols in the center (rows 1..3 index)
        List<Profession> order = new ArrayList<>();
        // Combat
        for (Profession p : Profession.values()) if (p.category()== Profession.Category.COMBAT && !p.isAdvancedCombat()) order.add(p);
        for (Profession p : Profession.values()) if (p.category()== Profession.Category.COMBAT && p.isAdvancedCombat()) order.add(p);
        // Gathering
        for (Profession p : Profession.values()) if (p.category()== Profession.Category.GATHERING) order.add(p);
        // Crafting
        for (Profession p : Profession.values()) if (p.category()== Profession.Category.CRAFTING) order.add(p);

        int[] slots = {
            12,13,14,
            21,22,23,
            30,31,32,
            39,40,41
        };
        for (int i=0;i<Math.min(slots.length, order.size());i++) {
            Profession prof = order.get(i);
            inv.setItem(slots[i], skillItem(prof, data));
        }

        // Help / footer
        inv.setItem(49, helpItem(data.getChosenAdvanced()));
    }

    private ItemStack skillItem(Profession prof, ProfData pd) {
        SkillData sd = pd.get(prof);
        int lvl = sd.getLevel();
        long xp = sd.getXp();
        double pct = sd.progressPct();

        ItemStack it = new ItemStack(prof.icon());
        ItemMeta m = it.getItemMeta();
        String name = (prof.isAdvancedCombat() ? "§d" : "§b") + prof.display() + " §7(Lv.§e" + lvl + "§7)";
        if (prof.isAdvancedCombat() && pd.getChosenAdvanced()!=null && !pd.getChosenAdvanced().equals(prof)) {
            // Locked alt
            name = "§8" + prof.display() + " §7(Locked)";
        }
        m.setDisplayName(name);

        List<String> lore = new ArrayList<>();
        lore.add(ChatColor.GRAY + "XP: " + ChatColor.YELLOW + xp + ChatColor.GRAY + " / " +
                 ChatColor.YELLOW + (lvl>=99 ? XpTable.XP_99 : sd.xpForNext()));
        // RS-like 10-seg bar in lore
        int segs = (int)Math.round(Math.min(100.0, pct)/10.0);
        StringBuilder bar = new StringBuilder();
        for (int i=0;i<10;i++) bar.append(i<segs ? "§a■" : "§7■");
        lore.add(ChatColor.DARK_GRAY + "[" + bar + ChatColor.DARK_GRAY + "] " + ChatColor.WHITE + (int)Math.floor(pct) + "%");

        if (prof.isAdvancedCombat()) {
            if (pd.getChosenAdvanced()==null) {
                lore.add(" ");
                lore.add("§dClick to CHOOSE this advanced path.");
                lore.add("§7(One-time choice)");
            } else if (pd.getChosenAdvanced().equals(prof)) {
                lore.add(" ");
                lore.add("§dChosen path.");
            } else {
                lore.add(" ");
                lore.add("§8Alternative path (locked).");
            }
        }

        m.setLore(lore);
        
        m.addItemFlags(
            org.bukkit.inventory.ItemFlag.HIDE_ADDITIONAL_TOOLTIP, // hides the big help box
            org.bukkit.inventory.ItemFlag.HIDE_ARMOR_TRIM          // optional: also hides the trim preview line
        );
        it.setItemMeta(m);
        return it;
    }

    private ItemStack helpItem(Profession chosen) {
        ItemStack it = new ItemStack(Material.PAPER);
        ItemMeta m = it.getItemMeta();
        m.setDisplayName("§fHow it works");
        m.setLore(Arrays.asList(
            "§7- Gain XP by performing specific actions, attending classes, etc.",
            "§7- Level 0→99.",
            "§7- Advanced Combat: choose §dDark Arts§7 or §dTransfiguration§7 once.",
            chosen==null ? "§dYou haven’t chosen an advanced path." : "§dChosen advanced path: " + chosen.display()
        ));
        it.setItemMeta(m);
        return it;
    }

    private ItemStack named(ItemStack it, String name) {
        ItemMeta m = it.getItemMeta();
        m.setDisplayName(name);
        it.setItemMeta(m);
        return it;
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        HumanEntity h = e.getWhoClicked();
        Inventory inv = open.get(h.getUniqueId());
        if (inv == null || !inv.equals(e.getInventory())) return;
        e.setCancelled(true);

        ItemStack clicked = e.getCurrentItem();
        if (clicked == null || clicked.getType()==Material.AIR) return;

        // Handle choosing advanced profession
        String name = clicked.getItemMeta() != null ? clicked.getItemMeta().getDisplayName() : "";
        for (Profession p : Profession.values()) {
            if (name.contains(p.display())) {
                if (p.isAdvancedCombat()) {
                    ProfData pd = plugin.dataOf(h.getUniqueId());
                    if (pd.getChosenAdvanced()==null || pd.getChosenAdvanced().equals(p)) {
                        if (pd.chooseAdvanced(p)) {
                            h.sendMessage("§dChosen advanced path: §f" + p.display());
                            plugin.save(h.getUniqueId());
                            refresh((Player) h);
                        }
                    } else {
                        h.sendMessage("§8You already chose: §d" + pd.getChosenAdvanced().display());
                    }
                }
                break;
            }
        }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent e) {
        Inventory inv = open.get(e.getPlayer().getUniqueId());
        if (inv != null && inv.equals(e.getInventory())) open.remove(e.getPlayer().getUniqueId());
    }
}
