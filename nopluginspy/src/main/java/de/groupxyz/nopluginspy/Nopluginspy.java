package de.groupxyz.nopluginspy;

import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.server.TabCompleteEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.*;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.ArrayList;
import java.util.List;

public final class Nopluginspy extends JavaPlugin implements Listener {

    private List<BlockedCommand> blockedCommands;

    @Override
    public void onEnable() {
        getLogger().info("nopluginspy by GroupXyz loaded succesfully!");

        getServer().getPluginManager().registerEvents(this, this);
        loadConfig();
        loadNopluginspy();
    }

    @Override
    public void onDisable() {
        getLogger().warning("No Pluginspy deactivated, if this is not a Restart or Shutdown, all your Plugins or Versions will be exposed to your Players!");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (command.getName().equalsIgnoreCase("nopluginspy")) {
            if (args.length > 0 && args[0].equalsIgnoreCase("reload")) {
                reloadConfig();
                blockedCommands.clear();
                loadConfig();
                sender.sendMessage(ChatColor.GREEN + "Reloaded nopluginspy configurations.");
                return true;
            }
        }
        return false;
    }

    @EventHandler
    public void onPlayerCommandPreprocess(PlayerCommandPreprocessEvent event) {
        String command = event.getMessage().split(" ")[0].toLowerCase();
        String playerName = event.getPlayer().getName();
        String timestamp = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date());

        if (isBlockedCommand(command) && !event.getPlayer().isOp()) {
            String message = getConfig().getString("blocked_message", "&c You can't do this.");
            event.getPlayer().sendMessage(ChatColor.translateAlternateColorCodes('&', message));
            event.setCancelled(true);

            savePlayerData(playerName, timestamp, command);
        }
    }

    @EventHandler
    public void onTabComplete(TabCompleteEvent event) {
        if (!event.getSender().isOp()) {
            List<String> completions = event.getCompletions();
            completions.removeIf(this::isBlockedCommand);
        }
    }


    private void loadConfig() {
        blockedCommands = new ArrayList<>();
        getConfig().options().copyDefaults(true);
        saveDefaultConfig();

        if (getConfig().contains("commands")) {
            ConfigurationSection blockedCommandsSection = getConfig().getConfigurationSection("commands");
            for (String path : blockedCommandsSection.getKeys(false)) {
                ConfigurationSection commandSection = blockedCommandsSection.getConfigurationSection(path);
                String command = commandSection.getString("command");
                String mode = commandSection.getString("mode");

                blockedCommands.add(new BlockedCommand(command, mode));
            }
        } else {
            getLogger().severe("Blocked commands configuration section not found in nopluginspy.yml.");
        }
    }

    private void loadNopluginspy() {
        saveResource("nopluginspy.yml", false);
    }


    private boolean isBlockedCommand(String command) {
        String listType = getConfig().getString("list-type", "blacklist");

        for (BlockedCommand blockedCommand : blockedCommands) {
            boolean matches = blockedCommand.matches(command);

            if (listType.equalsIgnoreCase("whitelist") && matches) {
                return false;
            } else if (listType.equalsIgnoreCase("blacklist") && matches) {
                return true;
            }
        }

        return listType.equalsIgnoreCase("whitelist");
    }


    private class BlockedCommand {
        private String command;
        private String mode;

        public BlockedCommand(String command, String mode) {
            this.command = command;
            this.mode = mode;
        }

        public boolean matches(String input) {
            if (mode.equals("equals")) {
                return command.equalsIgnoreCase(input);
            } else if (mode.equals("startsWith")) {
                return input.startsWith(command);
            }
            return false;
        }
    }

    private void savePlayerData(String playerName, String timestamp, String command) {
        try {
            File dataFolder = getDataFolder();
            if (!dataFolder.exists()) {
                dataFolder.mkdir();
            }

            File configFile = new File(dataFolder, "nopluginspy.yml");
            List<String> lines = new ArrayList<>();
            boolean foundPlayer = false;

            try (BufferedReader reader = new BufferedReader(new FileReader(configFile))) {
                String line;

                while ((line = reader.readLine()) != null) {
                    if (line.trim().equals(playerName + ":")) {
                        foundPlayer = true;
                        lines.add(line);
                        lines.add("    lastAttempt: " + timestamp + "\n");
                        lines.add("    command: " + command);
                        reader.readLine();
                    } else {
                        lines.add(line);
                    }
                }
            }

            if (!foundPlayer) {
                lines.add("  " + playerName + ":");
                lines.add("    lastAttempt: " + timestamp + "\n");
                lines.add("    command: " + command);
            }

            try (BufferedWriter writer = new BufferedWriter(new FileWriter(configFile))) {
                for (String line : lines) {
                    writer.write(line);
                    writer.newLine();
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

}
