package me.vesk.townyCore;

import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ConfigManager {
    private final JavaPlugin plugin;
    private FileConfiguration config;
    private File configFile;
    private boolean isConfigValid = false;

    public ConfigManager(JavaPlugin plugin) {
        this.plugin = plugin;
        this.configFile = new File(plugin.getDataFolder(), "config.yml");
        loadConfig();
    }

    /**
     * Загрузка конфига с защитой от повреждений
     */
    public void loadConfig() {
        // 1. Проверяем, существует ли папка плагина
        if (!plugin.getDataFolder().exists()) {
            plugin.getDataFolder().mkdirs();
        }

        // 2. Проверяем, существует ли файл конфига
        if (!configFile.exists()) {
            plugin.getLogger().info("config.yml не найден. Создаю новый...");
            plugin.saveDefaultConfig();
            plugin.getLogger().info("config.yml создан!");
        }

        // 3. Проверяем размер файла
        if (configFile.length() == 0) {
            plugin.getLogger().severe("config.yml ПУСТОЙ! Создаю резервную копию и новый файл...");
            createBackup("config.yml.empty");
            plugin.saveDefaultConfig();
        }

        // 4. Загружаем конфиг
        try {
            plugin.reloadConfig();
            config = plugin.getConfig();

            // 5. Проверяем, что конфиг загрузился правильно
            if (config.getKeys(false).isEmpty()) {
                plugin.getLogger().severe("config.yml поврежден или имеет ошибки в синтаксисе!");
                plugin.getLogger().severe("Создаю резервную копию и восстанавливаю конфиг...");

                createBackup("config.yml.broken");
                plugin.saveDefaultConfig();
                plugin.reloadConfig();
                config = plugin.getConfig();

                if (config.getKeys(false).isEmpty()) {
                    plugin.getLogger().severe("НЕ УДАЛОСЬ ВОССТАНОВИТЬ КОНФИГ! Проверьте файл вручную.");
                    isConfigValid = false;
                    return;
                }
            }

            isConfigValid = true;
            plugin.getLogger().info("✓ Конфиг загружен успешно! Секций: " + config.getKeys(false).size());

        } catch (Exception e) {
            plugin.getLogger().severe("Ошибка при загрузке config.yml: " + e.getMessage());
            createBackup("config.yml.error");
            isConfigValid = false;
        }
    }

    /**
     * Создание резервной копии конфига
     */
    private void createBackup(String backupName) {
        try {
            File backupFile = new File(plugin.getDataFolder(), backupName);
            if (configFile.exists()) {
                java.nio.file.Files.copy(
                        configFile.toPath(),
                        backupFile.toPath(),
                        java.nio.file.StandardCopyOption.REPLACE_EXISTING
                );
                plugin.getLogger().info("Создана резервная копия: " + backupName);
            }
        } catch (IOException e) {
            plugin.getLogger().warning("Не удалось создать резервную копию: " + e.getMessage());
        }
    }

    /**
     * Сохранение конфига
     */
    public void saveConfig() {
        if (config == null) {
            plugin.getLogger().warning("Нельзя сохранить конфиг: config == null");
            return;
        }

        try {
            config.save(configFile);
            plugin.getLogger().info("✓ Конфиг сохранен");
        } catch (IOException e) {
            plugin.getLogger().severe("Не удалось сохранить config.yml: " + e.getMessage());
        }
    }

    /**
     * Перезагрузка конфига
     */
    public void reloadConfig() {
        loadConfig();
    }

    /**
     * Проверка валидности конфига
     */
    public boolean isConfigValid() {
        return isConfigValid;
    }

    // ============== МЕТОДЫ ДЛЯ РАБОТЫ С КОНФИГОМ ==============

    /**
     * Получение ресурсов для уровня притязаний
     */
    public Map<Material, Integer> getDemandClaim(Integer claimLevel) {
        if (!isConfigValid) {
            plugin.getLogger().warning("Конфиг невалиден, возвращаю пустую карту");
            return new HashMap<>();
        }
        return getDemand("demand.claim_level." + claimLevel.toString());
    }

    /**
     * Получение ресурсов по пути
     */
    public Map<Material, Integer> getDemand(String path) {
        Map<Material, Integer> resources = new HashMap<>();

        if (!isConfigValid) {
            plugin.getLogger().warning("Конфиг невалиден, возвращаю пустую карту для пути: " + path);
            return resources;
        }

        // Проверяем, существует ли путь
        if (!config.contains(path)) {
            plugin.getLogger().warning("Путь не найден в конфиге: " + path);
            return resources;
        }

        List<String> list = config.getStringList(path);

        // Проверяем, что список не пустой
        if (list == null || list.isEmpty()) {
            plugin.getLogger().warning("Пустой список для пути: " + path);
            return resources;
        }

        for (String entry : list) {
            String[] parts = entry.split(" ");
            if (parts.length != 2) {
                plugin.getLogger().warning("Неверный формат ресурса: " + entry + " (нужно: МАТЕРИАЛ КОЛИЧЕСТВО)");
                continue;
            }

            String materialName = parts[0].toUpperCase();
            int amount;

            try {
                amount = Integer.parseInt(parts[1]);
            } catch (NumberFormatException e) {
                plugin.getLogger().warning("Некорректное число для " + entry);
                continue;
            }

            Material material = Material.getMaterial(materialName);
            if (material == null) {
                plugin.getLogger().warning("Неизвестный материал: " + materialName);
                continue;
            }

            if (amount <= 0) {
                plugin.getLogger().warning("Количество должно быть больше 0 для " + entry);
                continue;
            }

            if (material.isAir()) {
                plugin.getLogger().warning("Материал не может быть AIR для " + entry);
                continue;
            }

            resources.put(material, amount);
        }

        return resources;
    }

    /**
     * Получение списка плотов для уровня
     */
    public ArrayList<String> getClaimPlots(String level) {
        ArrayList<String> claimPlots = new ArrayList<>();

        if (!isConfigValid) {
            plugin.getLogger().warning("Конфиг невалиден, возвращаю пустой список");
            return claimPlots;
        }

        String path = "claim_level." + level;
        if (!config.contains(path)) {
            plugin.getLogger().warning("Путь не найден: " + path);
            return claimPlots;
        }

        List<String> list = config.getStringList(path);
        if (list != null) {
            claimPlots.addAll(list);
        }

        return claimPlots;
    }

    /**
     * Получение информации о городе
     */
    public ArrayList<String> getTownInfo() {
        ArrayList<String> messages = new ArrayList<>();

        if (!isConfigValid) {
            plugin.getLogger().warning("Конфиг невалиден, возвращаю пустой список");
            return messages;
        }

        String path = "messages.townInfo";
        if (!config.contains(path)) {
            plugin.getLogger().warning("Путь не найден: " + path);
            return messages;
        }

        List<String> list = config.getStringList(path);
        if (list != null) {
            messages.addAll(list);
        }

        return messages;
    }

    // ============== ГЕТТЕРЫ ДЛЯ СООБЩЕНИЙ ==============

    public String getNotEnoughResources() {
        return getMessage("messages.notEnoughResources", "&cНедостаточно ресурсов!");
    }

    public String getCreateTown() {
        return getMessage("messages.createTown", "&aГород создан!");
    }

    public String getNotTownPos() {
        return getMessage("messages.notTownPos", "&cНе выбрана позиция!");
    }

    public String getNotTown() {
        return getMessage("messages.notTown", "&cВы не в городе!");
    }

    public String getMaxClaimLevel() {
        return getMessage("messages.maxClaimLevel", "&cМаксимальный уровень достигнут!");
    }

    /**
     * Универсальный метод получения сообщений с дефолтным значением
     */
    private String getMessage(String path, String defaultValue) {
        if (!isConfigValid) {
            plugin.getLogger().warning("Конфиг невалиден, возвращаю дефолтное сообщение для: " + path);
            return defaultValue;
        }

        if (!config.contains(path)) {
            plugin.getLogger().warning("Путь не найден: " + path + ", использую дефолтное значение");
            return defaultValue;
        }

        String message = config.getString(path);
        return message != null ? message : defaultValue;
    }

    /**
     * Получение максимального уровня притязаний
     */
    public Integer getLevelsClaim() {
        if (!isConfigValid) {
            plugin.getLogger().warning("Конфиг невалиден, возвращаю дефолтное значение: 1");
            return 1;
        }

        if (!config.contains("claim_levels")) {
            plugin.getLogger().warning("claim_levels не найден, использую дефолтное значение: 1");
            return 1;
        }

        return config.getInt("claim_levels", 1);
    }

    /**
     * Получение сырого конфига (для экстренных случаев)
     */
    public FileConfiguration getRawConfig() {
        return config;
    }

    /**
     * Проверка наличия секции в конфиге
     */
    public boolean hasSection(String path) {
        return isConfigValid && config.contains(path);
    }
}