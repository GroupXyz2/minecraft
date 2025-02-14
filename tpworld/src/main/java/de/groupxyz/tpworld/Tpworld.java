package de.groupxyz.tpworld;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.world.WorldLoadEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public final class Tpworld extends JavaPlugin {

    @Override
    public void onEnable() {
        getCommand("tpworld").setExecutor(new TpWorldCommand());
        getCommand("worldcustom").setExecutor(new WorldCustomCommand());
        getLogger().info("tpworld by GroupXyz loaded successfully!");
        getLogger().info("Sup? If you encounter any errors, contact me on Discord: groupxyz");

        checkUnloadedWorlds();
    }

    @Override
    public void onDisable() {
        getLogger().info("tpworld by GroupXyz disabled! If this happened due to an Error please contact me on Discord: groupxyz");
    }

    class TpWorldCommand implements CommandExecutor {

        @Override
        public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
            if (cmd.getName().equalsIgnoreCase("tpworld")) {
                if (!(sender instanceof Player)) {
                    sender.sendMessage("This command can only be used by players.");
                    return true;
                }

                Player player = (Player) sender;

                if (args.length == 0) {
                    List<String> worldNames = new ArrayList<>();
                    for (World world : Bukkit.getWorlds()) {
                        worldNames.add(world.getName());
                    }
                    sender.sendMessage("Available worlds: " + String.join(", ", worldNames));
                    return true;
                }

                if (args.length == 1) {
                    String worldName = args[0];
                    World world = Bukkit.getWorld(worldName);

                    List<String> unloadedWorlds = getUnloadedWorlds();

                    if (world == null) {
                        sender.sendMessage("World '" + worldName + "' does not exist." + " However, there are maybe some unloaded worlds: " + String.join(", ", unloadedWorlds));
                        return true;
                    }

                    player.teleport(world.getSpawnLocation());
                    sender.sendMessage("Teleported to world '" + worldName + "'.");
                }
            }

            return false;
        }

        public List<String> onTabComplete(CommandSender sender, Command cmd, String alias, String[] args) {
            List<String> completions = new ArrayList<>();

            if (cmd.getName().equalsIgnoreCase("tpworld")) {
                if (args.length == 1) {
                    List<String> worldNames = new ArrayList<>();
                    for (World world : Bukkit.getWorlds()) {
                        worldNames.add(world.getName());
                    }

                    for (String worldName : worldNames) {
                        if (worldName.startsWith(args[0])) {
                            completions.add(worldName);
                        }
                    }
                }
            }

            return completions;
        }
    }

    class WorldCustomCommand implements CommandExecutor {

        @Override
        public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
            if (cmd.getName().equalsIgnoreCase("worldcustom")) {
                if (!(sender instanceof Player)) {
                    sender.sendMessage("This command can only be used by players.");
                    return true;
                }

                loadCustomWorlds(sender);
                return true;
            }

            return false;
        }
    }

    @EventHandler
    public void onWorldLoad(WorldLoadEvent event) {
        checkUnloadedWorlds();
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        checkUnloadedWorlds();
    }

    private void checkUnloadedWorlds() {
        List<String> unloadedWorlds = getUnloadedWorlds();
        if (!unloadedWorlds.isEmpty()) {
            getLogger().warning("The following worlds are in the server folder but not loaded yet: " + String.join(", ", unloadedWorlds));
        }
    }

    private List<String> getUnloadedWorlds() {
        List<String> unloadedWorlds = new ArrayList<>();
        File worldFolder = new File("container");

        if (worldFolder.exists() && worldFolder.isDirectory()) {
            File[] files = worldFolder.listFiles();
            if (files != null) {
                for (File file : files) {
                    if (file.isDirectory()) {
                        String worldName = file.getName();
                        World world = Bukkit.getWorld(worldName);
                        if (world == null) {
                            unloadedWorlds.add(worldName);
                        }
                    }
                }
            }
        }

        return unloadedWorlds;
    }

    private void loadCustomWorlds(CommandSender sender) {
        File serverFolder = new File("container");

        if (serverFolder.exists() && serverFolder.isDirectory()) {
            File[] files = serverFolder.listFiles();
            if (files != null) {
                int loadedCount = 0;
                List<String> loadedWorldNames = new ArrayList<>();
                for (File file : files) {
                    if (file.isDirectory()) {
                        String worldName = file.getName();
                        World world = Bukkit.getWorld(worldName);
                        if (world == null) {
                            WorldCreator worldCreator = new WorldCreator(worldName);
                            world = Bukkit.createWorld(worldCreator);
                            if (world != null) {
                                loadedCount++;
                                loadedWorldNames.add(worldName);
                            }
                        }
                    }
                }
                if (loadedCount > 0) {
                    sender.sendMessage(loadedCount + " custom worlds loaded: " + String.join(", ", loadedWorldNames));
                } else {
                    sender.sendMessage("No custom (unloaded) worlds found in the server folder.");
                }
            }
        }
    }
}














