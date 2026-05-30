package me.vesk.townyCore;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;

public class Manager {
    private final JavaPlugin plugin;
    private Map<String,Integer> demand = new HashMap<>();
    private final ConfigManager configManager;

    public Manager(JavaPlugin plugin, ConfigManager configManager) {
        this.plugin = plugin;
        this.configManager = configManager;
    }

    public void writingOffDemand(Player player, Boolean is_claim, Integer level_claim) {
        Map<Material, Integer> demandResources = configManager.getDemand("demand.resources");

        if (is_claim) {
            demandResources = configManager.getDemandClaim(level_claim);
        }

        for (Map.Entry<Material, Integer> entry : demandResources.entrySet()) {

            for (int j = 0; j < player.getInventory().getSize(); j++) {
                ItemStack item = player.getInventory().getItem(j);
                if (item.getType() != entry.getKey()) {continue;}

                int toRemove = entry.getValue();
                int itemAmout = item.getAmount();

                if (toRemove >= itemAmout) {
                    player.getInventory().remove(item);
                    toRemove -= itemAmout;
                }
                else {
                    item.setAmount(itemAmout - toRemove);
                    toRemove = 0;
                    break;
                }

            }
        }

    }

    public List<List<Component>> checkDemand(Player player, Boolean is_claim, Integer level_claim) {
        ItemStack[] playerResources = player.getInventory().getStorageContents();
        Map<Material, Integer> demandResources = configManager.getDemand("demand.resources");
        if (is_claim) {
            demandResources = configManager.getDemandClaim(level_claim);
        }

        for (int i = 0; i < playerResources.length; i++) {
            if (playerResources[i] == null) {continue;}
            ItemStack itemStack = playerResources[i];
            Material material = playerResources[i].getType();
            if (demandResources.containsKey(material)) {
                int amount = playerResources[i].getAmount();
                int remaining = demandResources.get(material) - amount;
                if (remaining <= 0) {
                    demandResources.remove(material);
                } else {
                    demandResources.put(material, remaining);
                }
            }
        }

        List<Component> missingComponents = new ArrayList<>();
        List<Component> missingAmount = new ArrayList<>();



        if (!demandResources.isEmpty()) {
            for (Map.Entry<Material, Integer> entry : demandResources.entrySet()) {
                String translationKey = entry.getKey().translationKey();
                Component missing = Component.translatable(translationKey);
                missingComponents.add(missing);
                missingAmount.add(Component.text(entry.getValue().toString()));
            }
        }

        List<List<Component>> finalResult = new ArrayList<>();
        finalResult.add(missingComponents);
        finalResult.add(missingAmount);

        return finalResult;
    }

}
