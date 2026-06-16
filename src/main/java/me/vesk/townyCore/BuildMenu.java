package me.vesk.townyCore;

import co.aikar.commands.BaseCommand;
import co.aikar.commands.PaperCommandManager;
import co.aikar.commands.annotation.CommandAlias;
import co.aikar.commands.annotation.Subcommand;
import com.github.stefvanschie.inventoryframework.gui.GuiItem;
import com.github.stefvanschie.inventoryframework.gui.type.ChestGui;
import com.github.stefvanschie.inventoryframework.gui.type.util.Gui;
import com.github.stefvanschie.inventoryframework.pane.StaticPane;
import com.github.stefvanschie.inventoryframework.pane.util.Slot;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.eclipse.aether.repository.LocalArtifactRegistration;

import java.util.ArrayList;
import java.util.List;

public class BuildMenu implements Listener {

    private final ChestGui gui;
    private final Player player;
    private final BuildsConfig buildsConfig;
    private final Manager manager;

    private boolean isStartBuild = false;
    private String buildName;
    private Location oldLocation;
    private BlockData[][] oldLayer;
    private World oldWorld;
    private int oldX;
    private int oldY;
    private int oldZ;

    public BuildMenu(Player player, BuildsConfig buildsConfig, Manager manager) {
        this.buildsConfig = buildsConfig;
        this.player = player;
        this.manager = manager;
        this.gui = new ChestGui(6, "Городские здания");

        guiSetup();
    }

    public Player getPlayer() {return player;}

    private void guiSetup() {

        StaticPane buildsBlock = new StaticPane(2,2,5,2);
        List<String> builds = buildsConfig.getBuilds();
        for (int ind = 0; ind < builds.size(); ind++) {
            ItemStack build = new ItemStack(buildsConfig.getBuildMaterial(builds.get(ind)));
            ItemMeta metaBuild = build.getItemMeta();

            metaBuild.setDisplayName(ChatColor.translateAlternateColorCodes('&', buildsConfig.getBuildName(builds.get(ind))));

            List<String> bL = buildsConfig.getBuildLore(builds.get(ind));
            List<String> buildLore = new ArrayList<>();

            //buildLore.add(" ");

            for (int k = 0; k < bL.size(); k++) {
                String translatedLine = ChatColor.translateAlternateColorCodes('&', bL.get(k));
                buildLore.add(translatedLine);
            }

            metaBuild.setLore(buildLore);
            build.setItemMeta(metaBuild);
            int index = ind;
            buildsBlock.addItem(new GuiItem(build, event -> {
                startBuild(player, builds.get(index));
                player.closeInventory();
                event.setCancelled(true);
            }
            ),ind,0);
        }

        gui.addPane(buildsBlock);


        // Background
        StaticPane background = new StaticPane(0, 0, 9, 6);

        ItemStack filler = new ItemStack(Material.BLACK_STAINED_GLASS_PANE);
        ItemMeta meta = filler.getItemMeta();
        meta.setDisplayName(" ");
        filler.setItemMeta(meta);

        for (int i = 0; i <= 8; i++) {
            for (int x = 0; x <= 8; x++) {
                background.addItem(new GuiItem(filler, event -> {event.setCancelled(true);}), x, i);
            }
        }

        gui.addPane(background);
    }

    public void openMenu() {
        gui.show(player);
    }

    public void startBuild(Player player, String nameBuild) {
        player.sendMessage("нажми ЛКМ по блоку чтобы начать строительство");
        isStartBuild = true;
        buildName = nameBuild;

    }

    private void makeMarking(Location startLocal) {
        BlockData[][][] matrix_blocks = buildsConfig.loadMatrix(buildName+".blocks");

        if (oldLayer != null) {
            for (int x = 0; x < oldLayer.length; x++) {
                for (int z = 0; z < oldLayer[x].length; z++) {
                    oldWorld.getBlockAt(x+oldX,oldY,z+oldZ).setType(oldLayer[x][z].getMaterial());
                }
            }
        }

        BlockData[][] newLayer = matrix_blocks[0];
        BlockData[][] oldLayerLocal = new BlockData[newLayer.length][newLayer[0].length];

        int startX = (int) startLocal.x();
        int startY = (int) startLocal.y();
        int startZ = (int) startLocal.z();

        oldX = startX;
        oldY = startY;
        oldZ = startZ;

        World world = player.getWorld();
        oldWorld = world;

        for (int x = 0; x < newLayer.length; x++) {
            for (int z = 0; z < newLayer[x].length; z++) {
                oldLayerLocal[x][z] = world.getBlockData(x+startX,startY,z+startZ);
                world.getBlockAt(x+startX,startY,z+startZ).setType(Material.STONE);
            }
        }
        oldLayer = oldLayerLocal;

        oldLocation = startLocal;
    }

    @EventHandler
    public void playerClick(PlayerInteractEvent event) {
        if (!isStartBuild) return;
        if (event.getAction() == Action.LEFT_CLICK_BLOCK) {
            event.setCancelled(true);
            makeMarking(event.getClickedBlock().getLocation());
        }
    }

    @EventHandler
    public void playerleave(PlayerQuitEvent event) {
        if (event.getPlayer().getUniqueId() == player.getUniqueId()) {
            isStartBuild = false;
            if (oldLayer != null) {
                for (int x = 0; x < oldLayer.length; x++) {
                    for (int z = 0; z < oldLayer[x].length; z++) {
                        oldWorld.getBlockAt(x+oldX,oldY,z+oldZ).setType(oldLayer[x][z].getMaterial());
                    }
                }
            }
            manager.buildMenus.remove(this);
        }
    }

    public void acceptBuild() {
        player.sendMessage("Начала стройки");
        if (oldLocation == null) return;
        if (!isStartBuild) return;
        isStartBuild = false;
        manager.makeBuild(oldLocation,player,buildName);
        player.sendMessage("ну все окей");
        manager.buildMenus.remove(this);
    }

    public void cancelBuild() {
        player.sendMessage("Отмена");
        isStartBuild = false;
        if (oldLayer != null) {
            for (int x = 0; x < oldLayer.length; x++) {
                for (int z = 0; z < oldLayer[x].length; z++) {
                    oldWorld.getBlockAt(x+oldX,oldY,z+oldZ).setType(oldLayer[x][z].getMaterial());
                }
            }
        }
        player.sendMessage("ну все окей");
        manager.buildMenus.remove(this);
    }
}
