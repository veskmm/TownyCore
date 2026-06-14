package me.vesk.townyCore;

import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
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
}
