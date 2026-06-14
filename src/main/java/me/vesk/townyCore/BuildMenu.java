package me.vesk.townyCore;

import com.github.stefvanschie.inventoryframework.gui.GuiItem;
import com.github.stefvanschie.inventoryframework.gui.type.ChestGui;
import com.github.stefvanschie.inventoryframework.gui.type.util.Gui;
import com.github.stefvanschie.inventoryframework.pane.StaticPane;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

public class BuildMenu {

    private final ChestGui gui;

    public BuildMenu() {
        this.gui = new ChestGui(6, "Городские здания");

        guiSetup();
    }

    private void guiSetup() {

    }

    private void setup() {
        // ============ Задний Фон ============
        StaticPane background = new StaticPane(0, 0, 9, 6);

        ItemStack filler = new ItemStack(Material.BLACK_STAINED_GLASS_PANE);
        ItemMeta meta = filler.getItemMeta();
        meta.setDisplayName(" ");
        filler.setItemMeta(meta);

        for (int x = 0; x <= 8; x++) {
            background.addItem(new GuiItem(filler, event -> {event.setCancelled(true);}), x, 0);
        }
        for (int x = 1; x <= 7; x++) {
            background.addItem(new GuiItem(filler, event -> {event.setCancelled(true);}), x, 5);
        }

        // ============ Линия разделения зон игроков ============
        ItemStack separator = new ItemStack(Material.BLACK_STAINED_GLASS_PANE);
        ItemMeta sepMeta = separator.getItemMeta();
        sepMeta.setDisplayName(" ");
        separator.setItemMeta(sepMeta);

        StaticPane separatorLine = new StaticPane(4, 0, 1, 6);
        for (int y = 0; y < 6; y++) {
            separatorLine.addItem(new GuiItem(separator, event -> {event.setCancelled(true);}), 0, y);
        }
        gui.addPane(separatorLine);

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
        gui.addPane(background);
    }
}
