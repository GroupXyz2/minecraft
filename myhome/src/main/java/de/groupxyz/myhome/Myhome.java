package de.groupxyz.myhome;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class Myhome extends JavaPlugin implements Listener {

    private FileConfiguration config;
    private final Map<UUID, Long> cooldowns = new HashMap<>();
    private final Map<UUID, TeleportTask> pendingTeleports = new HashMap<>();

    @Override
    public void onEnable() {
        saveDefaultConfig();
        config = getConfig();

        getCommand("sethome").setExecutor(new SetHomeCommand());
        getCommand("home").setExecutor(new HomeCommand());
        getCommand("delhome").setExecutor(new DelHomeCommand());
        getCommand("listhomes").setExecutor(new ListHomesCommand());

        getServer().getPluginManager().registerEvents(this, this);

        getLogger().info("myhome by GroupXyz starting.");
    }

    @Override
    public void onDisable() {
        saveConfig();
        getLogger().info("myhome shutting down.");
    }

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        if (pendingTeleports.containsKey(event.getPlayer().getUniqueId())) {
            Location from = event.getFrom();
            Location to = event.getTo();

            if (from.getBlockX() != to.getBlockX() ||
                    from.getBlockY() != to.getBlockY() ||
                    from.getBlockZ() != to.getBlockZ()) {

                UUID playerId = event.getPlayer().getUniqueId();
                pendingTeleports.get(playerId).cancel();
                pendingTeleports.remove(playerId);
                event.getPlayer().sendMessage(ChatColor.RED + "Teleport abgebrochen, du darfst dich nicht bewegen!");
            }
        }
    }

    private class SetHomeCommand implements CommandExecutor {
        @Override
        public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
            if (!(sender instanceof Player)) {
                sender.sendMessage(ChatColor.RED + "Dieser Befehl kann nur von Spielern verwendet werden!");
                return true;
            }

            Player player = (Player) sender;

            if (args.length < 1) {
                player.sendMessage(ChatColor.RED + "Bitte gib einen Namen an!");
                return false;
            }

            String homeName = args[0].toLowerCase();
            String playerUUID = player.getUniqueId().toString();
            String path = "players." + playerUUID + ".homes";

            if (config.contains(path + "." + homeName)) {
                updateHomeLocation(player, homeName);
                return true;
            }

            ConfigurationSection homesSection = config.getConfigurationSection(path);
            int maxHomes = config.getInt("max-homes", 3);
            if (homesSection != null && homesSection.getKeys(false).size() >= maxHomes) {
                player.sendMessage(ChatColor.RED + "Du kannst maximal " + maxHomes + " Homes setzen!");
                return true;
            }

            updateHomeLocation(player, homeName);
            return true;
        }

        private void updateHomeLocation(Player player, String homeName) {
            Location location = player.getLocation();
            String playerUUID = player.getUniqueId().toString();
            String path = "players." + playerUUID + ".homes." + homeName;

            config.set(path + ".world", location.getWorld().getName());
            config.set(path + ".x", location.getX());
            config.set(path + ".y", location.getY());
            config.set(path + ".z", location.getZ());
            config.set(path + ".yaw", location.getYaw());
            config.set(path + ".pitch", location.getPitch());
            saveConfig();

            player.sendMessage(ChatColor.GREEN + "Home '" + homeName + "' erstellt!");
        }
    }

    private class HomeCommand implements CommandExecutor {
        @Override
        public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
            if (!(sender instanceof Player)) {
                sender.sendMessage(ChatColor.RED + "Dieser Befehl kann nur von Spielern verwendet werden!");
                return true;
            }

            Player player = (Player) sender;
            UUID playerId = player.getUniqueId();

            if (args.length < 1) {
                player.sendMessage(ChatColor.RED + "Bitte gib einen Namen an!");
                return false;
            }

            String homeName = args[0].toLowerCase();
            String playerUUID = playerId.toString();
            String path = "players." + playerUUID + ".homes." + homeName;

            if (!config.contains(path)) {
                player.sendMessage(ChatColor.RED + "Home '" + homeName + "' existiert nicht!");
                return true;
            }

            if (cooldowns.containsKey(playerId)) {
                long timeLeft = ((cooldowns.get(playerId) / 1000) + 60) - (System.currentTimeMillis() / 1000);
                if (timeLeft > 0) {
                    player.sendMessage(ChatColor.RED + "Du musst noch " + timeLeft + " Sekunden warten!");
                    return true;
                }
            }

            if (pendingTeleports.containsKey(playerId)) {
                pendingTeleports.get(playerId).cancel();
                pendingTeleports.remove(playerId);
            }

            player.sendMessage(ChatColor.YELLOW + "Teleportiere in 3 Sekunden. Nicht bewegen!");

            TeleportTask task = new TeleportTask(player, homeName);
            pendingTeleports.put(playerId, task);
            task.runTaskLater(Myhome.this, 3 * 20);

            return true;
        }
    }

    private class TeleportTask implements Runnable {
        private final Player player;
        private final String homeName;
        private int taskId;

        public TeleportTask(Player player, String homeName) {
            this.player = player;
            this.homeName = homeName;
        }

        @Override
        public void run() {
            UUID playerId = player.getUniqueId();
            String playerUUID = playerId.toString();
            String path = "players." + playerUUID + ".homes." + homeName;

            String worldName = config.getString(path + ".world");
            double x = config.getDouble(path + ".x");
            double y = config.getDouble(path + ".y");
            double z = config.getDouble(path + ".z");
            float yaw = (float) config.getDouble(path + ".yaw");
            float pitch = (float) config.getDouble(path + ".pitch");

            Location location = new Location(Bukkit.getWorld(worldName), x, y, z, yaw, pitch);
            player.teleport(location);
            player.sendMessage(ChatColor.GREEN + "Zum Home '" + homeName + "' teleportiert!");

            cooldowns.put(playerId, System.currentTimeMillis());
            pendingTeleports.remove(playerId);
        }

        public void runTaskLater(Myhome plugin, long delay) {
            taskId = Bukkit.getScheduler().scheduleSyncDelayedTask(plugin, this, delay);
        }

        public void cancel() {
            Bukkit.getScheduler().cancelTask(taskId);
        }
    }

    private class DelHomeCommand implements CommandExecutor {
        @Override
        public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
            if (!(sender instanceof Player)) {
                sender.sendMessage(ChatColor.RED + "Dieser Befehl kann nur von Spielern verwendet werden!");
                return true;
            }

            Player player = (Player) sender;

            if (args.length < 1) {
                player.sendMessage(ChatColor.RED + "Bitte gib einen Namen an!");
                return false;
            }

            String homeName = args[0].toLowerCase();
            String playerUUID = player.getUniqueId().toString();
            String path = "players." + playerUUID + ".homes." + homeName;

            if (!config.contains(path)) {
                player.sendMessage(ChatColor.RED + "Home '" + homeName + "' existiert nicht!");
                return true;
            }

            config.set(path, null);
            saveConfig();

            player.sendMessage(ChatColor.GREEN + "Home '" + homeName + "' wurde gelöscht!");
            return true;
        }
    }

    private class ListHomesCommand implements CommandExecutor {
        @Override
        public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
            if (!(sender instanceof Player)) {
                sender.sendMessage(ChatColor.RED + "Dieser Befehl kann nur von Spielern verwendet werden!");
                return true;
            }

            Player player = (Player) sender;
            String playerUUID = player.getUniqueId().toString();
            String path = "players." + playerUUID + ".homes";

            if (!config.contains(path)) {
                player.sendMessage(ChatColor.YELLOW + "Du besitzt keine Homes!");
                return true;
            }

            ConfigurationSection homesSection = config.getConfigurationSection(path);
            if (homesSection == null || homesSection.getKeys(false).isEmpty()) {
                player.sendMessage(ChatColor.YELLOW + "Du besitzt keine Homes!");
                return true;
            }

            player.sendMessage(ChatColor.GREEN + "Deine Homes:");
            for (String homeName : homesSection.getKeys(false)) {
                player.sendMessage(ChatColor.GRAY + "- " + ChatColor.WHITE + homeName);
            }

            return true;
        }
    }
}
