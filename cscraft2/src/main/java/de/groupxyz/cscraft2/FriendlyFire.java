package de.groupxyz.cscraft2;

import org.bukkit.ChatColor;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class FriendlyFire implements Listener {
    private final Cscraft2 plugin;
    private final TeamManager teamManager;
    private boolean enabled;
    private int maxTeamDamageCount;
    private final Map<String, Integer> teamDamageCount = new HashMap<>();
    private final File configFile;
    private FileConfiguration config;

    public FriendlyFire(Cscraft2 plugin, TeamManager teamManager) {
        this.plugin = plugin;
        this.teamManager = teamManager;
        this.configFile = new File(plugin.getDataFolder(), "friendly_fire.yml");
        loadConfig();
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    private void loadConfig() {
        if (!configFile.exists()) {
            createDefaultConfig();
        }
        config = YamlConfiguration.loadConfiguration(configFile);
        enabled = config.getBoolean("enabled", false);
        maxTeamDamageCount = config.getInt("max_team_damage_count", 3);
    }

    private void createDefaultConfig() {
        config = new YamlConfiguration();
        config.set("enabled", false);
        config.set("max_team_damage_count", 3);
        saveConfig();
    }

    private void saveConfig() {
        try {
            config.save(configFile);
        } catch (IOException e) {
            plugin.getLogger().severe("Konnte FriendlyFire-Konfiguration nicht speichern: " + e.getMessage());
        }
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
        config.set("enabled", enabled);
        saveConfig();
    }

    public int getMaxTeamDamageCount() {
        return maxTeamDamageCount;
    }

    public void setMaxTeamDamageCount(int maxTeamDamageCount) {
        this.maxTeamDamageCount = maxTeamDamageCount;
        config.set("max_team_damage_count", maxTeamDamageCount);
        saveConfig();
    }

    public void resetDamageCounts() {
        teamDamageCount.clear();
    }

    @EventHandler
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player damager) || !(event.getEntity() instanceof Player victim)) {
            return;
        }

        if (GameStates.CURRENT_STATE != GameStates.PLAYING && !damager.isOp()) {
            event.setCancelled(true);
            return;
        } else if (GameStates.CURRENT_STATE != GameStates.PLAYING && damager.isOp()) {
            return;
        }

        String damagerTeam = teamManager.getPlayerTeam(damager);
        String victimTeam = teamManager.getPlayerTeam(victim);

        if (damagerTeam == null || victimTeam == null) {
            return;
        }

        if (damagerTeam.equals(victimTeam)) {
            if (!enabled) {
                event.setCancelled(true);
                damager.sendMessage(ChatColor.RED + "Friendly Fire ist deaktiviert!");
            } else {
                int count = teamDamageCount.getOrDefault(damager.getName(), 0) + 1;
                teamDamageCount.put(damager.getName(), count);

                damager.sendMessage(ChatColor.RED + "Achtung! Du hast ein Teammitglied verletzt! ("
                        + count + "/" + maxTeamDamageCount + ")");

                if (count >= maxTeamDamageCount) {
                    kickPlayer(damager);
                }
            }
        }
    }

    private void kickPlayer(Player player) {
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            plugin.getGameManager().leaveGame(player);
            player.sendMessage(ChatColor.RED + "Du wurdest aus dem Spiel entfernt wegen zu vielem Teamschaden!");

            for (Player gamePlayer : plugin.getGameManager().getPlayers()) {
                gamePlayer.sendMessage(ChatColor.RED + player.getName() +
                        " wurde wegen zu vielem Teamschaden aus dem Spiel entfernt!");
            }

            teamDamageCount.remove(player.getName());
        });
    }
}