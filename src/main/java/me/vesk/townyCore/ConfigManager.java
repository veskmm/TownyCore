package me.vesk.townyCore;

import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class ConfigManager {

    private final JavaPlugin plugin;
    private final File configFile;
    private FileConfiguration config;
    private boolean configValid;

    public ConfigManager(JavaPlugin plugin) {
        this.plugin = plugin;
        this.configFile = new File(plugin.getDataFolder(), "config.yml");
        loadConfig();
    }

    public void loadConfig() {
        try {
            if (!plugin.getDataFolder().exists() && !plugin.getDataFolder().mkdirs()) {
                plugin.getLogger().severe("Не удалось создать папку плагина.");
                configValid = false;
                return;
            }

            if (!configFile.exists()) {
                plugin.saveDefaultConfig();
            }

            if (configFile.exists() && configFile.length() == 0L) {
                backupAndRestore("config.yml.empty");
            }

            plugin.reloadConfig();
            config = plugin.getConfig();

            if (config.getKeys(false).isEmpty()) {
                backupAndRestore("config.yml.broken");
                plugin.reloadConfig();
                config = plugin.getConfig();
            }

            configValid = config != null && !config.getKeys(false).isEmpty();

            if (!configValid) {
                plugin.getLogger().severe("Не удалось загрузить корректный config.yml.");
            }
        } catch (Exception exception) {
            configValid = false;
            plugin.getLogger().severe("Ошибка загрузки config.yml: " + exception.getMessage());
        }
    }

    private void backupAndRestore(String backupName) throws IOException {
        if (configFile.exists()) {
            Files.copy(
                    configFile.toPath(),
                    new File(plugin.getDataFolder(), backupName).toPath(),
                    StandardCopyOption.REPLACE_EXISTING
            );

            Files.delete(configFile.toPath());
        }

        plugin.saveDefaultConfig();
    }

    public void saveConfig() {
        if (config == null) {
            plugin.getLogger().warning("Нельзя сохранить config.yml: конфиг не загружен.");
            return;
        }

        try {
            config.save(configFile);
        } catch (IOException exception) {
            plugin.getLogger().severe("Не удалось сохранить config.yml: " + exception.getMessage());
        }
    }

    public void reloadConfig() {
        loadConfig();
    }

    public boolean isConfigValid() {
        return configValid;
    }

    public Map<Material, Integer> getDemandClaim(int claimLevel) {
        return getDemand("demand.claim_level." + claimLevel);
    }

    public Map<Material, Integer> getDemand(String path) {
        Map<Material, Integer> resources = new LinkedHashMap<>();

        if (!configValid || !config.contains(path)) {
            return resources;
        }

        for (String entry : config.getStringList(path)) {
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

    public ArrayList<String> getClaimPlots(String level) {
        if (!configValid) {
            return new ArrayList<>();
        }

        return new ArrayList<>(config.getStringList("claim_level." + level));
    }

    public ArrayList<String> getTownInfo() {
        if (!configValid) {
            return new ArrayList<>();
        }

        return new ArrayList<>(config.getStringList("messages.townInfo"));
    }

    public String getNotEnoughResources() {
        return getMessage("messages.notEnoughResources", "&cНедостаточно ресурсов!");
    }

    public String getCreateTown() {
        return getMessage("messages.createTown", "&aГород создан!");
    }

    public String getNotTownPos() {
        return getMessage("messages.notTownPos", "&cНельзя строить за пределами своего города!");
    }

    public String getNotTown() {
        return getMessage("messages.notTown", "&cВы не состоите в городе!");
    }

    public String getMaxClaimLevel() {
        return getMessage("messages.maxClaimLevel", "&cМаксимальный уровень достигнут!");
    }

    public int getLevelsClaim() {
        return configValid ? config.getInt("claim_levels", 1) : 1;
    }

    public FileConfiguration getRawConfig() {
        return config;
    }

    public boolean hasSection(String path) {
        return configValid && config.contains(path);
    }

    private String getMessage(String path, String defaultValue) {
        if (!configValid) {
            return defaultValue;
        }

        return config.getString(path, defaultValue);
    }
}
