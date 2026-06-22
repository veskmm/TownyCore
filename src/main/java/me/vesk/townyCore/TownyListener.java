package me.vesk.townyCore;

import com.palmergames.bukkit.towny.TownyAPI;
import com.palmergames.bukkit.towny.event.DeleteTownEvent;
import com.palmergames.bukkit.towny.event.NewTownEvent;
import com.palmergames.bukkit.towny.event.PreNewTownEvent;
import com.palmergames.bukkit.towny.event.TownClaimEvent;
import com.palmergames.bukkit.towny.object.WorldCoord;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public class TownyListener implements Listener {

    private final JavaPlugin plugin;
    private final Manager manager;
    private final ConfigManager configManager;
    private final TownsConfig townsConfig;
    private final TownyAPI api;
    private final Map<String, PendingTownCreation> pendingTownCreations = new HashMap<>();

    public TownyListener(
            JavaPlugin plugin,
            Manager manager,
            ConfigManager configManager,
            TownsConfig townsConfig
    ) {
        this.plugin = plugin;
        this.manager = manager;
        this.configManager = configManager;
        this.townsConfig = townsConfig;
        this.api = TownyAPI.getInstance();
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onTownPreClaim(PreNewTownEvent event) {
        Player player = event.getPlayer();
        List<List<Component>> demand = manager.checkDemand(player, false, 0, false, "");

        if (!demand.get(0).isEmpty()) {
            event.setCancelMessage("");
            event.setCancelled(true);
            manager.sendMissingDemandMessage(player, demand);
            return;
        }

        String townKey = event.getTownName().toLowerCase(Locale.ROOT);

        pendingTownCreations.put(townKey, new PendingTownCreation(
                player.getUniqueId(),
                manager.getDemandResources(false, 0, false, ""),
                event.getTownWorldCoord()
        ));

        Bukkit.getScheduler().runTaskLater(
                plugin,
                () -> pendingTownCreations.remove(townKey),
                2L
        );
    }

    @EventHandler
    public void townCreate(NewTownEvent event) {
        String townName = event.getTown().getName();
        PendingTownCreation pending = pendingTownCreations.remove(townName.toLowerCase(Locale.ROOT));

        if (pending == null) {
            townsConfig.ensureTown(townName);
            manager.showTownBorder(townName);
            return;
        }

        Player player = Bukkit.getPlayer(pending.playerId());
        String mayorName = player == null ? "unknown" : player.getName();

        townsConfig.initializeTown(townName, mayorName, pending.homeCoord());

        if (player == null || !manager.withdrawMaterials(player, pending.cost())) {
            plugin.getLogger().severe("Город " + townName
                    + " создан, но ресурсы не удалось списать.");
        } else {
            player.sendMessage(ChatColor.translateAlternateColorCodes(
                    '&',
                    configManager.getCreateTown().replace("{newTown}", townName)
            ));
        }

        manager.showTownBorder(townName);
    }

    @EventHandler
    public void deletedTown(DeleteTownEvent event) {
        String townName = event.getTownName();
        manager.clearBorder(townName);
        townsConfig.removeTown(townName);
    }

    @EventHandler
    public void claimTown(TownClaimEvent event) {
        manager.showTownBorder(event.getTown().getName());
    }

    private record PendingTownCreation(
            UUID playerId,
            Map<Material, Integer> cost,
            WorldCoord homeCoord
    ) {
        private PendingTownCreation {
            cost = new LinkedHashMap<>(cost);
        }
    }
}
