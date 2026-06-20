package me.vesk.townyCore;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.data.BlockData;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
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

        if (!configFile.exists()) {
            configFile.getParentFile().mkdirs();
            // Сохраняем дефолтный файл из ресурсов плагина, если есть
            plugin.saveResource("builds_config.yml", false);
        }

        config = YamlConfiguration.loadConfiguration(configFile);
    }

    public void saveConfig() {
        try {
            config.save(configFile);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void reloadConfig() {
        config = YamlConfiguration.loadConfiguration(configFile);
    }

    public String getNameBuild(String buildName) {
        return config.getString(buildName + ".name");
    }
    public Integer getInt(String path) {
        return config.getInt(path);
    }

    public Map<Material, Integer> getDemandBuild(String buildName) {
        Map<Material, Integer> resources = new HashMap<>();
        for (String entry : config.getStringList(buildName + ".resources")) {
            String[] parts = entry.split(" ");
            if (parts.length != 2) {
                plugin.getLogger().warning("Неверный формат ресурса: " + entry);
                continue;
            }
            String materialName = parts[0];
            int materialNum;
            try {
                materialNum = Integer.parseInt(parts[1]);
            } catch (NumberFormatException e) {
                plugin.getLogger().warning("Некорректное число для " + entry);
                continue;
            }
            Material material = Material.getMaterial(materialName.toUpperCase());
            if (material == null) {
                plugin.getLogger().warning("Неизвестный материал: " + materialName);
                continue;
            }
            if (materialNum > 0 && !material.isAir()) {
                resources.put(material, materialNum);
            }
        }
        return resources;
    }

    public Map<Material, Location> getBlockBuild(String buildName) {
        Map<Material, Location> resources = new HashMap<>();
        for (String entry : config.getStringList(buildName + ".blocks")) {
            String[] parts = entry.split(" ");
            if (parts.length != 2) {
                plugin.getLogger().warning("Неверный формат ресурса: " + entry);
                continue;
            }
            String materialName = parts[0];
            Location materialLoc = new Location(getWorldBuild(buildName),
                    Integer.parseInt(parts[1]),Integer.parseInt(parts[2]),
                    Integer.parseInt(parts[3]));



            Material material = Material.getMaterial(materialName.toUpperCase());
            if (material == null) {
                plugin.getLogger().warning("Неизвестный материал: " + materialName);
                continue;
            }

            resources.put(material, materialLoc);

        }
        return resources;
    }

    public World getWorldBuild(String buildName) {
        return Bukkit.getWorld(config.getString(buildName + ".world"));
    }

    public void createBuildToConfig(String buildName, BlockData[][][] buildBlocks, World world) {
        config.set(buildName+".name","Untitel");
        config.set(buildName+".world",world.getName());
        config.set(buildName+".material","CRAFTER");
        config.set(buildName+".menu_name","menu");
        List<String> lore = new ArrayList<>();
        lore.add("Крутое здание");

        config.set(buildName+".lore",lore);

        List<String> builds = config.getStringList("builds_list");
        builds.add(buildName);

        config.set("builds_list",builds);

        saveMatrix(buildName+".blocks",buildBlocks);

        saveConfig();
    }

    public List<String> getBuilds() {
        return config.getStringList("builds_list");
    }

    public String getMenuName(String buildName) {
        return config.getString(buildName+".menu_name");
    }

    public List<String> getBuildLore(String buildName) {
        return config.getStringList(buildName+".lore");
    }
    public String getBuildName(String buildName) {
        return config.getString(buildName+".name");
    }
    public Material getBuildMaterial(String buildName) {
        return Material.getMaterial(config.getString(buildName+".material"));
    }

    // Сохранение матрицы BlockData в конфиг
    public void saveMatrix(String path, BlockData[][][] matrix) {
        // Создаем вложенную структуру List
        List<List<List<String>>> serialized = new ArrayList<>();

        for (int y = 0; y < matrix.length; y++) {
            List<List<String>> yLevel = new ArrayList<>();

            for (int x = 0; x < matrix[y].length; x++) {
                List<String> xLine = new ArrayList<>();

                for (int z = 0; z < matrix[y][x].length; z++) {
                    BlockData blockData = matrix[y][x][z];
                    // Сохраняем BlockData в строковом формате
                    if (blockData != null) {
                        xLine.add(blockData.getAsString());
                    } else {
                        xLine.add(Material.AIR.createBlockData().getAsString());
                    }
                }

                yLevel.add(xLine);
            }

            serialized.add(yLevel);
        }

        config.set(path, serialized);
        plugin.saveConfig();
    }

    // Загрузка матрицы BlockData из конфига
    public BlockData[][][] loadMatrix(String path) {
        if (!config.contains(path)) {
            return null;
        }

        // Получаем вложенную структуру
        List<?> serialized = config.getList(path);
        if (serialized == null) return null;

        // Проверка на пустую структуру
        if (serialized.isEmpty()) return null;

        // Определяем размеры
        int sizeY = serialized.size();
        int sizeX = ((List<?>) serialized.get(0)).size();
        int sizeZ = ((List<?>) ((List<?>) serialized.get(0)).get(0)).size();

        BlockData[][][] matrix = new BlockData[sizeY][sizeX][sizeZ];

        // Восстанавливаем матрицу
        for (int y = 0; y < sizeY; y++) {
            List<?> yLevel = (List<?>) serialized.get(y);

            if (yLevel.isEmpty()) continue;

            for (int x = 0; x < sizeX; x++) {
                List<?> xLine = (List<?>) yLevel.get(x);

                if (xLine.isEmpty()) continue;

                for (int z = 0; z < sizeZ; z++) {
                    String blockDataString = (String) xLine.get(z);

                    // Парсим BlockData из строки
                    try {
                        matrix[y][x][z] = Bukkit.createBlockData(blockDataString);
                    } catch (IllegalArgumentException e) {
                        // Если не удалось распарсить, ставим AIR
                        plugin.getLogger().warning("Failed to parse BlockData: " + blockDataString);
                        matrix[y][x][z] = Material.AIR.createBlockData();
                    }
                }
            }
        }

        return matrix;
    }
}
