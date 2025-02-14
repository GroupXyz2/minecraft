package de.groupxyz.dashability;

import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.plugin.PluginManager;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.util.*;

public class Dashability extends JavaPlugin implements Listener {

    private FileConfiguration playerConfig;
    private FileConfiguration whitelistConfig;
    private Set<String> whitelistedWorlds;
    private FileConfiguration cooldownConfig;
    private Set<String> cooldownTime;

    private int secondscooldown = 30;

    @Override
    public void onEnable() {
        playerConfig = YamlConfiguration.loadConfiguration(getPlayerConfigFile());
        whitelistConfig = YamlConfiguration.loadConfiguration(getWhitelistConfigFile());
        whitelistedWorlds = new HashSet<>(whitelistConfig.getStringList("whitelistedWorlds"));
        cooldownConfig = YamlConfiguration.loadConfiguration(getCooldownConfigFile());
        cooldownTime = new HashSet<>(cooldownConfig.getStringList("cooldownTime"));
        secondscooldown = cooldownConfig.getInt("secondscooldown", 30);

        PluginManager pm = getServer().getPluginManager();
        pm.registerEvents(this, this);
        getServer().getPluginManager().registerEvents(new FallDamadgeListener(), this);

        getLogger().info("Dashabilitys by GroupXyz loaded succesfully!");
    }

    @Override
    public void onDisable() {
        getLogger().info("Dashabilitys by GroupXyz disabled, if this is because of an Error please report to GroupXyz!");
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        Action action = event.getAction();
        int abilityCooldownMillis = secondscooldown * 1000;

        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) {
            return;
        }

        if (!whitelistedWorlds.contains(player.getWorld().getName()) || !isHoldingSword(player)) {
            return;
        }

