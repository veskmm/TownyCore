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
import java.util.logging.Level;

public class BuildMenu implements Listener {

    private static final int GUI_ROWS = 6;

    private static final int BUILDS_PANE_X = 2;
    private static final int BUILDS_PANE_Y = 2;
    private static final int BUILDS_PANE_WIDTH = 5;
    private static final int BUILDS_PANE_HEIGHT = 2;

    private static final int MAX_BUILDS_IN_MENU =
            BUILDS_PANE_WIDTH * BUILDS_PANE_HEIGHT;

    /*
     * Защита от слишком огромных схем, которые могут вызвать лаг
     * при проверке каждого блока.
     */
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

    private boolean isStartBuild = false;
    private String buildName;

    /*
     * oldLocation — начало здания, уже на один блок выше фундамента.
     */
    private Location oldLocation;

    /*
     * Исходные BlockData блока под красной разметкой.
     */
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

    private void guiSetup() {
        /*
         * Фон добавляется первым.
         * Предметы зданий добавятся поверх него.
         */
        StaticPane background = new StaticPane(0, 0, 9, GUI_ROWS);

        ItemStack filler = new ItemStack(Material.BLACK_STAINED_GLASS_PANE);
        ItemMeta fillerMeta = filler.getItemMeta();

        if (fillerMeta != null) {
            fillerMeta.setDisplayName(" ");
            filler.setItemMeta(fillerMeta);
        }

        for (int y = 0; y < GUI_ROWS; y++) {
            for (int x = 0; x < 9; x++) {
                background.addItem(
                        new GuiItem(filler, event -> event.setCancelled(true)),
                        x,
                        y
                );
            }
        }

        StaticPane buildsPane = new StaticPane(
                BUILDS_PANE_X,
                BUILDS_PANE_Y,
                BUILDS_PANE_WIDTH,
                BUILDS_PANE_HEIGHT
        );

        List<String> builds = buildsConfig.getBuilds();

        if (builds == null || builds.isEmpty()) {
            gui.addPane(buildsPane);
            return;
        }

        String townName = apiTowny.getTownName(player);
        int buildsToShow = Math.min(builds.size(), MAX_BUILDS_IN_MENU);

        if (builds.size() > MAX_BUILDS_IN_MENU) {
            plugin.getLogger().warning(
                    "В меню зданий больше " + MAX_BUILDS_IN_MENU
                            + " объектов. Лишние здания не будут показаны."
            );
        }

        for (int index = 0; index < buildsToShow; index++) {
            String buildId = builds.get(index);

            Material iconMaterial = buildsConfig.getBuildMaterial(buildId);

            if (iconMaterial == null || iconMaterial == Material.AIR) {
                iconMaterial = Material.BARRIER;
            }

            ItemStack buildItem = new ItemStack(iconMaterial);
            ItemMeta meta = buildItem.getItemMeta();

            if (meta == null) {
                continue;
            }

            String displayName = buildsConfig.getBuildName(buildId);

            if (displayName == null || displayName.trim().isEmpty()) {
                displayName = buildId;
            }

            meta.setDisplayName(
                    ChatColor.translateAlternateColorCodes('&', displayName)
            );

            List<String> lore = new ArrayList<>();
            List<String> configuredLore = buildsConfig.getBuildLore(buildId);

            if (configuredLore != null) {
                for (String line : configuredLore) {
                    if (line != null) {
                        lore.add(
                                ChatColor.translateAlternateColorCodes('&', line)
                        );
                    }
                }
            }

            lore.add(" ");

            boolean townAlreadyHasBuild =
                    townName != null
                            && !townName.isEmpty()
                            && townsConfig.isHasBuild(townName, buildId);

            if (townAlreadyHasBuild) {
                lore.add(
                        ChatColor.translateAlternateColorCodes(
                                '&',
                                "&rЛКМ - Открыть меню здания"
                        )
                );
            } else {
                lore.add(
                        ChatColor.translateAlternateColorCodes(
                                '&',
                                "&rНеобходимые ресурсы:"
                        )
                );

                Map<Material, Integer> requiredResources =
                        buildsConfig.getDemandBuild(buildId);

                if (requiredResources != null) {
                    for (Map.Entry<Material, Integer> entry
                            : requiredResources.entrySet()) {

                        String message = "&r"
                                + entry.getKey().name()
                                + " "
                                + entry.getValue()
                                + " штук.";

                        lore.add(
                                ChatColor.translateAlternateColorCodes(
                                        '&',
                                        message
                                )
                        );
                    }
                }
            }

            meta.setLore(lore);
            buildItem.setItemMeta(meta);

            final String selectedBuildId = buildId;

            int slotX = index % BUILDS_PANE_WIDTH;
            int slotY = index / BUILDS_PANE_WIDTH;

            buildsPane.addItem(
                    new GuiItem(buildItem, event -> {
                        event.setCancelled(true);

                        String currentTownName =
                                apiTowny.getTownName(player);

                        boolean hasBuild =
                                currentTownName != null
                                        && !currentTownName.isEmpty()
                                        && townsConfig.isHasBuild(
                                        currentTownName,
                                        selectedBuildId
                                );

                        if (hasBuild) {
                            openDeluxeMenu(
                                    player,
                                    buildsConfig.getMenuName(selectedBuildId)
                            );
                            return;
                        }

                        player.closeInventory();
                        startBuild(player, selectedBuildId);
                    }),
                    slotX,
                    slotY
            );
        }

        gui.addPane(buildsPane);
        gui.addPane(background);
    }

