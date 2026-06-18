package me.vesk.townyCore;

import com.palmergames.bukkit.towny.object.Coord;
import com.palmergames.bukkit.towny.object.Position;
import com.palmergames.bukkit.towny.object.WorldCoord;
import io.papermc.paper.math.BlockPosition;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
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

        if (!configFile.exists()) {
            configFile.getParentFile().mkdirs();
            // Сохраняем дефолтный файл из ресурсов плагина, если есть
            plugin.saveResource("towns_config.yml", false);
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

    public String getString(String path) {
        return config.getString(path);
    }
    public Integer getInt(String path) {
        return config.getInt(path);
    }
    public Boolean isHasBuild(String townName, String buildName) {
        return config.getBoolean(townName +"."+ buildName);
    }
    public void setHasBuild(String townName, String buildName) {
        config.set(townName +"."+ buildName,true);
        saveConfig();
    }


    public void setLine(String path, Object value) {
        config.set(path,value);
        saveConfig();
    }

    public void createLine(String nameLine) {
        config.createSection(nameLine);
        saveConfig();
    }

    // Сохранение Map<Location, BlockData>
    public void saveOldBorder(String townName, Map<Location, BlockData> oldBlocks) {
        // Создаем список для сохранения
        List<Map<String, Object>> serializedBlocks = new ArrayList<>();

        for (Map.Entry<Location, BlockData> entry : oldBlocks.entrySet()) {
            Map<String, Object> blockData = new HashMap<>();

            // Сериализуем Location в Map
            blockData.put("location", entry.getKey().serialize());

            // Сериализуем BlockData в строку
            blockData.put("blockData", entry.getValue().getAsString()); // например: "minecraft:stone"

            serializedBlocks.add(blockData);
        }

        // Сохраняем в конфиг
        config.set("oldBorder." + townName, serializedBlocks);
        saveConfig();
    }

    // Загрузка Map<Location, BlockData>
    public Map<Location, BlockData> loadOldBorder(String townName) {
        Map<Location, BlockData> result = new HashMap<>();

        List<Map<?, ?>> serializedBlocks = (List<Map<?, ?>>) config.getList("oldBorder." + townName);
        if (serializedBlocks == null) {
            return result;
        }

        for (Map<?, ?> data : serializedBlocks) {
            // Десериализуем Location
            Map<String, Object> locationMap = (Map<String, Object>) data.get("location");
            Location location = Location.deserialize(locationMap);

            // Десериализуем BlockData
            String blockDataString = (String) data.get("blockData");
            BlockData blockData = Bukkit.createBlockData(blockDataString);

            result.put(location, blockData);
        }

        return result;
    }

}
