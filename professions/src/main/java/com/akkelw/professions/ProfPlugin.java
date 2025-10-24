package com.akkelw.professions;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;

import com.akkelw.professions.api.ProfessionsAPI;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class ProfPlugin extends JavaPlugin implements Listener {

    private final Map<UUID, ProfData> profiles = new HashMap<>();
    private Storage storage;
    private ProfessionsGUI gui;

    @Override
    public void onEnable() {
        storage = new Storage(getDataFolder());
        gui = new ProfessionsGUI(this);
        getServer().getPluginManager().registerEvents(this, this);
        getServer().getPluginManager().registerEvents(gui, this);
        getLogger().info("Professions loaded.");

        getServer().getServicesManager().register(
            ProfessionsAPI.class,
            new ProfessionsApiImpl(this), 
            this,
            ServicePriority.Normal
        );
    }

    @Override
    public void onDisable() {
        // Save all
        for (UUID id : profiles.keySet()) save(id);
        profiles.clear();
    }

    // Accessors used by GUI
    public ProfData dataOf(UUID id) {
        return profiles.computeIfAbsent(id, storage::load);
    }
    public void save(UUID id) { storage.save(id, dataOf(id)); }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) { dataOf(e.getPlayer().getUniqueId()); }
    @EventHandler
    public void onQuit(PlayerQuitEvent e) { save(e.getPlayer().getUniqueId()); }

    // Commands: /prof, /prof addxp <prof> <amount>, /prof choose <Dark|Transfiguration>
    @Override
    public boolean onCommand(CommandSender s, Command cmd, String label, String[] args) {
        if (!(s instanceof Player)) {
            s.sendMessage("Players only.");
            return true;
        }
        Player p = (Player) s;
        if (!cmd.getName().equalsIgnoreCase("prof")) return false;

        if (args.length == 0) {
            gui.open(p);
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "addxp": {
                if (args.length < 3) { p.sendMessage("§7Usage: /prof addxp <profession> <amount>"); return true; }
                Profession prof = Profession.byName(args[1]);
                if (prof == null) { p.sendMessage("§cUnknown profession."); return true; }
                long amt;
                try { amt = Long.parseLong(args[2]); } catch (Exception ex) { p.sendMessage("§cAmount must be a number."); return true; }
                ProfData pd = dataOf(p.getUniqueId());
                SkillData sd = pd.get(prof);
                long before = sd.getXp();
                sd.setXp(Math.min(XpTable.XP_99, before + Math.max(0, amt)));
                save(p.getUniqueId());
                p.sendMessage("§a+" + amt + " XP §7to §f" + prof.display() + "§7. Lv: §e" + sd.getLevel());
                gui.refresh(p);
                return true;
            }
            case "choose": {
                if (args.length < 2) { p.sendMessage("§7Usage: /prof choose <Dark|Transfiguration>"); return true; }
                String pick = args[1].toLowerCase();
                Profession choice = pick.startsWith("dark") ? Profession.DARK_ARTS :
                                    pick.startsWith("trans") ? Profession.TRANSFIGURATION : null;
                if (choice == null) { p.sendMessage("§cChoose 'Dark' or 'Transfiguration'."); return true; }
                ProfData pd = dataOf(p.getUniqueId());
                if (pd.getChosenAdvanced()!=null && !pd.getChosenAdvanced().equals(choice)) {
                    p.sendMessage("§8You already chose: §d" + pd.getChosenAdvanced().display());
                    return true;
                }
                if (pd.chooseAdvanced(choice)) {
                    save(p.getUniqueId());
                    p.sendMessage("§dAdvanced path chosen: §f" + choice.display());
                    gui.refresh(p);
                } else {
                    p.sendMessage("§cCould not choose that path.");
                }
                return true;
            }
            default:
                p.sendMessage("§7/prof — open UI");
                p.sendMessage("§7/prof addxp <profession> <amount>");
                p.sendMessage("§7/prof choose <Dark|Transfiguration>");
                return true;
        }
    }
}
