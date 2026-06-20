package me.vesk.townyCore;

import com.palmergames.bukkit.towny.Towny;
import com.palmergames.bukkit.towny.TownyAPI;
import com.palmergames.bukkit.towny.event.*;

import com.palmergames.bukkit.towny.object.Coord;
import com.palmergames.bukkit.towny.object.WorldCoord;
import com.palmergames.bukkit.towny.tasks.TownClaim;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.block.data.BlockData;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;

import java.awt.*;
import java.nio.Buffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.kyori.adventure.text.Component;

public class TownyListener implements Listener {
    final JavaPlugin plugin;
    final Manager manager;
    final ConfigManager configManager;
    final TownsConfig townsConfig;

    private TownyAPI api;

    public TownyListener(JavaPlugin plugin, Manager manager, ConfigManager configManager, TownsConfig townsConfig) {
        this.plugin = plugin;
        this.manager = manager;
        this.configManager = configManager;
        this.townsConfig = townsConfig;

        this.api = TownyAPI.getInstance();
    }

    @EventHandler
    public void townCreate(NewTownEvent event) {
        manager.showTownBorder(event.getTown().getName());
    }

    @EventHandler
    public void onTownPreClaim(PreNewTownEvent event) {
        List<List<Component>> firthResult = manager.checkDemand(event.getPlayer(),false,0,false,"");

        List<Component> missingComponents = firthResult.get(0);
        List<Component> missingAmounts = firthResult.get(1);

        event.getPlayer().sendMessage("");
        if (missingComponents.isEmpty()) {
            manager.writingOffDemand(event.getPlayer(),false,0,false,"");
            String filledMessage = configManager.getCreateTown().replace("{newTown}",event.getTownName());
            String coloredMessage = ChatColor.translateAlternateColorCodes('&', filledMessage);
            event.getPlayer().sendMessage(coloredMessage);

            townsConfig.setLine(event.getTownName()+".level_claim",0);
            townsConfig.setLine(event.getTownName()+".mayor",event.getPlayer().getName());
            townsConfig.setLine(event.getTownName()+".position_x",event.getTownWorldCoord().getX());
            townsConfig.setLine(event.getTownName()+".position_z",event.getTownWorldCoord().getZ());
            townsConfig.setLine(event.getTownName()+".oldBorder", new HashMap<Location, BlockData>());
        }
        else {
            event.setCancelMessage("");
            event.setCancelled(true);
            Component message = Component.empty();

            int i = 0;
            for (Component mC : missingComponents) {
                message = message.append(Component.newline())
                        .append(Component.text("- "))
                        .append(mC)
                        .append(Component.text(" "))
                        .append(missingAmounts.get(i).color(NamedTextColor.GOLD))
                        .append(Component.text(" штук").color(NamedTextColor.GOLD));
                i++;
            }

            String rawMessage = configManager.getNotEnoughResources();
            String filledMessage = rawMessage.replace("{missingResources}", "");

            String coloredMessage = ChatColor.translateAlternateColorCodes('&', filledMessage);
            event.getPlayer().sendMessage(coloredMessage);
            event.getPlayer().sendMessage(message);
        }
        event.getPlayer().sendMessage("");
    }


    @EventHandler
    public void deletedTown(DeleteTownEvent event) {
        townsConfig.setLine(event.getTownName(), null);
    }

    @EventHandler
    public void claimTown(TownClaimEvent event) {
        manager.showTownBorder(event.getTown().getName());
    }
}
