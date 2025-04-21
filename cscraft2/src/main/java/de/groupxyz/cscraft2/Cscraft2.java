package de.groupxyz.cscraft2;

import org.bukkit.plugin.java.JavaPlugin;

import java.util.Objects;

public final class Cscraft2 extends JavaPlugin {
    private static Cscraft2 instance;
    private WeaponSelect weaponSelect;
    private TeamManager teamManager;
    private GameManager gameManager;
    private PlayerStats playerStats;

    @Override
    public void onEnable() {
        instance = this;
        getLogger().info("CSCraft2 by GroupXyz starting.");

        GameConfig gameConfig = new GameConfig(this);

        teamManager = new TeamManager(this);

        weaponSelect = new WeaponSelect();
        getServer().getPluginManager().registerEvents(new WeaponInventoryListener(weaponSelect), this);

        gameManager = new GameManager(this, gameConfig, weaponSelect, teamManager);

        playerStats = new PlayerStats(this);
        playerStats.loadStats();
        playerStats.enableAutoSave();

        getServer().getPluginManager().registerEvents(
                new PlayerMove(gameManager), this);

        Objects.requireNonNull(getCommand("cs")).setExecutor(new CommandHandler(gameManager));
        Objects.requireNonNull(getCommand("cs")).setTabCompleter(new TabCompleter());
    }

    public WeaponSelect getWeaponSelect() {
        return weaponSelect;
    }

    public TeamManager getTeamManager() {return teamManager;}

    public GameManager getGameManager() {return gameManager;}

    public PlayerStats getPlayerStats() {return playerStats;}

    public Boolean isLogDebug() {
        return false;
    }

    @Override
    public void onDisable() {
        getLogger().info("CSCraft2 stopping.");
    }

    public static Cscraft2 getInstance() {
        return instance;
    }
}