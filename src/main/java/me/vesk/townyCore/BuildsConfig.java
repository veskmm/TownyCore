package me.vesk.townyCore;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.data.BlockData;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class BuildsConfig {

    private final JavaPlugin plugin;
    private File configFile;
    private FileConfiguration config;

    public BuildsConfig(JavaPlugin plugin) {
        this.plugin = plugin;
        loadConfig();
    }

    public void loadConfig() {
        configFile = new File(plugin.getDataFolder(), "builds_config.yml");

        if (!plugin.getDataFolder().exists() && !plugin.getDataFolder().mkdirs()) {
            plugin.getLogger().warning("Не удалось создать папку плагина.");
        }

        if (!configFile.exists()) {
            try {
                if (plugin.getResource("builds_config.yml") != null) {
                    plugin.saveResource("builds_config.yml", false);
                } else if (!configFile.createNewFile()) {
                    plugin.getLogger().warning("Не удалось создать builds_config.yml.");
                }
            } catch (IOException exception) {
                plugin.getLogger().severe("Не удалось создать builds_config.yml: " + exception.getMessage());
            }
        }

        config = YamlConfiguration.loadConfiguration(configFile);
    }

    public void saveConfig() {
        try {
            config.save(configFile);
        } catch (IOException exception) {
            plugin.getLogger().severe("Не удалось сохранить builds_config.yml: " + exception.getMessage());
        }
    }

    public void reloadConfig() {
        config = YamlConfiguration.loadConfiguration(configFile);
    }

    public String getNameBuild(String buildName) {
        return getBuildName(buildName);
    }

    public Integer getInt(String path) {
        return config.getInt(path);
    }

    public Map<Material, Integer> getDemandBuild(String buildName) {
        Map<Material, Integer> resources = new LinkedHashMap<>();

        for (String entry : config.getStringList(buildName + ".resources")) {
            if (entry == null || entry.isBlank()) {
                continue;
            }

            String[] parts = entry.trim().split("\\s+");

            if (parts.length != 2) {
                plugin.getLogger().warning("Неверный формат ресурса: " + entry);
                continue;
            }

            Material material = Material.matchMaterial(parts[0]);

            if (material == null || material.isAir()) {
                plugin.getLogger().warning("Неизвестный или недопустимый материал: " + parts[0]);
                continue;
            }

            try {
                int amount = Integer.parseInt(parts[1]);

                if (amount <= 0) {
                    plugin.getLogger().warning("Количество должно быть больше 0: " + entry);
                    continue;
                }

                resources.merge(material, amount, Integer::sum);
            } catch (NumberFormatException exception) {
                plugin.getLogger().warning("Некорректное количество: " + entry);
            }
        }

        return resources;
    }

    public World getWorldBuild(String buildName) {
        String worldName = config.getString(buildName + ".world");
        return worldName == null ? null : Bukkit.getWorld(worldName);
    }

    public boolean createBuildToConfig(
            String buildName,
            BlockData[][][] buildBlocks,
            World world
    ) {
        if (buildName == null || !buildName.matches("[a-zA-Z0-9_-]{3,40}")
                || world == null || !isValidMatrix(buildBlocks)) {
            return false;
        }

        if (config.contains(buildName)) {
            plugin.getLogger().warning("Шаблон " + buildName + " уже существует.");
            return false;
        }

        config.set(buildName + ".name", buildName);
        config.set(buildName + ".world", world.getName());
        config.set(buildName + ".material", "CRAFTER");
        config.set(buildName + ".menu_name", "menu");
        config.set(buildName + ".lore", List.of("Крутое здание"));

        List<String> builds = new ArrayList<>(config.getStringList("builds_list"));

        if (!builds.contains(buildName)) {
            builds.add(buildName);
        }

        config.set("builds_list", builds);

        if (!saveMatrix(buildName + ".blocks", buildBlocks)) {
            return false;
        }

        saveConfig();
        return true;
    }

    public List<String> getBuilds() {
        return new ArrayList<>(config.getStringList("builds_list"));
    }

    public String getMenuName(String buildName) {
        return config.getString(buildName + ".menu_name");
    }

    public List<String> getBuildLore(String buildName) {
        return new ArrayList<>(config.getStringList(buildName + ".lore"));
    }

    public String getBuildName(String buildName) {
        return config.getString(buildName + ".name", buildName);
    }

    public Material getBuildMaterial(String buildName) {
        String materialName = config.getString(buildName + ".material", "BARRIER");
        Material material = Material.matchMaterial(materialName);
        return material == null ? Material.BARRIER : material;
    }

    public boolean saveMatrix(String path, BlockData[][][] matrix) {
        if (!isValidMatrix(matrix)) {
            plugin.getLogger().warning("Не удалось сохранить повреждённую матрицу: " + path);
            return false;
        }

        List<List<List<String>>> serialized = new ArrayList<>();

        for (BlockData[][] layer : matrix) {
            List<List<String>> serializedLayer = new ArrayList<>();

            for (BlockData[] row : layer) {
                List<String> serializedRow = new ArrayList<>();

                for (BlockData blockData : row) {
                    serializedRow.add(blockData.getAsString());
                }

                serializedLayer.add(serializedRow);
            }

            serialized.add(serializedLayer);
        }

        config.set(path, serialized);
        return true;
    }

    public BlockData[][][] loadMatrix(String path) {
        List<?> rawMatrix = config.getList(path);

        if (rawMatrix == null || rawMatrix.isEmpty()) {
            return null;
        }

        if (!(rawMatrix.get(0) instanceof List<?> firstLayer)
                || firstLayer.isEmpty()
                || !(firstLayer.get(0) instanceof List<?> firstRow)
                || firstRow.isEmpty()) {
            plugin.getLogger().warning("Неверная структура матрицы: " + path);
            return null;
        }

        int sizeY = rawMatrix.size();
        int sizeX = firstLayer.size();
        int sizeZ = firstRow.size();

        BlockData[][][] matrix = new BlockData[sizeY][sizeX][sizeZ];

        for (int y = 0; y < sizeY; y++) {
            Object rawLayer = rawMatrix.get(y);

            if (!(rawLayer instanceof List<?> layer) || layer.size() != sizeX) {
                plugin.getLogger().warning("Неверный размер Y-слоя матрицы: " + path);
                return null;
            }

            for (int x = 0; x < sizeX; x++) {
                Object rawRow = layer.get(x);

                if (!(rawRow instanceof List<?> row) || row.size() != sizeZ) {
                    plugin.getLogger().warning("Неверный размер X-строки матрицы: " + path);
                    return null;
                }

                for (int z = 0; z < sizeZ; z++) {
                    Object rawBlockData = row.get(z);

                    if (!(rawBlockData instanceof String blockDataString)) {
                        plugin.getLogger().warning("BlockData должен быть строкой: " + path);
                        return null;
                    }

                    try {
                        matrix[y][x][z] = Bukkit.createBlockData(blockDataString);
                    } catch (IllegalArgumentException exception) {
                        plugin.getLogger().warning("Не удалось прочитать BlockData: " + blockDataString);
                        return null;
                    }
                }
            }
        }

        return matrix;
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
}
