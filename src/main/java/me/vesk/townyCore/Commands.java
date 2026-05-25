package me.vesk.townyCore;

import co.aikar.commands.BaseCommand;
import co.aikar.commands.annotation.CommandAlias;
import co.aikar.commands.annotation.Subcommand;
import com.palmergames.bukkit.towny.Towny;
import com.palmergames.bukkit.towny.TownyAPI;
import com.palmergames.bukkit.towny.event.TownPreClaimEvent;
import com.palmergames.bukkit.towny.object.WorldCoord;
import com.palmergames.bukkit.towny.tasks.TownClaim;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;

@CommandAlias("townycore")
public class Commands extends BaseCommand {

    private final JavaPlugin plugin;
    private final ConfigManager configManager;
    private final TownsConfig townsConfig;

    private TownyAPI apiTowny;

    public Commands(JavaPlugin plugin, ConfigManager configManager, TownsConfig townsConfig) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.townsConfig = townsConfig;

        apiTowny = TownyAPI.getInstance();
    }

    @Subcommand("reload")
    public void pluginReload(CommandSender sender) {
        plugin.reloadConfig();
        configManager.loadConfig();
        sender.sendMessage("Конфигурация перезагружена.");
    }

    @Subcommand("claim")
    public void claimNewPlot(Player player) {

        int townLevelClaim = townsConfig.getInt(apiTowny.getTownName(player)+".level_claim");
        ArrayList<String> claimPlotsLevel = configManager.getClaimPlots(String.valueOf(townLevelClaim+1));

        List<WorldCoord> selection = new ArrayList<>();

        for (int i = 0; i < claimPlotsLevel.size(); i++) {
            String plots = claimPlotsLevel.get(i);
            for (int j = 0; j < plots.length(); j++) {
                int expectedNumber = townLevelClaim + 1;
                char expectedChar = (char) ('0' + expectedNumber); // преобразуем цифру в символ

                if (plots.charAt(j) != expectedChar) {
                    continue;
                }

                Location townSpawn = apiTowny.getTownSpawnLocation(player);

                if (townSpawn == null) {continue;}

                int blockX = townSpawn.getBlockX();
                int blockZ = townSpawn.getBlockZ();

                int chunkXSpawn = blockX / 16;
                int chunkZSpawn = blockZ / 16;

                int chunkX = chunkXSpawn+(i-3);
                int chunkZ = chunkZSpawn+(j-3);

                selection.add(new WorldCoord(townSpawn.getWorld().getName(), chunkX, chunkZ));
            }
        }

        Towny townyPlugin = Towny.getPlugin();

        Bukkit.getScheduler().runTask(plugin, new TownClaim(
                townyPlugin, player, apiTowny.getTown(player), selection,
                false, true, false));

        townsConfig.setLine(apiTowny.getTownName(player)+".level_claim", townLevelClaim + 1);
    }
}
