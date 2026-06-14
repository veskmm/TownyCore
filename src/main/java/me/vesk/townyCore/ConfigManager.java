package me.vesk.townyCore;

import com.palmergames.adventure.key.KeyPattern;
import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ConfigManager {
    private final JavaPlugin plugin;
    private FileConfiguration config;

    public ConfigManager(JavaPlugin plugin) {
        this.plugin = plugin;
        loadConfig();
    }

    public void loadConfig() {
        plugin.reloadConfig();
        config = plugin.getConfig();
    }

    public Map<Material, Integer> getDemandClaim(Integer claim_level) {
        return getDemand("demand.claim_level."+claim_level.toString());
    }

    public Map<Material, Integer> getDemand(String path) {
        Map<Material, Integer> resources = new HashMap<>();
        for (String entry : config.getStringList(path)) {
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

    public ArrayList<String> getClaimPlots(String level) {
        ArrayList<String> claimPlots = new ArrayList<>();
        for (String entry : config.getStringList("claim_level."+level)) {
            claimPlots.add(entry);
        }
        return claimPlots;
    }

    public ArrayList<String> getTownInfo() {
        ArrayList<String> messages = new ArrayList<>();
        for (String entry : config.getStringList("messages.townInfo")) {
            messages.add(entry);
        }
        return messages;
    }

    public String getNotEnoughResources() {
        return config.getString("messages.notEnoughResources");
    }

    public String getcreateTown() {
        return config.getString("messages.createTown");
    }

    public String getNotTownPos() {
        return config.getString("messages.notTownPos");
    }

    public String getNotTown() {
        return config.getString("messages.notTown");
    }

    public Integer getLevelsClaim() {
        return config.getInt("claim_levels");
    }

    public String getMaxClaimLevel() {
        return config.getString("messages.maxClaimLevel");
    }
}
