package me.vesk.townyCore;

import com.palmergames.bukkit.towny.object.WorldCoord;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.block.data.BlockData;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class TownsConfig {

    private final JavaPlugin plugin;
    private File configFile;
    private FileConfiguration config;

    public TownsConfig(JavaPlugin plugin) {
        this.plugin = plugin;
        loadConfig();
    }

    private void loadConfig() {
        configFile = new File(plugin.getDataFolder(), "towns_config.yml");

        if (!plugin.getDataFolder().exists() && !plugin.getDataFolder().mkdirs()) {
            plugin.getLogger().warning("Не удалось создать папку плагина.");
        }

        if (!configFile.exists()) {
            try {
                if (plugin.getResource("towns_config.yml") != null) {
                    plugin.saveResource("towns_config.yml", false);
                } else if (!configFile.createNewFile()) {
                    plugin.getLogger().warning("Не удалось создать towns_config.yml.");
                }
            } catch (IOException exception) {
                plugin.getLogger().severe("Не удалось создать towns_config.yml: " + exception.getMessage());
            }
        }

        config = YamlConfiguration.loadConfiguration(configFile);
    }

    public void saveConfig() {
        try {
            config.save(configFile);
        } catch (IOException exception) {
            plugin.getLogger().severe("Не удалось сохранить towns_config.yml: " + exception.getMessage());
        }
    }

    public void reloadConfig() {
        config = YamlConfiguration.loadConfiguration(configFile);
    }

    public String getString(String path) {
        return config.getString(path);
    }

    public int getInt(String path, int defaultValue) {
        return config.getInt(path, defaultValue);
    }

    public int getInt(String path) {
        return config.getInt(path, 0);
    }

    public boolean isHasBuild(String townName, String buildName) {
        if (townName == null || buildName == null) {
            return false;
        }

        return config.getBoolean(townName + "." + buildName, false);
    }

    public void setHasBuild(String townName, String buildName) {
        if (townName == null || buildName == null) {
            return;
        }

        config.set(townName + "." + buildName, true);
        saveConfig();
    }

    public void setLine(String path, Object value) {
        config.set(path, value);
        saveConfig();
    }

    public void createLine(String nameLine) {
        if (!config.contains(nameLine)) {
            config.createSection(nameLine);
            saveConfig();
        }
    }

    public void initializeTown(
            String townName,
            String mayorName,
            WorldCoord homeCoord
    ) {
        config.set(townName + ".level_claim", 0);
        config.set(townName + ".mayor", mayorName);

        if (homeCoord != null) {
            config.set(townName + ".position_x", homeCoord.getX());
            config.set(townName + ".position_z", homeCoord.getZ());
        }

        config.set("oldBorder." + townName, new ArrayList<>());
        saveConfig();
    }

    public void ensureTown(String townName) {
        if (!config.contains(townName + ".level_claim")) {
            config.set(townName + ".level_claim", 0);
            saveConfig();
        }
    }

    public void removeTown(String townName) {
        config.set(townName, null);
        config.set("oldBorder." + townName, null);
        saveConfig();
    }

    public void saveOldBorder(String townName, Map<Location, BlockData> oldBlocks) {
        List<Map<String, Object>> serializedBlocks = new ArrayList<>();

        if (oldBlocks != null) {
            for (Map.Entry<Location, BlockData> entry : oldBlocks.entrySet()) {
                Location location = entry.getKey();
                BlockData blockData = entry.getValue();

                if (location == null || location.getWorld() == null || blockData == null) {
                    continue;
                }

                Map<String, Object> serialized = new LinkedHashMap<>();
                serialized.put("location", location.serialize());
                serialized.put("blockData", blockData.getAsString());
                serializedBlocks.add(serialized);
            }
        }

        config.set("oldBorder." + townName, serializedBlocks);
        saveConfig();
    }

    public Map<Location, BlockData> loadOldBorder(String townName) {
        Map<Location, BlockData> result = new LinkedHashMap<>();
        List<?> serializedBlocks = config.getList("oldBorder." + townName);

        if (serializedBlocks == null) {
            return result;
        }

        for (Object rawEntry : serializedBlocks) {
            if (!(rawEntry instanceof Map<?, ?> entry)) {
                continue;
            }

            Object rawLocation = entry.get("location");
            Object rawBlockData = entry.get("blockData");

            if (!(rawLocation instanceof Map<?, ?> rawLocationMap)
                    || !(rawBlockData instanceof String blockDataString)) {
                continue;
            }

            Map<String, Object> locationMap = new HashMap<>();

            for (Map.Entry<?, ?> locationEntry : rawLocationMap.entrySet()) {
                if (locationEntry.getKey() instanceof String key) {
                    locationMap.put(key, locationEntry.getValue());
                }
            }

            try {
                Location location = Location.deserialize(locationMap);
                BlockData blockData = Bukkit.createBlockData(blockDataString);

                if (location != null && location.getWorld() != null) {
                    result.put(location, blockData);
                }
            } catch (IllegalArgumentException exception) {
                plugin.getLogger().warning(
                        "Не удалось загрузить блок границы города " + townName
                );
            }
        }

        return result;
    }

    public void clearOldBorder(String townName) {
        config.set("oldBorder." + townName, null);
        saveConfig();
    }
}
