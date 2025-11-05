package com.akkelw.potionsystem.commands;

import com.akkelw.potionsystem.CauldronManager;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.Location;

import java.util.UUID;

public class CauldronDebugCommand implements CommandExecutor {
    private final CauldronManager cauldronManager;

    public CauldronDebugCommand(CauldronManager cauldronManager) {
        this.cauldronManager = cauldronManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatColor.RED + "Only players can use this command.");
            return true;
        }

        Block target = player.getTargetBlockExact(5);
        if (target == null || target.getType() != Material.CAULDRON) {
            player.sendMessage(ChatColor.YELLOW + "Look at a cauldron within 5 blocks.");
            return true;
        }

        Location loc = target.getLocation().getBlock().getLocation();

        if (args.length > 0 && args[0].equalsIgnoreCase("free")) {
            cauldronManager.stopUsing(loc);
            player.sendMessage(ChatColor.GREEN + "Freed that cauldron.");
            return true;
        }

        // Simulate "someone else"
        UUID fake = UUID.nameUUIDFromBytes("SOMEONE_ELSE".getBytes());
        boolean success = cauldronManager.startUsing(fake, loc);

        if (success) {
            player.sendMessage(ChatColor.GREEN + "Occupied by someone else (fake user).");
        } else {
            player.sendMessage(ChatColor.RED + "That cauldron is already in use!");
        }

        return true;
    }
}
