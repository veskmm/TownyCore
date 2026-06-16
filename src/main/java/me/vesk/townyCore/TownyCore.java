package me.vesk.townyCore;

import co.aikar.commands.PaperCommandManager;
import org.bukkit.plugin.java.JavaPlugin;

public final class TownyCore extends JavaPlugin {

    private Manager manager;
    private PaperCommandManager commandManager;
    private ConfigManager configManager;
    private TownsConfig townsConfig;
    private BuildsConfig buildsConfig;


    @Override
    public void onEnable() {
        this.configManager = new ConfigManager(this);
        this.townsConfig = new TownsConfig(this);

        this.buildsConfig = new BuildsConfig(this);
        this.manager = new Manager(this,configManager,buildsConfig,townsConfig);

        commandManager = new PaperCommandManager(this);
        commandManager.registerCommand(new Commands(this, configManager,townsConfig,manager,buildsConfig,commandManager));

        saveDefaultConfig();

        getServer().getPluginManager().registerEvents(new TownyListener(this,manager,configManager,townsConfig), this);
    }

    @Override
    public void onDisable() {
        // Plugin shutdown logic
    }
}