    public void openDeluxeMenu(Player player, String menuName) {
        if (menuName == null || menuName.trim().isEmpty()) {
            player.sendMessage(
                    ChatColor.RED + "Для этого здания не настроено меню."
            );
            return;
        }

        String command = "dm open "
                + menuName
                + " "
                + player.getName();

        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command);
    }

    public void openMenu() {
        if (!player.isOnline()) {
            return;
        }

        gui.show(player);
    }

    public void startBuild(Player ignoredPlayer, String nameBuild) {
        if (nameBuild == null || nameBuild.trim().isEmpty()) {
            player.sendMessage(
                    ChatColor.RED + "Не удалось определить тип здания."
            );
            return;
        }

        /*
         * Если была старая разметка, сначала восстанавливаем мир.
         */
        restoreOldMarking();

        this.buildName = nameBuild;
        this.isStartBuild = true;

        player.sendMessage(
                ChatColor.YELLOW
                        + "Нажми ЛКМ по блоку-фундаменту, "
                        + "чтобы выбрать место строительства."
        );
    }

    private void makeMarking(Location foundationLocation) {
        if (!isStartBuild || buildName == null) {
            return;
        }

        /*
         * Старую разметку нужно убрать до новой проверки.
         * Иначе красный бетон сам будет мешать проверке.
         */
        restoreOldMarking();

        BlockData[][][] matrixBlocks =
                buildsConfig.loadMatrix(buildName + ".blocks");

        PlacementValidation validation =
                validatePlacement(foundationLocation, matrixBlocks);

        if (!validation.isValid()) {
            showValidationProblems(validation);

            /*
             * Важно:
             * не вызываем cancelBuild().
             *
             * Игрок может исправить землю или убрать блоки,
             * после чего снова выбрать фундамент.
             */
            return;
        }

        if (!checkResources(buildName)) {
            return;
        }

        Block foundationBlock = foundationLocation.getBlock();
        World world = foundationBlock.getWorld();

        int startX = foundationBlock.getX();
        int startY = foundationBlock.getY() + 1;
        int startZ = foundationBlock.getZ();

        createPreview(world, startX, startY, startZ, matrixBlocks);

        player.sendMessage(
                ChatColor.GREEN
                        + "Место проверено. Разметка здания установлена."
        );
    }

    private PlacementValidation validatePlacement(
            Location foundationLocation,
            BlockData[][][] matrixBlocks
    ) {
        if (foundationLocation == null
                || foundationLocation.getWorld() == null) {

            return PlacementValidation.failure(
                    "Не удалось определить мир строительства."
            );
        }

        if (!isCorrectMatrix(matrixBlocks)) {
            return PlacementValidation.failure(
                    "Схема здания пуста или имеет неверную структуру."
            );
        }

        String playerTownName = apiTowny.getTownName(player);

        if (playerTownName == null || playerTownName.isEmpty()) {
            return PlacementValidation.failure(
                    configManager.getNotTown()
            );
        }

        Block foundationBlock = foundationLocation.getBlock();
        World world = foundationBlock.getWorld();

        int startX = foundationBlock.getX();
        int startY = foundationBlock.getY() + 1;
        int startZ = foundationBlock.getZ();

        int sizeY = matrixBlocks.length;
        int sizeX = matrixBlocks[0].length;
        int sizeZ = matrixBlocks[0][0].length;

        long volume = (long) sizeX * sizeY * sizeZ;

        if (volume > MAX_VALIDATION_VOLUME) {
            return PlacementValidation.failure(
                    "Схема слишком большая для проверки: "
                            + volume
                            + " блоков. Лимит: "
                            + MAX_VALIDATION_VOLUME
                            + "."
            );
        }

        int minWorldY = world.getMinHeight();
        int maxWorldY = world.getMaxHeight();

        if (startY < minWorldY || startY + sizeY > maxWorldY) {
            return PlacementValidation.failure(
                    "Нельзя построить: здание выходит "
                            + "за допустимую высоту мира."
            );
        }

        /*
         * Towny-проверка по основанию здания.
         *
         * Towny-клеймы не зависят от высоты,
         * поэтому нет смысла повторять одинаковую проверку
         * для каждого Y-слоя схемы.
         */
        for (int x = 0; x < sizeX; x++) {
            for (int z = 0; z < sizeZ; z++) {
                Location checkLocation = new Location(
                        world,
                        startX + x,
                        startY,
                        startZ + z
                );

                String townNameAtBlock =
                        apiTowny.getTownName(checkLocation);

                if (townNameAtBlock == null
                        || !townNameAtBlock.equals(playerTownName)) {

                    return PlacementValidation.failure(
                            configManager.getNotTownPos()
                    );
                }
            }
        }

        List<Location> blockingBlocks = new ArrayList<>();
        List<Location> missingSupports = new ArrayList<>();

        /*
         * Проверяем весь объём будущей постройки.
         *
         * Тут действует строгий режим:
         * любое дерево, трава, вода, стена или потолок
         * внутри габаритов здания запрещает постройку.
         */
        for (int y = 0; y < sizeY; y++) {
            for (int x = 0; x < sizeX; x++) {
                for (int z = 0; z < sizeZ; z++) {
                    Block blockAtPlace = world.getBlockAt(
                            startX + x,
                            startY + y,
                            startZ + z
                    );

                    if (!blockAtPlace.getType().isAir()) {
                        if (blockingBlocks.size()
                                < MAX_BLOCKING_PARTICLES) {

                            blockingBlocks.add(
                                    blockAtPlace.getLocation()
                            );
                        }
                    }
                }
            }
        }

        int supportY = startY - 1;

        if (supportY < minWorldY) {
            return PlacementValidation.failure(
                    "Нельзя построить: под зданием нет места для фундамента."
            );
        }

        /*
         * Проверяем опору под всем основанием.
         */
        for (int x = 0; x < sizeX; x++) {
            for (int z = 0; z < sizeZ; z++) {
                Block supportBlock = world.getBlockAt(
                        startX + x,
                        supportY,
                        startZ + z
                );

                /*
                 * Block#isSolid() лучше, чем Material#isSolid():
                 * проверяется сам блок, а не только его материал.
                 */
                if (!supportBlock.isSolid()) {
                    if (missingSupports.size()
                            < MAX_SUPPORT_PARTICLES) {

                        missingSupports.add(
                                supportBlock.getLocation()
                        );
                    }
                }
            }
        }

        if (!blockingBlocks.isEmpty() || !missingSupports.isEmpty()) {
            return PlacementValidation.withProblems(
                    blockingBlocks,
                    missingSupports
            );
        }

        return PlacementValidation.success();
    }

    private boolean isCorrectMatrix(BlockData[][][] matrixBlocks) {
        if (matrixBlocks == null
                || matrixBlocks.length == 0
                || matrixBlocks[0] == null
                || matrixBlocks[0].length == 0
                || matrixBlocks[0][0] == null
                || matrixBlocks[0][0].length == 0) {

            return false;
        }

        int expectedSizeX = matrixBlocks[0].length;
        int expectedSizeZ = matrixBlocks[0][0].length;

        for (BlockData[][] layer : matrixBlocks) {
            if (layer == null || layer.length != expectedSizeX) {
                return false;
            }

            for (BlockData[] row : layer) {
                if (row == null || row.length != expectedSizeZ) {
                    return false;
                }
            }
        }

        return true;
    }

    private void showValidationProblems(PlacementValidation validation) {
        if (validation.getErrorMessage() != null) {
            player.sendMessage(
                    ChatColor.RED + validation.getErrorMessage()
            );
            return;
        }

        List<Location> blockingBlocks =
                validation.getBlockingBlocks();

        List<Location> missingSupports =
                validation.getMissingSupports();

        if (!blockingBlocks.isEmpty()) {
            showBlockingBlockParticles(player, blockingBlocks);
        }

        if (!missingSupports.isEmpty()) {
            showMissingSupportParticles(player, missingSupports);
        }

        if (!blockingBlocks.isEmpty() && !missingSupports.isEmpty()) {
            player.sendMessage(
                    ChatColor.RED
                            + "Нельзя построить: красные частицы показывают "
                            + "блоки, которые нужно убрать, "
                            + "а оранжевые — места без фундамента."
            );
            return;
        }

        if (!blockingBlocks.isEmpty()) {
            Location firstBlock = blockingBlocks.get(0);

            player.sendMessage(
                    ChatColor.RED
                            + "Нельзя построить: красные частицы показывают "
                            + "блоки, которые мешают строительству. "
                            + "Первый блок: "
                            + firstBlock.getBlock().getType().name()
                            + " на X=" + firstBlock.getBlockX()
                            + ", Y=" + firstBlock.getBlockY()
                            + ", Z=" + firstBlock.getBlockZ()
                            + "."
            );

            return;
        }

        Location firstMissingSupport = missingSupports.get(0);

        player.sendMessage(
                ChatColor.RED
                        + "Нельзя построить: оранжевые частицы показывают "
                        + "места, где нет твёрдой опоры. "
                        + "Первая точка: X="
                        + firstMissingSupport.getBlockX()
                        + ", Y=" + firstMissingSupport.getBlockY()
                        + ", Z=" + firstMissingSupport.getBlockZ()
                        + "."
        );
    }

    private boolean checkResources(String requestedBuildName) {
        List<List<Component>> result =
                manager.checkDemand(
                        player,
                        false,
                        10,
                        true,
                        requestedBuildName
                );

        if (result == null
                || result.size() < 2
                || result.get(0) == null
                || result.get(1) == null) {

            plugin.getLogger().warning(
                    "Manager#checkDemand вернул неверный результат "
                            + "для здания " + requestedBuildName
            );

            player.sendMessage(
                    ChatColor.RED
                            + "Не удалось проверить необходимые ресурсы."
            );

            return false;
        }

        List<Component> missingComponents = result.get(0);
        List<Component> missingAmounts = result.get(1);

        if (missingComponents.isEmpty()) {
            return true;
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
                    .append(
                            Component.text(" штук")
                                    .color(NamedTextColor.GOLD)
                    );
        }

        String rawMessage = configManager.getNotEnoughResources();

        if (rawMessage == null) {
            rawMessage = "&cНедостаточно ресурсов.";
        }

        player.sendMessage(
                ChatColor.translateAlternateColorCodes(
                        '&',
                        rawMessage.replace("{missingResources}", "")
                )
        );

        player.sendMessage(message);

        return false;
    }

    private void createPreview(
            World world,
            int startX,
            int startY,
            int startZ,
            BlockData[][][] matrixBlocks
    ) {
        BlockData[][] firstLayer = matrixBlocks[0];

        oldLayer = new BlockData[
                firstLayer.length
                ][
                firstLayer[0].length
                ];

        oldX = startX;
        oldY = startY;
        oldZ = startZ;
        oldWorld = world;

        for (int x = 0; x < firstLayer.length; x++) {
            for (int z = 0; z < firstLayer[x].length; z++) {
                Block block = world.getBlockAt(
                        startX + x,
                        startY,
                        startZ + z
                );

                oldLayer[x][z] = block.getBlockData().clone();

                /*
                 * Здесь preview создаётся только после строгой проверки,
                 * поэтому на месте должны быть воздушные блоки.
                 */
                block.setType(Material.RED_CONCRETE, false);
            }
        }

        oldLocation = new Location(
                world,
                startX,
                startY,
                startZ
        );
    }

    private void restoreOldMarking() {
        if (oldLayer == null || oldWorld == null) {
            clearPreviewData();
            return;
        }

        for (int x = 0; x < oldLayer.length; x++) {
            for (int z = 0; z < oldLayer[x].length; z++) {
                BlockData oldData = oldLayer[x][z];

                if (oldData == null) {
                    continue;
                }

                oldWorld.getBlockAt(
                                oldX + x,
                                oldY,
                                oldZ + z
                        )
                        .setBlockData(oldData, false);
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

    private void showMissingSupportParticles(
            Player player,
            List<Location> missingSupports
    ) {
        Particle.DustOptions orangeDust = new Particle.DustOptions(
                Color.fromRGB(255, 150, 0),
                1.8F
        );

        showProblemParticles(
                player,
                missingSupports,
                orangeDust
        );
    }

    private void showBlockingBlockParticles(
            Player player,
            List<Location> blockingBlocks
    ) {
        Particle.DustOptions redDust = new Particle.DustOptions(
                Color.fromRGB(255, 35, 35),
                1.8F
        );

        showProblemParticles(
                player,
                blockingBlocks,
                redDust
        );
    }

    private void showProblemParticles(
            Player player,
            List<Location> problemBlocks,
            Particle.DustOptions dustOptions
    ) {
        if (!player.isOnline() || problemBlocks.isEmpty()) {
            return;
        }

        List<Location> pointsToShow = new ArrayList<>();

        for (Location location : problemBlocks) {
            if (location != null) {
                pointsToShow.add(location.clone());
            }
        }

        new BukkitRunnable() {
            private int shownTimes = 0;

            @Override
            public void run() {
                if (!player.isOnline() || shownTimes >= 12) {
                    cancel();
                    return;
                }

                for (Location location : pointsToShow) {
                    World world = location.getWorld();

                    if (world == null || !world.equals(player.getWorld())) {
                        continue;
                    }

                    double x = location.getBlockX();
                    double y = location.getBlockY();
                    double z = location.getBlockZ();

                    player.spawnParticle(
                            Particle.REDSTONE,
                            x + 0.5D,
                            y + 0.5D,
                            z + 0.5D,
                            20,
                            0.48D,
                            0.48D,
                            0.48D,
                            0.0D,
                            dustOptions
                    );

                    player.spawnParticle(
                            Particle.REDSTONE,
                            x + 0.5D,
                            y + 1.02D,
                            z + 0.5D,
                            8,
                            0.42D,
                            0.02D,
                            0.42D,
                            0.0D,
                            dustOptions
                    );
                }

                shownTimes++;
            }
        }.runTaskTimer(plugin, 0L, 4L);
    }

    @EventHandler(ignoreCancelled = true)
    public void playerClick(PlayerInteractEvent event) {
        /*
         * Критично: этот BuildMenu принадлежит только одному игроку.
         */
        if (!event.getPlayer().getUniqueId().equals(playerId)) {
            return;
        }

        if (!isStartBuild) {
            return;
        }

        /*
         * Иначе событие может сработать повторно от второй руки.
         */
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }

        if (event.getAction() != Action.LEFT_CLICK_BLOCK) {
            return;
        }

        Block clickedBlock = event.getClickedBlock();

        if (clickedBlock == null) {
            return;
        }

        event.setCancelled(true);

        makeMarking(clickedBlock.getLocation());
    }

    @EventHandler
    public void playerLeave(PlayerQuitEvent event) {
        if (!event.getPlayer().getUniqueId().equals(playerId)) {
            return;
        }

        endSession(true);
    }

    public void acceptBuild() {
        if (!isStartBuild || buildName == null) {
            player.sendMessage(
                    ChatColor.RED
                            + "Сначала выбери место для строительства."
            );
            return;
        }

        if (oldLocation == null) {
            player.sendMessage(
                    ChatColor.RED
                            + "Сначала выбери корректное место для здания."
            );
            return;
        }

        /*
         * Сохраняем данные до восстановления preview.
         */
        Location buildOrigin = oldLocation.clone();
        String selectedBuildName = buildName;

        BlockData[][][] matrixBlocks =
                buildsConfig.loadMatrix(selectedBuildName + ".blocks");

        /*
         * Перед реальной постройкой обязательно повторяем проверку.
         *
         * За время между preview и подтверждением
         * мир мог измениться.
         */
        restoreOldMarking();

        Location foundationLocation = buildOrigin.clone().subtract(0, 1, 0);

        PlacementValidation validation =
                validatePlacement(foundationLocation, matrixBlocks);

        if (!validation.isValid()) {
            showValidationProblems(validation);
            return;
        }

        if (!checkResources(selectedBuildName)) {
            return;
        }

        try {
            manager.makeBuild(
                    buildOrigin,
                    player,
                    selectedBuildName
            );

            player.sendMessage(
                    ChatColor.GREEN + "Строительство началось."
            );

            endSession(false);

        } catch (RuntimeException exception) {
            plugin.getLogger().log(
                    Level.SEVERE,
                    "Ошибка при строительстве здания "
                            + selectedBuildName
                            + " для игрока "
                            + player.getName(),
                    exception
            );

            player.sendMessage(
                    ChatColor.RED
                            + "Во время начала строительства произошла ошибка."
            );
        }
    }

    public void cancelBuild() {
        if (!isStartBuild && oldLayer == null) {
            return;
        }

        player.sendMessage(
                ChatColor.YELLOW + "Строительство отменено."
        );

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

        manager.buildMenus.remove(this);

        /*
         * Оставь эту строку, если экземпляр BuildMenu
         * регистрируется отдельно для каждого игрока/сеанса.
         */
        HandlerList.unregisterAll(this);
    }

    private static final class PlacementValidation {

        private final boolean valid;
        private final String errorMessage;
        private final List<Location> blockingBlocks;
        private final List<Location> missingSupports;

        private PlacementValidation(
                boolean valid,
                String errorMessage,
                List<Location> blockingBlocks,
                List<Location> missingSupports
        ) {
            this.valid = valid;
            this.errorMessage = errorMessage;
            this.blockingBlocks = blockingBlocks;
            this.missingSupports = missingSupports;
        }

        public static PlacementValidation success() {
            return new PlacementValidation(
                    true,
                    null,
                    Collections.emptyList(),
                    Collections.emptyList()
            );
        }

        public static PlacementValidation failure(String message) {
            return new PlacementValidation(
                    false,
                    message,
                    Collections.emptyList(),
                    Collections.emptyList()
            );
        }

        public static PlacementValidation withProblems(
                List<Location> blockingBlocks,
                List<Location> missingSupports
        ) {
            return new PlacementValidation(
                    false,
                    null,
                    blockingBlocks,
                    missingSupports
            );
        }

        public boolean isValid() {
            return valid;
        }

        public String getErrorMessage() {
            return errorMessage;
        }

        public List<Location> getBlockingBlocks() {
            return blockingBlocks;
        }

        public List<Location> getMissingSupports() {
            return missingSupports;
        }
    }
}