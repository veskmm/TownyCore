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
        if (getServer().getPluginManager().getPlugin("Towny") == null) {
            getLogger().severe("Towny не найден. TownyCore будет отключён.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        saveDefaultConfig();

        configManager = new ConfigManager(this);
        townsConfig = new TownsConfig(this);
        buildsConfig = new BuildsConfig(this);
        manager = new Manager(this, configManager, buildsConfig, townsConfig);

        commandManager = new PaperCommandManager(this);
        commandManager.registerCommand(new Commands(
                this,
                configManager,
                townsConfig,
                manager,
                buildsConfig,
                commandManager
        ));

        getServer().getPluginManager().registerEvents(
                new TownyListener(this, manager, configManager, townsConfig),
                this
        );
    }

    @Override
    public void onDisable() {
        if (manager != null) {
            manager.shutdownBuildMenus();
        }
    }
}
