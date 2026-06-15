package me.vesk.townyCore;

import com.github.stefvanschie.inventoryframework.gui.GuiItem;
import com.github.stefvanschie.inventoryframework.gui.type.ChestGui;
import com.github.stefvanschie.inventoryframework.gui.type.util.Gui;
import com.github.stefvanschie.inventoryframework.pane.StaticPane;
import com.github.stefvanschie.inventoryframework.pane.util.Slot;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

public class BuildMenu {

    private final ChestGui gui;
    private final Player player;
    private final BuildsConfig buildsConfig;
    private final Manager manager;

    private int[] posBuilds = {20,21,22,23};

    public BuildMenu(Player player, BuildsConfig buildsConfig, Manager manager) {
        this.buildsConfig = buildsConfig;
        this.player = player;
        this.manager = manager;
        this.gui = new ChestGui(6, "Городские здания");

        guiSetup();
    }

    private void guiSetup() {

        // Background
        StaticPane background = new StaticPane(0, 0, 9, 6);

        ItemStack filler = new ItemStack(Material.BLACK_STAINED_GLASS_PANE);
        ItemMeta meta = filler.getItemMeta();
        meta.setDisplayName(" ");
        filler.setItemMeta(meta);

        for (int i = 0; i <= 8; i++) {
            for (int x = 0; x <= 8; x++) {
                //background.addItem(new GuiItem(filler, event -> {event.setCancelled(true);}), x, i);
            }
        }

        //
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
                manager.startBuild(player, builds.get(index));
                event.setCancelled(true);
            }
            ),ind,0);
        }

        gui.addPane(buildsBlock);
        gui.addPane(background);
    }

    private void setup() {

        // ============ Функциональные кнопки ============
        StaticPane FuncItems = new StaticPane(0, 5, 1, 1);
        ItemStack buttonAccept = new ItemStack(Material.GREEN_STAINED_GLASS_PANE);
        ItemMeta acceptMeta = buttonAccept.getItemMeta();
        acceptMeta.setDisplayName(ChatColor.RESET + "" + ChatColor.GREEN + "ПРИНЯТЬ ПРЕДЛОЖЕНИЕ");
        buttonAccept.setItemMeta(acceptMeta);
        FuncItems.addItem(new GuiItem(buttonAccept, event -> {
            event.setCancelled(true);
        }), 0, 0);
        gui.addPane(FuncItems);


        // ============ Добавляем фон (должен быть последним, чтобы не перекрывать другие элементы) ============

    }

    public void openMenu() {
        gui.show(player);
    }
}
