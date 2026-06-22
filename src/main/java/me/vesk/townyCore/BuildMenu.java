package me.vesk.townyCore;

import com.github.stefvanschie.inventoryframework.gui.GuiItem;
import com.github.stefvanschie.inventoryframework.gui.type.ChestGui;
import com.github.stefvanschie.inventoryframework.pane.StaticPane;
import com.palmergames.bukkit.towny.TownyAPI;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class BuildMenu implements Listener {

    private static final int GUI_ROWS = 6;
    private static final int BUILDS_PANE_X = 2;
    private static final int BUILDS_PANE_Y = 2;
    private static final int BUILDS_PANE_WIDTH = 5;
    private static final int BUILDS_PANE_HEIGHT = 2;
    private static final int MAX_BUILDS_IN_MENU = BUILDS_PANE_WIDTH * BUILDS_PANE_HEIGHT;
    private static final long MAX_VALIDATION_VOLUME = 100_000L;
    private static final int MAX_BLOCKING_PARTICLES = 150;
    private static final int MAX_SUPPORT_PARTICLES = 120;

    private final ChestGui gui;
    private final Player player;
    private final UUID playerId;
    private final BuildsConfig buildsConfig;
    private final Manager manager;
    private final TownsConfig townsConfig;
    private final ConfigManager configManager;
    private final JavaPlugin plugin;
    private final TownyAPI apiTowny;

    private boolean isStartBuild;
    private String buildName;
    private Location oldLocation;
    private BlockData[][] oldLayer;
    private World oldWorld;
    private int oldX;
    private int oldY;
    private int oldZ;

    public BuildMenu(
            Player player,
            BuildsConfig buildsConfig,
            Manager manager,
            TownsConfig townsConfig,
            ConfigManager configManager,
            JavaPlugin plugin
    ) {
        this.player = player;
        this.playerId = player.getUniqueId();
        this.buildsConfig = buildsConfig;
        this.manager = manager;
        this.townsConfig = townsConfig;
        this.configManager = configManager;
        this.plugin = plugin;
        this.apiTowny = TownyAPI.getInstance();
        this.gui = new ChestGui(GUI_ROWS, "Городские здания");
        guiSetup();
    }

    public Player getPlayer() {
        return player;
    }

    public UUID getPlayerId() {
        return playerId;
    }

    private void guiSetup() {
        StaticPane background = new StaticPane(0, 0, 9, GUI_ROWS);
        ItemStack filler = new ItemStack(Material.BLACK_STAINED_GLASS_PANE);
        ItemMeta fillerMeta = filler.getItemMeta();

        if (fillerMeta != null) {
            fillerMeta.setDisplayName(" ");
            filler.setItemMeta(fillerMeta);
        }

        for (int y = 0; y < GUI_ROWS; y++) {
            for (int x = 0; x < 9; x++) {
                background.addItem(new GuiItem(filler, event -> event.setCancelled(true)), x, y);
            }
        }

        StaticPane buildsPane = new StaticPane(
                BUILDS_PANE_X,
                BUILDS_PANE_Y,
                BUILDS_PANE_WIDTH,
                BUILDS_PANE_HEIGHT
        );

        List<String> builds = buildsConfig.getBuilds();
        String townName = apiTowny.getTownName(player);
        int buildsToShow = Math.min(builds.size(), MAX_BUILDS_IN_MENU);

        if (builds.size() > MAX_BUILDS_IN_MENU) {
            plugin.getLogger().warning("В меню больше " + MAX_BUILDS_IN_MENU
                    + " зданий. Лишние не будут показаны.");
        }

        for (int index = 0; index < buildsToShow; index++) {
            String buildId = builds.get(index);
            Material iconMaterial = buildsConfig.getBuildMaterial(buildId);
            ItemStack buildItem = new ItemStack(iconMaterial == null ? Material.BARRIER : iconMaterial);
            ItemMeta meta = buildItem.getItemMeta();

            if (meta == null) {
                continue;
            }

            meta.setDisplayName(ChatColor.translateAlternateColorCodes(
                    '&',
                    buildsConfig.getBuildName(buildId)
            ));

            List<String> lore = new ArrayList<>();

            for (String line : buildsConfig.getBuildLore(buildId)) {
                lore.add(ChatColor.translateAlternateColorCodes('&', line));
            }

            lore.add(" ");

            boolean townAlreadyHasBuild = townName != null && !townName.isBlank()
                    && townsConfig.isHasBuild(townName, buildId);

            if (townAlreadyHasBuild) {
                lore.add(ChatColor.translateAlternateColorCodes('&', "&rЛКМ - Открыть меню здания"));
            } else {
                lore.add(ChatColor.translateAlternateColorCodes('&', "&rНеобходимые ресурсы:"));

                for (Map.Entry<Material, Integer> entry : buildsConfig.getDemandBuild(buildId).entrySet()) {
                    lore.add(ChatColor.translateAlternateColorCodes(
                            '&',
                            "&r" + entry.getKey().name() + " " + entry.getValue() + " штук."
                    ));
                }
            }

            meta.setLore(lore);
            buildItem.setItemMeta(meta);

            final String selectedBuildId = buildId;
            int slotX = index % BUILDS_PANE_WIDTH;
            int slotY = index / BUILDS_PANE_WIDTH;

            buildsPane.addItem(new GuiItem(buildItem, event -> {
                event.setCancelled(true);

                String currentTownName = apiTowny.getTownName(player);
                boolean hasBuild = currentTownName != null && !currentTownName.isBlank()
                        && townsConfig.isHasBuild(currentTownName, selectedBuildId);

                if (hasBuild) {
                    openDeluxeMenu(player, buildsConfig.getMenuName(selectedBuildId));
                    return;
                }

                player.closeInventory();
                startBuild(player, selectedBuildId);
            }), slotX, slotY);
        }

        /* Items first, Background after it. */
        gui.addPane(buildsPane);
        gui.addPane(background);
    }

    public void openDeluxeMenu(Player player, String menuName) {
        if (menuName == null || menuName.isBlank()) {
            player.sendMessage(ChatColor.RED + "Для этого здания не настроено меню.");
            return;
        }

        Bukkit.dispatchCommand(
                Bukkit.getConsoleSender(),
                "dm open " + menuName + " " + player.getName()
        );
    }

    public void openMenu() {
        if (player.isOnline()) {
            gui.show(player);
        }
    }

    public void startBuild(Player ignoredPlayer, String nameBuild) {
        if (nameBuild == null || nameBuild.isBlank()) {
            player.sendMessage(ChatColor.RED + "Не удалось определить тип здания.");
            return;
        }

        restoreOldMarking();
        buildName = nameBuild;
        isStartBuild = true;
        player.sendMessage(ChatColor.YELLOW
                + "Нажми ЛКМ по блоку-фундаменту, чтобы выбрать место строительства.");
    }

    private void makeMarking(Location foundationLocation) {
        if (!isStartBuild || buildName == null) {
            return;
        }

        restoreOldMarking();
        BlockData[][][] matrix = buildsConfig.loadMatrix(buildName + ".blocks");
        PlacementValidation validation = validatePlacement(foundationLocation, matrix);

        if (!validation.valid()) {
            showValidationProblems(validation);
            return;
        }

        List<List<Component>> demand = manager.checkDemand(player, false, 0, true, buildName);

        if (!demand.get(0).isEmpty()) {
            manager.sendMissingDemandMessage(player, demand);
            return;
        }

        Block foundation = foundationLocation.getBlock();
        createPreview(
                foundation.getWorld(),
                foundation.getX(),
                foundation.getY() + 1,
                foundation.getZ(),
                matrix
        );

        player.sendMessage(ChatColor.GREEN + "Место проверено. Разметка здания установлена.");
    }

    private PlacementValidation validatePlacement(Location foundationLocation, BlockData[][][] matrix) {
        if (foundationLocation == null || foundationLocation.getWorld() == null) {
            return PlacementValidation.failure("Не удалось определить мир строительства.");
        }

        if (!isValidMatrix(matrix)) {
            return PlacementValidation.failure("Схема здания пуста или повреждена.");
        }

        String playerTownName = apiTowny.getTownName(player);

        if (playerTownName == null || playerTownName.isBlank()) {
            return PlacementValidation.failure(configManager.getNotTown());
        }

        Block foundation = foundationLocation.getBlock();
        World world = foundation.getWorld();
        int startX = foundation.getX();
        int startY = foundation.getY() + 1;
        int startZ = foundation.getZ();

        int sizeY = matrix.length;
        int sizeX = matrix[0].length;
        int sizeZ = matrix[0][0].length;

        if ((long) sizeX * sizeY * sizeZ > MAX_VALIDATION_VOLUME) {
            return PlacementValidation.failure("Схема слишком большая для проверки.");
        }

        if (startY < world.getMinHeight() || startY + sizeY > world.getMaxHeight()) {
            return PlacementValidation.failure("Здание выходит за допустимую высоту мира.");
        }

        for (int x = 0; x < sizeX; x++) {
            for (int z = 0; z < sizeZ; z++) {
                String townAt = apiTowny.getTownName(new Location(world, startX + x, startY, startZ + z));

                if (!playerTownName.equals(townAt)) {
                    return PlacementValidation.failure(configManager.getNotTownPos());
                }
            }
        }

        List<Location> blocking = new ArrayList<>();
        List<Location> missingSupport = new ArrayList<>();

        for (int y = 0; y < sizeY; y++) {
            for (int x = 0; x < sizeX; x++) {
                for (int z = 0; z < sizeZ; z++) {
                    Block block = world.getBlockAt(startX + x, startY + y, startZ + z);

                    if (!block.getType().isAir() && blocking.size() < MAX_BLOCKING_PARTICLES) {
                        blocking.add(block.getLocation());
                    }
                }
            }
        }

        int supportY = startY - 1;

        if (supportY < world.getMinHeight()) {
            return PlacementValidation.failure("Под зданием нет места для фундамента.");
        }

        for (int x = 0; x < sizeX; x++) {
            for (int z = 0; z < sizeZ; z++) {
                Block support = world.getBlockAt(startX + x, supportY, startZ + z);

                if (!support.getType().isSolid() && missingSupport.size() < MAX_SUPPORT_PARTICLES) {
                    missingSupport.add(support.getLocation());
                }
            }
        }

        if (!blocking.isEmpty() || !missingSupport.isEmpty()) {
            return PlacementValidation.problems(blocking, missingSupport);
        }

        return PlacementValidation.success();
    }

    private void createPreview(
            World world,
            int startX,
            int startY,
            int startZ,
            BlockData[][][] matrix
    ) {
        BlockData[][] firstLayer = matrix[0];
        oldLayer = new BlockData[firstLayer.length][firstLayer[0].length];
        oldWorld = world;
        oldX = startX;
        oldY = startY;
        oldZ = startZ;

        for (int x = 0; x < firstLayer.length; x++) {
            for (int z = 0; z < firstLayer[x].length; z++) {
                Block block = world.getBlockAt(startX + x, startY, startZ + z);
                oldLayer[x][z] = block.getBlockData().clone();
                block.setType(Material.RED_CONCRETE, false);
            }
        }

        oldLocation = new Location(world, startX, startY, startZ);
    }

    private void restoreOldMarking() {
        if (oldLayer == null || oldWorld == null) {
            clearPreviewData();
            return;
        }

        for (int x = 0; x < oldLayer.length; x++) {
            for (int z = 0; z < oldLayer[x].length; z++) {
                BlockData oldData = oldLayer[x][z];
                Block current = oldWorld.getBlockAt(oldX + x, oldY, oldZ + z);

                /* Do not overwrite a block placed by someone else during preview. */
                if (oldData != null && current.getType() == Material.RED_CONCRETE) {
                    current.setBlockData(oldData, false);
                }
            }
        }

        clearPreviewData();
    }

    private void clearPreviewData() {
        oldLayer = null;
        oldWorld = null;
        oldLocation = null;
        oldX = 0;
        oldY = 0;
        oldZ = 0;
    }

    private void showValidationProblems(PlacementValidation validation) {
        if (validation.errorMessage() != null) {
            player.sendMessage(ChatColor.RED + validation.errorMessage());
            return;
        }

        if (!validation.blockingBlocks().isEmpty()) {
            showBlockingBlockParticles(player, validation.blockingBlocks());
        }

        if (!validation.missingSupports().isEmpty()) {
            showMissingSupportParticles(player, validation.missingSupports());
        }

        if (!validation.blockingBlocks().isEmpty() && !validation.missingSupports().isEmpty()) {
            player.sendMessage(ChatColor.RED
                    + "Красные частицы показывают блоки, которые нужно убрать, "
                    + "а оранжевые — места без фундамента.");
        } else if (!validation.blockingBlocks().isEmpty()) {
            player.sendMessage(ChatColor.RED + "Красные частицы показывают блоки, которые мешают строительству.");
        } else {
            player.sendMessage(ChatColor.RED + "Оранжевые частицы показывают места без твёрдой опоры.");
        }
    }

    private void showMissingSupportParticles(Player player, List<Location> missingSupports) {
        showProblemParticles(
                player,
                missingSupports,
                new Particle.DustOptions(Color.fromRGB(255, 150, 0), 1.8F)
        );
    }

    private void showBlockingBlockParticles(Player player, List<Location> blockingBlocks) {
        showProblemParticles(
                player,
                blockingBlocks,
                new Particle.DustOptions(Color.fromRGB(255, 35, 35), 1.8F)
        );
    }

    private void showProblemParticles(
            Player viewer,
            List<Location> problemBlocks,
            Particle.DustOptions dustOptions
    ) {
        if (!viewer.isOnline() || problemBlocks.isEmpty()) {
            return;
        }

        List<Location> points = new ArrayList<>();

        for (Location location : problemBlocks) {
            if (location != null) {
                points.add(location.clone());
            }
        }

        new BukkitRunnable() {
            private int shownTimes;

            @Override
            public void run() {
                if (!viewer.isOnline() || shownTimes >= 12) {
                    cancel();
                    return;
                }

                for (Location location : points) {
                    if (location.getWorld() == null || !location.getWorld().equals(viewer.getWorld())) {
                        continue;
                    }

                    double x = location.getBlockX();
                    double y = location.getBlockY();
                    double z = location.getBlockZ();

                    viewer.spawnParticle(Particle.REDSTONE, x + 0.5D, y + 0.5D, z + 0.5D,
                            20, 0.48D, 0.48D, 0.48D, 0.0D, dustOptions);
                    viewer.spawnParticle(Particle.REDSTONE, x + 0.5D, y + 1.02D, z + 0.5D,
                            8, 0.42D, 0.02D, 0.42D, 0.0D, dustOptions);
                }

                shownTimes++;
            }
        }.runTaskTimer(plugin, 0L, 4L);
    }

    @EventHandler(ignoreCancelled = true)
    public void playerClick(PlayerInteractEvent event) {
        if (!event.getPlayer().getUniqueId().equals(playerId)
                || !isStartBuild
                || event.getHand() != EquipmentSlot.HAND
                || event.getAction() != Action.LEFT_CLICK_BLOCK
                || event.getClickedBlock() == null) {
            return;
        }

        event.setCancelled(true);
        makeMarking(event.getClickedBlock().getLocation());
    }

    @EventHandler
    public void playerLeave(PlayerQuitEvent event) {
        if (event.getPlayer().getUniqueId().equals(playerId)) {
            shutdown();
        }
    }

    public void acceptBuild() {
        if (!isStartBuild || buildName == null || oldLocation == null) {
            player.sendMessage(ChatColor.RED + "Сначала выбери корректное место для здания.");
            return;
        }

        Location buildOrigin = oldLocation.clone();
        String selectedBuildName = buildName;
        restoreOldMarking();

        if (!manager.makeBuild(buildOrigin, player, selectedBuildName)) {
            return;
        }

        player.sendMessage(ChatColor.GREEN + "Строительство завершено.");
        endSession(false);
    }

    public void cancelBuild() {
        if (!isStartBuild && oldLayer == null) {
            return;
        }

        player.sendMessage(ChatColor.YELLOW + "Строительство отменено.");
        endSession(true);
    }

    public void shutdown() {
        endSession(true);
    }

    private void endSession(boolean restorePreview) {
        if (restorePreview) {
            restoreOldMarking();
        } else {
            clearPreviewData();
        }

        isStartBuild = false;
        buildName = null;
        manager.removeBuildMenu(playerId, this);
        HandlerList.unregisterAll(this);
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
            }
        }

        return true;
    }

    private record PlacementValidation(
            boolean valid,
            String errorMessage,
            List<Location> blockingBlocks,
            List<Location> missingSupports
    ) {
        private static PlacementValidation success() {
            return new PlacementValidation(true, null, Collections.emptyList(), Collections.emptyList());
        }

        private static PlacementValidation failure(String message) {
            return new PlacementValidation(false, message, Collections.emptyList(), Collections.emptyList());
        }

        private static PlacementValidation problems(
                List<Location> blockingBlocks,
                List<Location> missingSupports
        ) {
            return new PlacementValidation(false, null, blockingBlocks, missingSupports);
        }
    }
}
