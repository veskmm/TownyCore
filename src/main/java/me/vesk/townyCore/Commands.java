package me.vesk.townyCore;

import co.aikar.commands.BaseCommand;
import co.aikar.commands.PaperCommandManager;
import co.aikar.commands.annotation.CommandAlias;
import co.aikar.commands.annotation.CommandPermission;
import co.aikar.commands.annotation.Description;
import co.aikar.commands.annotation.Subcommand;
import com.palmergames.bukkit.towny.Towny;
import com.palmergames.bukkit.towny.TownyAPI;
import com.palmergames.bukkit.towny.object.Town;
import com.palmergames.bukkit.towny.object.TownBlock;
import com.palmergames.bukkit.towny.object.WorldCoord;
import com.palmergames.bukkit.towny.tasks.TownClaim;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@CommandAlias("townycore")
public class Commands extends BaseCommand {

    private final JavaPlugin plugin;
    private final ConfigManager configManager;
    private final TownsConfig townsConfig;
    private final Manager manager;
    private final BuildsConfig buildsConfig;
    private final TownyAPI apiTowny;

    public Commands(
            JavaPlugin plugin,
            ConfigManager configManager,
            TownsConfig townsConfig,
            Manager manager,
            BuildsConfig buildsConfig,
            PaperCommandManager ignoredCommandManager
    ) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.townsConfig = townsConfig;
        this.manager = manager;
        this.buildsConfig = buildsConfig;
        this.apiTowny = TownyAPI.getInstance();
    }

    @Subcommand("reload")
    @CommandPermission("townycore.admin.reload")
    public void pluginReload(CommandSender sender) {
        configManager.loadConfig();
        buildsConfig.reloadConfig();
        townsConfig.reloadConfig();
        sender.sendMessage(ChatColor.GREEN + "Конфигурации перезагружены.");
    }

    @Subcommand("claim")
    public void claimNewPlot(Player player) {
        String townName = apiTowny.getTownName(player);

        if (townName == null || townName.isBlank()) {
            player.sendMessage(configManager.getNotTown());
            return;
        }

        Town town = apiTowny.getTown(player);

        if (town == null) {
            player.sendMessage(configManager.getNotTown());
            return;
        }

        int currentLevel = townsConfig.getInt(townName + ".level_claim", 0);
        int nextLevel = currentLevel + 1;

        if (nextLevel > configManager.getLevelsClaim()) {
            player.sendMessage(configManager.getMaxClaimLevel());
            return;
        }

        if (nextLevel > 9) {
            player.sendMessage(ChatColor.RED
                    + "Текущая схема claim_level поддерживает только уровни 0–9.");
            return;
        }

        Location spawn = apiTowny.getTownSpawnLocation(player);

        if (spawn == null || spawn.getWorld() == null) {
            player.sendMessage(ChatColor.RED + "Не удалось определить спавн города.");
            return;
        }

        List<WorldCoord> selection = getClaimSelection(spawn, nextLevel);

        if (selection.isEmpty()) {
            player.sendMessage(ChatColor.RED + "Для следующего уровня не настроены чанки.");
            return;
        }

        List<List<Component>> demand = manager.checkDemand(player, true, nextLevel, false, "");

        if (!demand.get(0).isEmpty()) {
            manager.sendMissingDemandMessage(player, demand);
            return;
        }

        Map<Material, Integer> cost = manager.getDemandResources(true, nextLevel, false, "");

        /* Reserve payment first; refund only if Towny makes no claims. */
        if (!manager.withdrawMaterials(player, cost)) {
            player.sendMessage(ChatColor.RED + "Не удалось списать ресурсы.");
            return;
        }

        Bukkit.getScheduler().runTask(plugin, new TownClaim(
                Towny.getPlugin(),
                player,
                town,
                selection,
                false,
                true,
                false
        ));

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            boolean allClaimed = townOwnsAll(town, selection);
            boolean anyClaimed = townOwnsAny(town, selection);

            if (allClaimed) {
                townsConfig.setLine(townName + ".level_claim", nextLevel);
                manager.showTownBorder(townName);

                if (player.isOnline()) {
                    player.sendMessage(ChatColor.GREEN + "Уровень города повышен до " + nextLevel + ".");
                }
                return;
            }

            if (!anyClaimed) {
                manager.refundMaterials(player, cost);

                if (player.isOnline()) {
                    player.sendMessage(ChatColor.RED
                            + "Towny не смог заклеймить территорию. Ресурсы возвращены.");
                }
                return;
            }

            plugin.getLogger().warning("Towny выполнил частичный клейм для города "
                    + townName + ". Уровень не был повышен.");

            if (player.isOnline()) {
                player.sendMessage(ChatColor.RED
                        + "Towny заклеймил территорию только частично. Обратитесь к администрации.");
            }
        }, 2L);
    }

    @Subcommand("info")
    public void writeInfoTown(Player player) {
        String townName = apiTowny.getTownName(player);

        if (townName == null || townName.isBlank()) {
            player.sendMessage(configManager.getNotTown());
            return;
        }

        for (String line : configManager.getTownInfo()) {
            String message = line
                    .replace("{townName}", townName)
                    .replace("{claimLevel}", String.valueOf(
                            townsConfig.getInt(townName + ".level_claim", 0)
                    ));

            player.sendMessage(ChatColor.translateAlternateColorCodes('&', message));
        }
    }

    @Subcommand("builds add townybuild")
    @CommandPermission("townycore.admin.builds")
    public void onTownyBuildAdd(Player player, String name, Location first, Location second) {
        if (manager.addBuild(first, second, player, name)) {
            player.sendMessage(ChatColor.GREEN + "Шаблон здания успешно добавлен.");
        }
    }

    @Subcommand("builds make townybuild")
    @CommandPermission("townycore.admin.builds")
    public void onTownyBuildMake(Player player, String name) {
        Block foundation = player.getTargetBlockExact(10);

        if (foundation == null) {
            player.sendMessage(ChatColor.RED + "Посмотри на блок-фундамент в радиусе 10 блоков.");
            return;
        }

        Location origin = foundation.getRelative(BlockFace.UP).getLocation();

        if (manager.makeBuild(origin, player, name)) {
            player.sendMessage(ChatColor.GREEN + "Здание успешно построено.");
        }
    }

    @Subcommand("builds")
    @Description("Меню зданий")
    public void openTownyBuildMenu(Player player) {
        BuildMenu menu = new BuildMenu(
                player,
                buildsConfig,
                manager,
                townsConfig,
                configManager,
                plugin
        );

        plugin.getServer().getPluginManager().registerEvents(menu, plugin);
        manager.registerBuildMenu(menu);
        menu.openMenu();
    }

    @Subcommand("build_accept")
    public void buildAccept(Player player) {
        BuildMenu menu = manager.getBuildMenu(player.getUniqueId());

        if (menu == null) {
            player.sendMessage(ChatColor.RED + "Нет активной постройки.");
            return;
        }

        menu.acceptBuild();
    }

    @Subcommand("build_cancel")
    public void buildCancel(Player player) {
        BuildMenu menu = manager.getBuildMenu(player.getUniqueId());

        if (menu == null) {
            player.sendMessage(ChatColor.RED + "Нет активной постройки.");
            return;
        }

        menu.cancelBuild();
    }

    private List<WorldCoord> getClaimSelection(Location townSpawn, int level) {
        List<String> rows = configManager.getClaimPlots(String.valueOf(level));

        if (rows.isEmpty()) {
            return Collections.emptyList();
        }

        World world = townSpawn.getWorld();

        if (world == null) {
            return Collections.emptyList();
        }

        int spawnChunkX = Math.floorDiv(townSpawn.getBlockX(), 16);
        int spawnChunkZ = Math.floorDiv(townSpawn.getBlockZ(), 16);
        int centerRow = rows.size() / 2;
        char expected = Character.forDigit(level, 10);
        Set<WorldCoord> result = new LinkedHashSet<>();

        for (int rowIndex = 0; rowIndex < rows.size(); rowIndex++) {
            String row = rows.get(rowIndex);

            if (row == null || row.isEmpty()) {
                continue;
            }

            int centerColumn = row.length() / 2;

            for (int columnIndex = 0; columnIndex < row.length(); columnIndex++) {
                if (row.charAt(columnIndex) != expected) {
                    continue;
                }

                result.add(new WorldCoord(
                        world.getName(),
                        spawnChunkX + (columnIndex - centerColumn),
                        spawnChunkZ + (rowIndex - centerRow)
                ));
            }
        }

        return new ArrayList<>(result);
    }

    private boolean isClaimSelectionFree(Location reference, List<WorldCoord> selection) {
        World world = reference.getWorld();

        if (world == null) {
            return false;
        }

        for (WorldCoord coord : selection) {
            Location check = new Location(
                    world,
                    coord.getX() * 16 + 8,
                    reference.getBlockY(),
                    coord.getZ() * 16 + 8
            );

            String ownerTown = apiTowny.getTownName(check);

            if (ownerTown != null && !ownerTown.isBlank()) {
                return false;
            }
        }

        return true;
    }

    private boolean townOwnsAll(Town town, List<WorldCoord> selection) {
        Set<WorldCoord> chunks = new HashSet<>();

        for (TownBlock block : town.getTownBlocks()) {
            chunks.add(block.getWorldCoord());
        }

        return chunks.containsAll(selection);
    }

    private boolean townOwnsAny(Town town, List<WorldCoord> selection) {
        Set<WorldCoord> chunks = new HashSet<>();

        for (TownBlock block : town.getTownBlocks()) {
            chunks.add(block.getWorldCoord());
        }

        for (WorldCoord coord : selection) {
            if (chunks.contains(coord)) {
                return true;
            }
        }

        return false;
    }
}
