package me.vesk.townyCore;

import com.palmergames.bukkit.towny.TownyAPI;
import com.palmergames.bukkit.towny.object.Coord;
import com.palmergames.bukkit.towny.object.Town;
import com.palmergames.bukkit.towny.object.TownBlock;
import com.palmergames.bukkit.towny.object.WorldCoord;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.*;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import org.eclipse.aether.collection.CollectStepData;

public class Manager implements Listener {
    private final JavaPlugin plugin;
    private Map<String, Integer> demand = new HashMap<>();
    private final ConfigManager configManager;
    private final BuildsConfig buildsConfig;
    private final TownsConfig townsConfig;

    private TownyAPI apiTowny = TownyAPI.getInstance();

    public Manager(JavaPlugin plugin, ConfigManager configManager, BuildsConfig buildsConfig, TownsConfig townsConfig) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.buildsConfig = buildsConfig;
        this.townsConfig = townsConfig;
    }

    public ArrayList<BuildMenu> buildMenus = new ArrayList<>();

    public void writingOffDemand(Player player, Boolean is_claim, Integer level_claim, Boolean is_build, String nameBuld) {
        Map<Material, Integer> demandResources = configManager.getDemand("demand.resources");

        if (is_claim) {
            demandResources = configManager.getDemandClaim(level_claim);
        }
        if (is_build) {
            demandResources = buildsConfig.getDemandBuild(nameBuld);
        }

        for (Map.Entry<Material, Integer> entry : demandResources.entrySet()) {

            for (int j = 0; j < player.getInventory().getSize(); j++) {
                ItemStack item = player.getInventory().getItem(j);

                if (item == null || item.getType() == Material.AIR) {
                    continue;  // пустой слот — пропускаем
                }

                if (item.getType() != entry.getKey()) {
                    continue;
                }

                int toRemove = entry.getValue();
                int itemAmout = item.getAmount();

                if (toRemove >= itemAmout) {
                    player.getInventory().remove(item);
                    toRemove -= itemAmout;
                } else {
                    item.setAmount(itemAmout - toRemove);
                    toRemove = 0;
                    break;
                }

            }
        }

    }

    public List<List<Component>> checkDemand(Player player, Boolean is_claim, Integer level_claim, Boolean is_build, String nameBuld) {
        ItemStack[] playerResources = player.getInventory().getStorageContents();
        Map<Material, Integer> demandResources = configManager.getDemand("demand.resources");
        if (is_claim) {
            demandResources = configManager.getDemandClaim(level_claim);
        }
        if (is_build) {
            demandResources = buildsConfig.getDemandBuild(nameBuld);
        }

        for (int i = 0; i < playerResources.length; i++) {
            if (playerResources[i] == null) {
                continue;
            }
            ItemStack itemStack = playerResources[i];
            Material material = playerResources[i].getType();
            if (demandResources.containsKey(material)) {
                int amount = playerResources[i].getAmount();
                int remaining = demandResources.get(material) - amount;
                if (remaining <= 0) {
                    demandResources.remove(material);
                } else {
                    demandResources.put(material, remaining);
                }
            }
        }

        List<Component> missingComponents = new ArrayList<>();
        List<Component> missingAmount = new ArrayList<>();

        if (!demandResources.isEmpty()) {
            for (Map.Entry<Material, Integer> entry : demandResources.entrySet()) {
                String translationKey = entry.getKey().translationKey();
                Component missing = Component.translatable(translationKey);
                missingComponents.add(missing);
                missingAmount.add(Component.text(entry.getValue().toString()));
            }
        }

        List<List<Component>> finalResult = new ArrayList<>();
        finalResult.add(missingComponents);
        finalResult.add(missingAmount);

        return finalResult;
    }

    public void makeBuild(Location cord, Player player, String nameBuld) {
        List<List<Component>> firthResult = checkDemand(player, false, 10, true, nameBuld);

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
        BlockData[][][] matrix_blocks = buildsConfig.loadMatrix(nameBuld + ".blocks");

        int playerX = (int) cord.x();
        int playerY = (int) cord.y();
        int playerZ = (int) cord.z();

        World world = player.getWorld();

        // Матрица может быть пустой или иметь нулевую длину
        if (matrix_blocks == null || matrix_blocks.length == 0) {
            player.sendMessage("Матрица пуста или имеет нулевую длину, сообщите администрации");
            return;
        }

        if (apiTowny.getTownName(player) == null) {
            player.sendMessage(configManager.getNotTown());
            return;
        }
        if (apiTowny.getTownName(player).equals("")) {
            player.sendMessage(configManager.getNotTown());
            return;
        }
        for (int Y = playerY; Y < matrix_blocks.length + playerY; Y++) {
            for (int X = playerX; X < matrix_blocks[0].length + playerX; X++) {
                for (int Z = playerZ; Z < matrix_blocks[0][0].length + playerZ; Z++) {
                    Location blockLocation = new Location(world, X, Y, Z);
                    String townName = apiTowny.getTownName(blockLocation);

                    if (townName != null) {
                        if (!(townName.equals(apiTowny.getTownName(player)))) {
                            player.sendMessage(configManager.getNotTownPos());
                            return;
                        }
                    } else {
                        player.sendMessage(configManager.getNotTownPos());
                        return;
                    }
                }
            }
        }

        for (int Y = playerY; Y < matrix_blocks.length + playerY; Y++) {
            for (int X = playerX; X < matrix_blocks[0].length + playerX; X++) {
                for (int Z = playerZ; Z < matrix_blocks[0][0].length + playerZ; Z++) {
                    world.getBlockAt(X, Y, Z).setBlockData(matrix_blocks[Y - playerY][X - playerX][Z - playerZ]);
                }
            }
        }
        writingOffDemand(player, false, 0, true, nameBuld);
        townsConfig.setHasBuild(apiTowny.getTownName(player), nameBuld);
    }

    public boolean addBuild(Location cord1, Location cord2, Player player, String nameBuild) {

        int x1 = (int) cord1.x();
        int y1 = (int) cord1.y();
        int z1 = (int) cord1.z();

        int x2 = (int) cord2.x();
        int y2 = (int) cord2.y();
        int z2 = (int) cord2.z();

        int maxX = Integer.max(x1, x2);
        int maxY = Integer.max(y1, y2);
        int maxZ = Integer.max(z1, z2);

        int minX = Integer.min(x1, x2);
        int minY = Integer.min(y1, y2);
        int minZ = Integer.min(z1, z2);

        // Создаем трехмерный массив
        BlockData[][][] build_matrix = new BlockData[maxY - minY][maxX - minX][maxZ - minZ];

        World world = player.getWorld();

        for (int Y = minY; Y < maxY; Y++) {
            for (int X = minX; X < maxX; X++) {
                for (int Z = minZ; Z < maxZ; Z++) {
                    build_matrix[Y - minY][X - minX][Z - minZ] = world.getBlockData(X, Y, Z);
                }
            }
        }

        buildsConfig.createBuildToConfig(nameBuild, build_matrix, player.getWorld());

        return true;
    }

    public void showTownBorder(String townName) {
        Town town = apiTowny.getTown(townName);

        if (town == null) {
            return;
        }

        Collection<TownBlock> townBlocks = town.getTownBlocks();

        if (townBlocks == null || townBlocks.isEmpty()) {
            return;
        }

        // Сначала восстанавливаем предыдущую границу.
        clearBorder(townName);

        Set<WorldCoord> townChunks = new HashSet<>();

        for (TownBlock block : townBlocks) {
            townChunks.add(block.getWorldCoord());
        }

        // Здесь сохраняем блоки, которые будут заменены границей.
        Map<Location, BlockData> oldBlocks = new HashMap<>();

        for (TownBlock townBlock : townBlocks) {
            WorldCoord coord = townBlock.getWorldCoord();

            int chunkX = coord.getX();
            int chunkZ = coord.getZ();
            String worldName = coord.getWorldName();

            World world = Bukkit.getWorld(worldName);

            if (world == null) {
                continue;
            }

            int minX = chunkX * 16;
            int maxX = minX + 15;
            int minZ = chunkZ * 16;
            int maxZ = minZ + 15;

            boolean northFree = !townChunks.contains(
                    new WorldCoord(worldName, chunkX, chunkZ - 1)
            );

            boolean southFree = !townChunks.contains(
                    new WorldCoord(worldName, chunkX, chunkZ + 1)
            );

            boolean westFree = !townChunks.contains(
                    new WorldCoord(worldName, chunkX - 1, chunkZ)
            );

            boolean eastFree = !townChunks.contains(
                    new WorldCoord(worldName, chunkX + 1, chunkZ)
            );

            // Север: Z = minZ
            if (northFree) {
                for (int x = minX; x <= maxX; x++) {
                    placeBorderOnSurface(world, x, minZ, oldBlocks);
                }
            }

            // Юг: Z = maxZ
            if (southFree) {
                for (int x = minX; x <= maxX; x++) {
                    placeBorderOnSurface(world, x, maxZ, oldBlocks);
                }
            }

            // Запад: X = minX
            if (westFree) {
                for (int z = minZ; z <= maxZ; z++) {
                    placeBorderOnSurface(world, minX, z, oldBlocks);
                }
            }

            // Восток: X = maxX
            if (eastFree) {
                for (int z = minZ; z <= maxZ; z++) {
                    placeBorderOnSurface(world, maxX, z, oldBlocks);
                }
            }
        }

        townsConfig.saveOldBorder(townName, oldBlocks);
    }

    private void placeBorderOnSurface(
            World world,
            int x,
            int z,
            Map<Location, BlockData> oldBlocks
    ) {
        Block ground = world.getHighestBlockAt(
                x,
                z,
                HeightMap.MOTION_BLOCKING_NO_LEAVES
        );

        int y = ground.getY() + 1;

        // Верхний предел мира: выше него блок ставить нельзя.
        if (y >= world.getMaxHeight()) {
            return;
        }

        Location location = new Location(world, x, y, z);
        Block borderBlock = location.getBlock();

        // Углы обрабатываются два раза, поэтому исходный блок сохраняем лишь один раз.
        oldBlocks.putIfAbsent(
                location,
                borderBlock.getBlockData().clone()
        );

        borderBlock.setType(Material.YELLOW_CONCRETE, false);
    }

    private void placeBorderBlock(Location loc, String townName) {
        Block block = loc.getBlock();
        block.setType(Material.YELLOW_CONCRETE); // Или любой другой блок

    }

    public void clearBorder(String townName) {
        // Используй тот же ключ, который передаёшь в saveOldBorder().
        Map<Location, BlockData> oldBlocks = townsConfig.loadOldBorder(townName);

        if (oldBlocks == null || oldBlocks.isEmpty()) {
            return;
        }

        for (Map.Entry<Location, BlockData> entry : oldBlocks.entrySet()) {
            Location location = entry.getKey();

            if (location.getWorld() == null) {
                continue;
            }

            location.getBlock().setBlockData(entry.getValue(), false);
        }
    }
}


