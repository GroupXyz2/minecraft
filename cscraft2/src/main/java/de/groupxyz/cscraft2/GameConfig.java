package de.groupxyz.cscraft2;

import org.bukkit.Location;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.Objects;

public class GameConfig {
    private final JavaPlugin plugin;
    private File configFile;
    private FileConfiguration config;

    private int countdownTime = 30;
    private int bombPlacementTime = 90;
    private int minPlayers = 2;
    private int maxPlayers = 10;
    private Location ctSpawn;
    private Location tSpawn;
    private Location lobby_spawn;

    private boolean isRoundBased = true;
    private int maxRounds = 30;
    private int moneyPerRound = 800;
    private int moneyPerKill = 300;
    private int moneyPerPlant = 300;
    private int moneyPerDefuse = 300;
    private int winnerBonus = 3000;
    private int loserBonus = 1400;

    public GameConfig(JavaPlugin plugin) {
        this.plugin = plugin;
        if (plugin != null) {
            loadConfig();
        } else {
            System.err.println("Plugin is null at GameConfig init!");
        }
    }

    private void loadConfig() {
        try {
            if (!plugin.getDataFolder().exists()) {
                plugin.getDataFolder().mkdir();
            }

            configFile = new File(plugin.getDataFolder(), "cscraft_config.yml");

            if (!configFile.exists()) {
                configFile.createNewFile();
                config = new YamlConfiguration();
                setDefaults();
                saveConfig();
                plugin.getLogger().info("New config created.");
            } else {
                config = YamlConfiguration.loadConfiguration(configFile);

                loadSettings();
                plugin.getLogger().info("Config loaded. CT Spawn: " +
                        (ctSpawn != null ? ctSpawn.getWorld().getName() + "," + ctSpawn.getX() + "," + ctSpawn.getY() + "," + ctSpawn.getZ() : "null"));
            }
        } catch (IOException e) {
            plugin.getLogger().severe("Error loading config: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void setDefaults() {
        config.addDefault("game.countdown", 30);
        config.addDefault("game.bomb_placement_time", 90);
        config.addDefault("game.min_players", 2);
        config.addDefault("game.max_players", 10);

        config.addDefault("game.round_based", true);
        config.addDefault("game.max_rounds", 30);
        config.addDefault("economy.money_per_round", 800);
        config.addDefault("economy.money_per_kill", 300);
        config.addDefault("economy.money_per_plant", 300);
        config.addDefault("economy.money_per_defuse", 300);
        config.addDefault("economy.winner_bonus", 3000);
        config.addDefault("economy.loser_bonus", 1400);

        config.options().copyDefaults(true);
        saveConfig();
    }

    private void loadSettings() {
        countdownTime = config.getInt("game.countdown");
        bombPlacementTime = config.getInt("game.bomb_placement_time");
        minPlayers = config.getInt("game.min_players");
        maxPlayers = config.getInt("game.max_players");
        ctSpawn = deserializeLocation("game.ct_spawn");
        tSpawn = deserializeLocation("game.t_spawn");
        lobby_spawn = deserializeLocation("lobby.lobby_spawn");

        isRoundBased = config.getBoolean("game.round_based", true);
        maxRounds = config.getInt("game.max_rounds", 30);
        moneyPerRound = config.getInt("economy.money_per_round", 800);
        moneyPerKill = config.getInt("economy.money_per_kill", 300);
        moneyPerPlant = config.getInt("economy.money_per_plant", 300);
        moneyPerDefuse = config.getInt("economy.money_per_defuse", 300);
        winnerBonus = config.getInt("economy.winner_bonus", 3000);
        loserBonus = config.getInt("economy.loser_bonus", 1400);
    }

    public void saveConfig() {
        try {
            config.save(configFile);
        } catch (IOException e) {
            plugin.getLogger().severe("Configuartion couldn't be saved.");
            e.printStackTrace();
        }
    }

    public FileConfiguration getConfig() {
        return config;
    }

    public int getCountdownTime() {
        if (countdownTime <= 0) {
            plugin.getLogger().warning("Ungültiger countdownTime-Wert: " + countdownTime + ", setze auf 30");
            countdownTime = 30;
            config.set("game.countdown", 30);
            saveConfig();
        }
        return countdownTime; }
    public int getBombPlacementTime() {
        if (bombPlacementTime <= 0) {
            plugin.getLogger().warning("Ungültiger bombPlacementTime-Wert: " + bombPlacementTime + ", setze auf 90");
            bombPlacementTime = 90;
            config.set("game.bomb_placement_time", 90);
            saveConfig();
        }
        return bombPlacementTime;
    }
    public int getMinPlayers() {
        if (minPlayers <= 0) {
            plugin.getLogger().warning("Ungültiger minPlayers-Wert: " + minPlayers + ", setze auf 2");
            minPlayers = 2;
            config.set("game.min_players", 2);
            saveConfig();
        }
        return minPlayers; }
    public int getMaxPlayers() {
        if (maxPlayers <= 0) {
            plugin.getLogger().warning("Ungültiger maxPlayers-Wert: " + maxPlayers + ", setze auf 10");
            maxPlayers = 10;
            config.set("game.max_players", 10);
            saveConfig();
        }
        return maxPlayers; }
    public Location getCtSpawn() {
        if (ctSpawn == null) {
            plugin.getLogger().warning("CT Spawn is null, returning default.");
            ctSpawn = new Location(plugin.getServer().getWorlds().get(0), 0, 0, 0);
        }
        return ctSpawn; }
    public Location getTSpawn() {
        if (tSpawn == null) {
            plugin.getLogger().warning("T Spawn is null, returning default.");
            tSpawn = new Location(plugin.getServer().getWorlds().get(0), 0, 0, 0);
        }
        return tSpawn; }
    public Location getLobbySpawn() { return lobby_spawn; }

    public void setCtSpawn(Location location) {
        this.ctSpawn = location;
        serializeLocation("game.ct_spawn", location);
        saveConfig();
    }

    public void setTSpawn(Location location) {
        this.tSpawn = location;
        serializeLocation("game.t_spawn", location);
        saveConfig();
    }

    public void setLobbySpawn(Location location) {
        this.lobby_spawn = location;
        serializeLocation("lobby.lobby_spawn", location);
        saveConfig();
    }

    public void setBombPlacementTime(int seconds) {
        this.bombPlacementTime = seconds;
        config.set("game.bomb_placement_time", seconds);
        saveConfig();
    }

    public void setCountdownTime(int seconds) {
        this.countdownTime = seconds;
        config.set("game.countdown", seconds);
        saveConfig();
    }

    public void setMinPlayers(int players) {
        this.minPlayers = players;
        config.set("game.min_players", players);
        saveConfig();
    }

    public void setMaxPlayers(int players) {
        this.maxPlayers = players;
        config.set("game.max_players", players);
        saveConfig();
    }

    private Location deserializeLocation(String path) {
        if (config.contains(path)) {
            return new Location(
                    plugin.getServer().getWorld(Objects.requireNonNull(config.getString(path + ".world"))),
                    config.getDouble(path + ".x"),
                    config.getDouble(path + ".y"),
                    config.getDouble(path + ".z"),
                    (float) config.getDouble(path + ".yaw"),
                    (float) config.getDouble(path + ".pitch")
            );
        }
        return null;
    }

    private void serializeLocation(String path, Location location) {
        if (location != null) {
            config.set(path + ".world", location.getWorld().getName());
            config.set(path + ".x", location.getX());
            config.set(path + ".y", location.getY());
            config.set(path + ".z", location.getZ());
            config.set(path + ".yaw", location.getYaw());
            config.set(path + ".pitch", location.getPitch());
        }
    }

    public boolean isRoundBased() {
        return isRoundBased;
    }

    public void setRoundBased(boolean roundBased) {
        this.isRoundBased = roundBased;
        config.set("game.round_based", roundBased);
        saveConfig();
    }

    public int getMaxRounds() {
        return maxRounds;
    }

    public void setMaxRounds(int maxRounds) {
        this.maxRounds = maxRounds;
        config.set("game.max_rounds", maxRounds);
        saveConfig();
    }

    public int getMoneyPerRound() {
        return moneyPerRound;
    }

    public void setMoneyPerRound(int moneyPerRound) {
        this.moneyPerRound = moneyPerRound;
        config.set("economy.money_per_round", moneyPerRound);
        saveConfig();
    }

    public int getMoneyPerKill() {
        return moneyPerKill;
    }

    public void setMoneyPerKill(int moneyPerKill) {
        this.moneyPerKill = moneyPerKill;
        config.set("economy.money_per_kill", moneyPerKill);
        saveConfig();
    }

    public int getMoneyPerPlant() {
        return moneyPerPlant;
    }

    public void setMoneyPerPlant(int moneyPerPlant) {
        this.moneyPerPlant = moneyPerPlant;
        config.set("economy.money_per_plant", moneyPerPlant);
        saveConfig();
    }

    public int getMoneyPerDefuse() {
        return moneyPerDefuse;
    }

    public void setMoneyPerDefuse(int moneyPerDefuse) {
        this.moneyPerDefuse = moneyPerDefuse;
        config.set("economy.money_per_defuse", moneyPerDefuse);
        saveConfig();
    }

    public int getWinnerBonus() {
        return winnerBonus;
    }

    public void setWinnerBonus(int bonus) {
        this.winnerBonus = bonus;
        config.set("economy.winner_bonus", bonus);
        saveConfig();
    }

    public int getLoserBonus() {
        return loserBonus;
    }

    public void setLoserBonus(int bonus) {
        this.loserBonus = bonus;
        config.set("economy.loser_bonus", bonus);
        saveConfig();
    }
}