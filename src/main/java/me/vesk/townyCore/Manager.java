package me.vesk.townyCore;

import com.palmergames.bukkit.towny.TownyAPI;
import com.palmergames.bukkit.towny.object.Town;
import com.palmergames.bukkit.towny.object.TownBlock;
import com.palmergames.bukkit.towny.object.WorldCoord;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.HeightMap;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;

public class Manager implements Listener {

    private static final long MAX_TEMPLATE_VOLUME = 100_000L;

    private final JavaPlugin plugin;
    private final ConfigManager configManager;
    private final BuildsConfig buildsConfig;
    private final TownsConfig townsConfig;
    private final TownyAPI apiTowny;

    private final Map<UUID, BuildMenu> buildMenus = new HashMap<>();

    public Manager(
            JavaPlugin plugin,
            ConfigManager configManager,
            BuildsConfig buildsConfig,
            TownsConfig townsConfig
    ) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.buildsConfig = buildsConfig;
        this.townsConfig = townsConfig;
        this.apiTowny = TownyAPI.getInstance();
    }

    public void registerBuildMenu(BuildMenu menu) {
        BuildMenu oldMenu = buildMenus.put(menu.getPlayerId(), menu);

        if (oldMenu != null && oldMenu != menu) {
            oldMenu.shutdown();
        }
    }

    public BuildMenu getBuildMenu(UUID playerId) {
        return buildMenus.get(playerId);
    }

    public void removeBuildMenu(UUID playerId, BuildMenu menu) {
        buildMenus.remove(playerId, menu);
    }

    public void shutdownBuildMenus() {
        List<BuildMenu> activeMenus = new ArrayList<>(buildMenus.values());

        for (BuildMenu menu : activeMenus) {
            menu.shutdown();
        }

        buildMenus.clear();
    }

    public Map<Material, Integer> getDemandResources(
            boolean isClaim,
            int claimLevel,
            boolean isBuild,
            String buildName
    ) {
        Map<Material, Integer> source;

        if (isBuild) {
            source = buildsConfig.getDemandBuild(buildName);
        } else if (isClaim) {
            source = configManager.getDemandClaim(claimLevel);
        } else {
            source = configManager.getDemand("demand.resources");
        }

        Map<Material, Integer> result = new LinkedHashMap<>();

        if (source == null) {
            return result;
        }

        for (Map.Entry<Material, Integer> entry : source.entrySet()) {
            Material material = entry.getKey();
            Integer amount = entry.getValue();

            if (material == null || material.isAir() || amount == null || amount <= 0) {
                continue;
            }

            result.merge(material, amount, Integer::sum);
        }

        return result;
    }

    public boolean hasMaterials(Player player, Map<Material, Integer> required) {
        if (player == null || required == null || required.isEmpty()) {
            return true;
        }

        Map<Material, Integer> remaining = new LinkedHashMap<>(required);

        for (ItemStack item : player.getInventory().getStorageContents()) {
            if (item == null || item.getType().isAir()) {
                continue;
            }

            Material material = item.getType();
            Integer needed = remaining.get(material);

            if (needed == null) {
                continue;
            }

            int left = needed - item.getAmount();

            if (left <= 0) {
                remaining.remove(material);
            } else {
                remaining.put(material, left);
            }

            if (remaining.isEmpty()) {
                return true;
            }
        }

        return remaining.isEmpty();
    }

    public boolean writingOffDemand(
            Player player,
            boolean isClaim,
            int claimLevel,
            boolean isBuild,
            String buildName
    ) {
        return withdrawMaterials(
                player,
                getDemandResources(isClaim, claimLevel, isBuild, buildName)
        );
    }

    public boolean withdrawMaterials(
            Player player,
            Map<Material, Integer> required
    ) {
        if (player == null || required == null || required.isEmpty()) {
            return true;
        }

        if (!hasMaterials(player, required)) {
            return false;
        }

        ItemStack[] contents = player.getInventory().getStorageContents();

        for (Map.Entry<Material, Integer> entry : required.entrySet()) {
            Material requiredMaterial = entry.getKey();
            int remainingToRemove = entry.getValue();

            for (int slot = 0; slot < contents.length; slot++) {
                ItemStack item = contents[slot];

                if (item == null || item.getType() != requiredMaterial) {
                    continue;
                }

                int removeNow = Math.min(remainingToRemove, item.getAmount());
                int newAmount = item.getAmount() - removeNow;
                remainingToRemove -= removeNow;

                if (newAmount <= 0) {
                    contents[slot] = null;
                } else {
                    ItemStack changed = item.clone();
                    changed.setAmount(newAmount);
                    contents[slot] = changed;
                }

                if (remainingToRemove == 0) {
                    break;
                }
            }

            if (remainingToRemove != 0) {
                return false;
            }
        }

        player.getInventory().setStorageContents(contents);
        return true;
    }

    public void refundMaterials(Player player, Map<Material, Integer> resources) {
        if (player == null || !player.isOnline() || resources == null || resources.isEmpty()) {
            return;
        }

        for (Map.Entry<Material, Integer> entry : resources.entrySet()) {
            ItemStack stack = new ItemStack(entry.getKey(), entry.getValue());
            Map<Integer, ItemStack> leftovers = player.getInventory().addItem(stack);

            for (ItemStack leftover : leftovers.values()) {
                player.getWorld().dropItemNaturally(player.getLocation(), leftover);
            }
        }
    }

    public List<List<Component>> checkDemand(
            Player player,
            boolean isClaim,
            int claimLevel,
            boolean isBuild,
            String buildName
    ) {
        Map<Material, Integer> remaining = getDemandResources(
                isClaim,
                claimLevel,
                isBuild,
                buildName
        );

        for (ItemStack item : player.getInventory().getStorageContents()) {
            if (item == null || item.getType().isAir()) {
                continue;
            }

            Material material = item.getType();
            Integer needed = remaining.get(material);

            if (needed == null) {
                continue;
            }

            int left = needed - item.getAmount();

            if (left <= 0) {
                remaining.remove(material);
            } else {
                remaining.put(material, left);
            }
        }

        List<Component> missingComponents = new ArrayList<>();
        List<Component> missingAmounts = new ArrayList<>();

        for (Map.Entry<Material, Integer> entry : remaining.entrySet()) {
            missingComponents.add(Component.translatable(entry.getKey().translationKey()));
            missingAmounts.add(Component.text(entry.getValue()));
        }

        List<List<Component>> result = new ArrayList<>();
        result.add(missingComponents);
        result.add(missingAmounts);

        return result;
    }

    public void sendMissingDemandMessage(Player player, List<List<Component>> result) {
        if (result == null || result.size() < 2 || result.get(0) == null || result.get(1) == null) {
            player.sendMessage(ChatColor.RED + "Не удалось проверить ресурсы.");
            return;
        }

        List<Component> missingComponents = result.get(0);
        List<Component> missingAmounts = result.get(1);

        if (missingComponents.isEmpty()) {
            return;
        }

        Component message = Component.empty();

        for (int i = 0; i < missingComponents.size(); i++) {
            Component amount = i < missingAmounts.size()
                    ? missingAmounts.get(i)
                    : Component.text("?");

            message = message.append(Component.newline())
                    .append(Component.text("- "))
                    .append(missingComponents.get(i))
                    .append(Component.text(" "))
                    .append(amount.color(NamedTextColor.GOLD))
                    .append(Component.text(" штук").color(NamedTextColor.GOLD));
        }

        String rawMessage = configManager.getNotEnoughResources();
        player.sendMessage(ChatColor.translateAlternateColorCodes(
                '&',
                rawMessage.replace("{missingResources}", "")
        ));
        player.sendMessage(message);
    }

    public boolean makeBuild(Location origin, Player player, String buildName) {
        if (origin == null || origin.getWorld() == null) {
            player.sendMessage(ChatColor.RED + "Не удалось определить место постройки.");
            return false;
        }

        if (buildName == null || buildName.isBlank()) {
            player.sendMessage(ChatColor.RED + "Не найден шаблон здания.");
            return false;
        }

        BlockData[][][] matrix = buildsConfig.loadMatrix(buildName + ".blocks");

        if (!isValidMatrix(matrix)) {
            player.sendMessage(ChatColor.RED + "Схема здания пуста или повреждена.");
            return false;
        }

        String playerTownName = apiTowny.getTownName(player);

        if (playerTownName == null || playerTownName.isBlank()) {
            player.sendMessage(configManager.getNotTown());
            return false;
        }

        World world = origin.getWorld();
        int startX = origin.getBlockX();
        int startY = origin.getBlockY();
        int startZ = origin.getBlockZ();

        int sizeY = matrix.length;
        int sizeX = matrix[0].length;
        int sizeZ = matrix[0][0].length;

        if (startY < world.getMinHeight() || startY + sizeY > world.getMaxHeight()) {
            player.sendMessage(ChatColor.RED + "Здание выходит за допустимую высоту мира.");
            return false;
        }

        for (int x = 0; x < sizeX; x++) {
            for (int z = 0; z < sizeZ; z++) {
                String townAtLocation = apiTowny.getTownName(new Location(
                        world,
                        startX + x,
                        startY,
                        startZ + z
                ));

                if (!playerTownName.equals(townAtLocation)) {
                    player.sendMessage(configManager.getNotTownPos());
                    return false;
                }
            }
        }

        for (int y = 0; y < sizeY; y++) {
            for (int x = 0; x < sizeX; x++) {
                for (int z = 0; z < sizeZ; z++) {
                    Block block = world.getBlockAt(startX + x, startY + y, startZ + z);

                    if (!block.getType().isAir()) {
                        player.sendMessage(ChatColor.RED
                                + "Нельзя построить: место занято блоком "
                                + block.getType().name() + ".");
                        return false;
                    }
                }
            }
        }

        int supportY = startY - 1;

        if (supportY < world.getMinHeight()) {
            player.sendMessage(ChatColor.RED + "Под зданием нет места для фундамента.");
            return false;
        }

        for (int x = 0; x < sizeX; x++) {
            for (int z = 0; z < sizeZ; z++) {
                Block support = world.getBlockAt(startX + x, supportY, startZ + z);

                if (!support.getType().isSolid()) {
                    player.sendMessage(ChatColor.RED + "Под зданием нет твёрдой опоры.");
                    return false;
                }
            }
        }

        List<List<Component>> demandResult = checkDemand(player, false, 0, true, buildName);

        if (!demandResult.get(0).isEmpty()) {
            sendMissingDemandMessage(player, demandResult);
            return false;
        }

        BlockData[][][] oldBlocks = new BlockData[sizeY][sizeX][sizeZ];

        try {
            for (int y = 0; y < sizeY; y++) {
                for (int x = 0; x < sizeX; x++) {
                    for (int z = 0; z < sizeZ; z++) {
                        Block block = world.getBlockAt(startX + x, startY + y - 1, startZ + z);
                        oldBlocks[y][x][z] = block.getBlockData().clone();
                        block.setBlockData(matrix[y][x][z].clone(), false);
                    }
                }
            }
        } catch (RuntimeException exception) {
            restoreBlocks(world, startX, startY, startZ, oldBlocks);
            plugin.getLogger().log(Level.SEVERE, "Не удалось поставить здание " + buildName, exception);
            player.sendMessage(ChatColor.RED + "Во время постройки произошла ошибка.");
            return false;
        }

        if (!writingOffDemand(player, false, 0, true, buildName)) {
            restoreBlocks(world, startX, startY, startZ, oldBlocks);
            player.sendMessage(ChatColor.RED + "Не удалось списать ресурсы. Постройка отменена.");
            return false;
        }

        townsConfig.setHasBuild(playerTownName, buildName);
        return true;
    }

    public boolean addBuild(Location first, Location second, Player player, String buildName) {
        if (first == null || second == null || first.getWorld() == null || second.getWorld() == null) {
            player.sendMessage(ChatColor.RED + "Не удалось определить область.");
            return false;
        }

        if (!first.getWorld().equals(second.getWorld())) {
            player.sendMessage(ChatColor.RED + "Обе точки должны быть в одном мире.");
            return false;
        }

        if (buildName == null || !buildName.matches("[a-zA-Z0-9_-]{3,40}")) {
            player.sendMessage(ChatColor.RED + "Имя шаблона: только буквы, цифры, _ и -.");
            return false;
        }

        int minX = Math.min(first.getBlockX(), second.getBlockX());
        int minY = Math.min(first.getBlockY(), second.getBlockY());
        int minZ = Math.min(first.getBlockZ(), second.getBlockZ());

        int maxX = Math.max(first.getBlockX(), second.getBlockX());
        int maxY = Math.max(first.getBlockY(), second.getBlockY());
        int maxZ = Math.max(first.getBlockZ(), second.getBlockZ());

        int sizeX = maxX - minX + 1;
        int sizeY = maxY - minY + 1;
        int sizeZ = maxZ - minZ + 1;

        long volume = (long) sizeX * sizeY * sizeZ;

        if (volume > MAX_TEMPLATE_VOLUME) {
            player.sendMessage(ChatColor.RED + "Шаблон слишком большой: " + volume
                    + " блоков. Лимит: " + MAX_TEMPLATE_VOLUME + ".");
            return false;
        }

        BlockData[][][] matrix = new BlockData[sizeY][sizeX][sizeZ];
        World world = first.getWorld();

        for (int y = 0; y < sizeY; y++) {
            for (int x = 0; x < sizeX; x++) {
                for (int z = 0; z < sizeZ; z++) {
                    matrix[y][x][z] = world.getBlockData(minX + x, minY + y, minZ + z).clone();
                }
            }
        }

        return buildsConfig.createBuildToConfig(buildName, matrix, world);
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

        clearBorder(townName);

        Set<WorldCoord> townChunks = new HashSet<>();

        for (TownBlock block : townBlocks) {
            townChunks.add(block.getWorldCoord());
        }

        Map<Location, BlockData> oldBlocks = new LinkedHashMap<>();

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

            if (!townChunks.contains(new WorldCoord(worldName, chunkX, chunkZ - 1))) {
                for (int x = minX; x <= maxX; x++) {
                    placeBorderOnSurface(world, x, minZ, oldBlocks);
                }
            }

            if (!townChunks.contains(new WorldCoord(worldName, chunkX, chunkZ + 1))) {
                for (int x = minX; x <= maxX; x++) {
                    placeBorderOnSurface(world, x, maxZ, oldBlocks);
                }
            }

            if (!townChunks.contains(new WorldCoord(worldName, chunkX - 1, chunkZ))) {
                for (int z = minZ; z <= maxZ; z++) {
                    placeBorderOnSurface(world, minX, z, oldBlocks);
                }
            }

            if (!townChunks.contains(new WorldCoord(worldName, chunkX + 1, chunkZ))) {
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
        Block ground = world.getHighestBlockAt(x, z, HeightMap.MOTION_BLOCKING_NO_LEAVES);
        int y = ground.getY() + 1;

        if (y >= world.getMaxHeight()) {
            return;
        }

        Location location = new Location(world, x, y, z);
        Block borderBlock = location.getBlock();

        oldBlocks.putIfAbsent(location, borderBlock.getBlockData().clone());
        borderBlock.setType(Material.YELLOW_CONCRETE, false);
    }

    public void clearBorder(String townName) {
        Map<Location, BlockData> oldBlocks = townsConfig.loadOldBorder(townName);

        for (Map.Entry<Location, BlockData> entry : oldBlocks.entrySet()) {
            Location location = entry.getKey();
            BlockData oldData = entry.getValue();

            if (location == null || location.getWorld() == null || oldData == null) {
                continue;
            }

            location.getBlock().setBlockData(oldData, false);
        }

        townsConfig.clearOldBorder(townName);
    }

    private boolean isValidMatrix(BlockData[][][] matrix) {
        if (matrix == null || matrix.length == 0 || matrix[0] == null
                || matrix[0].length == 0 || matrix[0][0] == null
                || matrix[0][0].length == 0) {
            return false;
        }

        int sizeX = matrix[0].length;
        int sizeZ = matrix[0][0].length;

        for (BlockData[][] layer : matrix) {
            if (layer == null || layer.length != sizeX) {
                return false;
            }

            for (BlockData[] row : layer) {
                if (row == null || row.length != sizeZ) {
                    return false;
                }

                for (BlockData blockData : row) {
                    if (blockData == null) {
                        return false;
                    }
                }
            }
        }

        return true;
    }

    private void restoreBlocks(
            World world,
            int startX,
            int startY,
            int startZ,
            BlockData[][][] oldBlocks
    ) {
        if (oldBlocks == null) {
            return;
        }

        for (int y = 0; y < oldBlocks.length; y++) {
            for (int x = 0; x < oldBlocks[y].length; x++) {
                for (int z = 0; z < oldBlocks[y][x].length; z++) {
                    BlockData oldData = oldBlocks[y][x][z];

                    if (oldData != null) {
                        world.getBlockAt(startX + x, startY + y, startZ + z)
                                .setBlockData(oldData, false);
                    }
                }
            }
        }
    }
}
