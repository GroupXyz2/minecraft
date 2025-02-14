package de.groupxyz.maintenance;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerLoginEvent;
import org.bukkit.plugin.java.JavaPlugin;

public class Maintenance extends JavaPlugin implements Listener {

    private boolean maintenanceMode = false;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        maintenanceMode = getConfig().getBoolean("maintenanceMode", false);

        Bukkit.getPluginManager().registerEvents(this, this);

        getLogger().info("Maintenance von GroupXyz aktiviert!");
    }

    @Override
    public void onDisable() {
        getConfig().set("maintenanceMode", maintenanceMode);
        saveConfig();
        getLogger().info("Maintenance deaktiviert!");
    }

    @EventHandler
    public void onPlayerLogin(PlayerLoginEvent event) {
        Player player = event.getPlayer();
        if (maintenanceMode && !player.isOp() && !player.hasPermission("maintenance.bypass")) {
            event.disallow(PlayerLoginEvent.Result.KICK_OTHER, ChatColor.RED +
                    "Der Server ist derzeit im Wartungsmodus.\nBitte spaeter erneut versuchen.");
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (command.getName().equalsIgnoreCase("maintenance")) {
            if (!sender.isOp() && !sender.hasPermission("maintenance.change")) {
                sender.sendMessage(ChatColor.RED + "Du hast keine Berechtigung, diesen Befehl auszufuehren.");
                return true;
            }

            maintenanceMode = !maintenanceMode;
            getConfig().set("maintenanceMode", maintenanceMode);
            saveConfig();

            String status = maintenanceMode ? "aktiviert" : "deaktiviert";
            Bukkit.broadcastMessage(ChatColor.YELLOW + "Wartungsmodus wurde " + status + ".");
            return true;
        }
        return false;
    }
}
