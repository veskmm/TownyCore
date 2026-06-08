package me.vesk.townyCore;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.*;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;

public class Manager {
    private final JavaPlugin plugin;
    private Map<String,Integer> demand = new HashMap<>();
    private final ConfigManager configManager;
    private final BuildsConfig buildsConfig;

    public Manager(JavaPlugin plugin, ConfigManager configManager, BuildsConfig buildsConfig) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.buildsConfig = buildsConfig;
    }

    public void writingOffDemand(Player player, Boolean is_claim, Integer level_claim) {
        Map<Material, Integer> demandResources = configManager.getDemand("demand.resources");

        if (is_claim) {
            demandResources = configManager.getDemandClaim(level_claim);
        }

        for (Map.Entry<Material, Integer> entry : demandResources.entrySet()) {

            for (int j = 0; j < player.getInventory().getSize(); j++) {
                ItemStack item = player.getInventory().getItem(j);

                if (item == null || item.getType() == Material.AIR) {
                    continue;  // пустой слот — пропускаем
                }

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

    public void makeBuild(Location cord, Player player, String nameBuld) {
        BlockData[][][] matrix_blocks = buildsConfig.loadMatrix(nameBuld+".blocks");

        int playerX = (int) cord.x();
        int playerY = (int) cord.y();
        int playerZ = (int) cord.z();

        World world = player.getWorld();

        // Матрица может быть пустой или иметь нулевую длину
        if (matrix_blocks == null || matrix_blocks.length == 0) return;

        for (int Y = playerY; Y < matrix_blocks.length + playerY; Y++) {
            for (int X = playerX; X < matrix_blocks[0].length + playerX; X++) {
                for (int Z = playerZ; Z < matrix_blocks[0][0].length + playerZ; Z++) {
                    world.getBlockAt(X,Y,Z).setBlockData(matrix_blocks[Y-playerY][X-playerX][Z-playerZ]);
                }
            }
        }
    }

    public boolean addBuild(Location cord1, Location cord2, Player player, String nameBuild) {

        int x1 = (int) cord1.x();
        int y1 = (int) cord1.y();
        int z1 = (int) cord1.z();

        int x2 = (int) cord2.x();
        int y2 = (int) cord2.y();
        int z2 = (int) cord2.z();

        int maxX = Integer.max(x1,x2);
        int maxY = Integer.max(y1,y2);
        int maxZ = Integer.max(z1,z2);

        int minX = Integer.min(x1,x2);
        int minY = Integer.min(y1,y2);
        int minZ = Integer.min(z1,z2);

        // Создаем трехмерный массив
        BlockData[][][] build_matrix = new BlockData[maxY - minY][maxX - minX][maxZ - minZ];

        World world = player.getWorld();

        for (int Y = minY; Y < maxY; Y++) {
            for (int X = minX; X < maxX; X++) {
                for (int Z = minZ; Z < maxZ; Z++) {
                    build_matrix[Y - minY][X - minX][Z - minZ] = world.getBlockData(X,Y,Z);
                }
            }
        }

        buildsConfig.createBuildToConfig(nameBuild,build_matrix,player.getWorld());

        return true;
    }
}
