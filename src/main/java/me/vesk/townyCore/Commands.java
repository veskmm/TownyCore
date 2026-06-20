package me.vesk.townyCore;

import co.aikar.commands.BaseCommand;
import co.aikar.commands.PaperCommandManager;
import co.aikar.commands.annotation.*;
import com.palmergames.bukkit.towny.Towny;
import com.palmergames.bukkit.towny.TownyAPI;
import com.palmergames.bukkit.towny.event.TownPreClaimEvent;
import com.palmergames.bukkit.towny.object.WorldCoord;
import com.palmergames.bukkit.towny.tasks.TownClaim;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;

import com.palmergames.bukkit.towny.TownyAPI;

@CommandAlias("townycore")
public class Commands extends BaseCommand {

    private final JavaPlugin plugin;
    private final ConfigManager configManager;
    private final TownsConfig townsConfig;
    private final Manager manager;
    private final BuildsConfig buildsConfig;
    private final PaperCommandManager commandManager;

    private TownyAPI apiTowny;

    public Commands(JavaPlugin plugin, ConfigManager configManager, TownsConfig townsConfig, Manager manager, BuildsConfig buildsConfig,PaperCommandManager commandManager) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.townsConfig = townsConfig;
        this.manager = manager;
        this.buildsConfig = buildsConfig;
        this.commandManager = commandManager;

        apiTowny = TownyAPI.getInstance();
    }

    @Subcommand("reload")
    public void pluginReload(CommandSender sender) {
        plugin.reloadConfig();
        configManager.loadConfig();
        buildsConfig.loadConfig();
        sender.sendMessage("Конфигурация перезагружена.");
    }

    @Subcommand("claim")
    public void claimNewPlot(Player player) {
        int townLevelClaim = townsConfig.getInt(apiTowny.getTownName(player)+".level_claim");

        if (townLevelClaim+1 >= configManager.getLevelsClaim()) {
            player.sendMessage(configManager.getMaxClaimLevel());
            return;
        }

        List<List<Component>> firthResult = manager.checkDemand(player,true,townLevelClaim+1,false,"");

        List<Component> missingComponents = firthResult.get(0);
        List<Component> missingAmounts = firthResult.get(1);

        if (!missingComponents.isEmpty()) {
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
            player.sendMessage(coloredMessage);
            player.sendMessage(message);

            return;
        }

        manager.writingOffDemand(player,true,townLevelClaim+1,false,"");
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

    @Subcommand("info")
    public void writeInfoTown(CommandSender sender) {
        if (!(sender instanceof Player)) {
            return;
        }
        Player playerSender = (Player) sender;
        if (apiTowny.getTownName(playerSender) == null) return;
        if (apiTowny.getTownName(playerSender).equals("")) return;
        ArrayList<String> listMessage = configManager.getTownInfo();
        for (int i = 0; i < listMessage.size(); i++) {
            String message = listMessage.get(i);
            message = message.replace("{townName}", apiTowny.getTownName(playerSender));
            message = message.replace("{claimLevel}",townsConfig.getInt(apiTowny.getTownName(playerSender)+
                    ".level_claim").toString());
            playerSender.sendMessage(message);
        }

    }

    @Subcommand("builds add townybuild")
    //@CommandPermission("townycore.builds.admin")
    public void onTownyBuildAdd(
            CommandSender sender,
            String name,
            Location cord1,
            Location cord2
    )
    {
        if (!(sender instanceof Player)) return;
        Player playerSender = (Player) sender;

        boolean result =  manager.addBuild(cord1,cord2,playerSender,name);

        if (result) {
            playerSender.sendMessage("Здание успешно добавлено");
        }
    }

    @Subcommand("builds make townybuild")
    //@CommandPermission("townycore.builds.admin")
    public void onTownyBuildMake(
            CommandSender sender,
            String name
    )
    {
        if (!(sender instanceof Player)) return;
        Player PlayerSender = (Player) sender;

        manager.makeBuild(PlayerSender.getLocation(),PlayerSender,name);
    }

    @Subcommand("builds")
    @Description("Меню зданий")
    public void openTownyBuildMenu(CommandSender sender) {
        if (sender instanceof Player) {
            BuildMenu buildMenu = new BuildMenu((Player) sender,buildsConfig,manager,townsConfig,configManager);
            plugin.getServer().getPluginManager().registerEvents(buildMenu,plugin);
            manager.buildMenus.add(buildMenu);
            buildMenu.openMenu();
        }
    }

    @Subcommand("build_accept")
    public void buildAccept(CommandSender sender) {
        if (sender instanceof Player) {
            Player player = (Player) sender;
            for (int i = 0; i < manager.buildMenus.size(); i++) {
                plugin.getLogger().warning("ищем");
                if (manager.buildMenus.get(i).getPlayer().getUniqueId() == player.getUniqueId()) {
                    manager.buildMenus.get(i).acceptBuild();
                    return;
                }
                else {
                    plugin.getLogger().warning("не нашли");
                }
            }
        }
    }

    @Subcommand("build_cancel")
    public void buildCancel(CommandSender sender) {
        if (sender instanceof Player) {
            Player player = (Player) sender;
            for (int i = 0; i < manager.buildMenus.size(); i++) {
                if (manager.buildMenus.get(i).getPlayer().getUniqueId() == player.getUniqueId() ) {
                    manager.buildMenus.get(i).cancelBuild();
                    return;
                }
            }
        }
    }
}