        if (playerConfig.getBoolean(player.getUniqueId().toString() + ".enabled", false)) {
            long lastUsed = playerConfig.getLong(player.getUniqueId().toString() + ".lastUsed", 0);
            if (System.currentTimeMillis() - lastUsed >= abilityCooldownMillis) {
                DashAbilityUse.use(player, secondscooldown);
                playerConfig.set(player.getUniqueId().toString() + ".lastUsed", System.currentTimeMillis());
                playerConfig.set(player.getUniqueId().toString() + ".playerName", player.getName());
                savePlayerConfig();
            }
        }
    }

    public void setCooldownSeconds(int seconds) {
        secondscooldown = seconds;
    }

    private boolean isHoldingSword(Player player) {
        return player.getInventory().getItemInMainHand().getType().toString().toLowerCase().contains("sword");
    }


    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();

        if (!playerConfig.contains(player.getUniqueId().toString())) {
            playerConfig.set(player.getUniqueId().toString() + ".enabled", true);
            savePlayerConfig();
        }
    }

    private void savePlayerConfig() {
        try {
            playerConfig.save(getPlayerConfigFile());
        } catch (IOException e) {
            getLogger().warning("This is a Failure, Player Config could not be saved - Please Report to GroupXyz");
            e.printStackTrace();
        }
    }

    private File getPlayerConfigFile() {
        return new File(getDataFolder(), "players.yml");
    }

    private File getWhitelistConfigFile() {
        return new File(getDataFolder(), "whitelist.yml");
    }

    private File getCooldownConfigFile() {
        return new File(getDataFolder(), "cooldown.yml");
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, String[] args) {
        if (sender instanceof Player player) {

            if (command.getName().equalsIgnoreCase("deactivateability")) {
                playerConfig.set(player.getUniqueId().toString() + ".enabled", false);
                savePlayerConfig();
                player.sendMessage("Ability deactivated!");
                return true;
            } else if (command.getName().equalsIgnoreCase("reactivateability")) {
                playerConfig.set(player.getUniqueId().toString() + ".enabled", true);
                savePlayerConfig();
                player.sendMessage("Ability reactivated!");
                return true;
            } else if (command.getName().equalsIgnoreCase("abilitywhitelist")) {
                if (args.length > 0) {
                    String worldName = args[0];
                    whitelistedWorlds.add(worldName);
                    whitelistConfig.set("whitelistedWorlds", new ArrayList<>(whitelistedWorlds));
                    saveWhitelistConfig();
                    player.sendMessage("Added world '" + worldName + "' to the ability whitelist.");
                    return true;
                } else {
                    player.sendMessage("Usage: /abilitywhitelist [worldname]");
                }
            } else if (command.getName().equalsIgnoreCase("removeabilitywhitelist")) {
                if (args.length > 0) {
                    String worldName = args[0];
                    whitelistedWorlds.remove(worldName);
                    whitelistConfig.set("whitelistedWorlds", new ArrayList<>(whitelistedWorlds));
                    saveWhitelistConfig();
                    player.sendMessage("Removed world '" + worldName + "' from the ability whitelist.");
                    return true;
                } else {
                    player.sendMessage("Usage: /removeabilitywhitelist [worldname]");
                }
            } else if (command.getName().equalsIgnoreCase("setabilitycooldown")) {
                if (args.length > 0) {
                    try {
                        int cooldownSeconds = Integer.parseInt(args[0]);
                        if (cooldownSeconds > 0) {
                            setCooldownSeconds(cooldownSeconds);
                            cooldownConfig.set("secondscooldown", cooldownSeconds);
                            cooldownConfig.set("cooldownTime", new ArrayList<>(cooldownTime));
                            saveCooldownConfig();
                            player.sendMessage("Ability cooldown set to " + cooldownSeconds + " seconds.");
                        } else {
                            player.sendMessage("Cooldown must be a positive integer.");
                        }
                    } catch (NumberFormatException e) {
                        player.sendMessage("Invalid cooldown value.");
                    }
                    return true;
                } else {
                    player.sendMessage("Usage: /setabilitycooldown [cooldown_seconds]");
                }
            }
        }

        return false;
    }

    public class DashAbilityUse {
        public static void use(Player player, int secondscooldown) {
            Vector dashVector = player.getLocation().getDirection().normalize().multiply(5.0D);
            player.setVelocity(dashVector);
            player.playSound(player.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 1.0F, 1.0F);
            playParticle(player, Particle.SMOKE_LARGE);

            int tickDelay = secondscooldown * 20;

            new BukkitRunnable() {
                @Override
                public void run() {
                    showAbilityReadyPopup(player);
                }
            }.runTaskLater(JavaPlugin.getPlugin(Dashability.class), tickDelay);

            player.addScoreboardTag("dashCooldown");

            BukkitTask task = new BukkitRunnable() {
                @Override
                public void run() {
                    player.removeScoreboardTag("dashCooldown");
                }
            }.runTaskLater(JavaPlugin.getPlugin(Dashability.class), 100);
        }

        private static void playParticle(Player player, Particle particle) {
            Location playerLocation = player.getLocation();
            player.getWorld().spawnParticle(particle, playerLocation, 50, 0.5D, 0.5D, 0.5D, 0.1D);
        }

        private static void showAbilityReadyPopup(Player player) {
            player.getWorld().spawnParticle(Particle.END_ROD, player.getLocation().add(0, 2, 0), 30, 0.5, 0.5, 0.5, 0.1);
        }
    }

    public class FallDamadgeListener implements Listener {

        @EventHandler
        public void onEntityDamage(EntityDamageEvent event) {
            if (event.getEntity() instanceof Player && event.getCause() == EntityDamageEvent.DamageCause.FALL) {
                Player player = (Player) event.getEntity();

                if (player.getScoreboardTags().contains("dashCooldown")) {
                    event.setCancelled(true);
                }
            }
        }
    }

    private void saveWhitelistConfig() {
        try {
            whitelistConfig.save(getWhitelistConfigFile());
        } catch (IOException e) {
            getLogger().warning("This is a Failure, Whitelist Config could not be saved - Please Report to GroupXyz");
            e.printStackTrace();
        }
    }

    private void saveCooldownConfig() {
        try {
            cooldownConfig.save(getCooldownConfigFile());
        } catch (IOException e) {
            getLogger().warning("This is a Failure, Cooldown Config could not be saved - Please Report to GroupXyz");
            e.printStackTrace();
        }
    }
}

//TODO:

//playername in yml config   | Implemented - functional
//dashability no falldamadge | Implemented - functional
//leftclick works (disable)  | Implemented - functional
//particle 30sec not working | Implemented - functional
//bukkit utils multiversion  | make if release on bukkit
//other particels when ready | make poll on server
//nerv hight (?)             | make poll on server

