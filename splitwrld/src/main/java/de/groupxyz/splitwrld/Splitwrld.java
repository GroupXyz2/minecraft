package de.groupxyz.splitwrld;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.plugin.messaging.PluginMessageListener;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;

public final class Splitwrld extends JavaPlugin implements Listener, PluginMessageListener, CommandExecutor {

    @Override
    public void onEnable() {
        getServer().getPluginManager().registerEvents(this, this);
        getServer().getMessenger().registerOutgoingPluginChannel(this, "BungeeCord");
        getServer().getMessenger().registerIncomingPluginChannel(this, "BungeeCord", this);
        getCommand("listworlds").setExecutor(this);
        getCommand("pingworldserver").setExecutor(this);
        getLogger().info("Splitwrld by GroupXyz initiallized!");
    }

    @Override
    public void onDisable() {
        getLogger().info("Splitworld shutting down...");
    }

    @EventHandler
    public void onPlayerChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        String playerName = player.getDisplayName();
        String message = event.getMessage();

        String formattedMessage = playerName + ": " + message;

        sendChatMessageToOtherServers(formattedMessage);
    }

    private void sendChatMessageToOtherServers(String formattedMessage) {
        for (Player onlinePlayer : Bukkit.getServer().getOnlinePlayers()) {
            onlinePlayer.sendPluginMessage(this, "BungeeCord", ("Forward" + "ALL" + "BungeeCord").getBytes());
        }
    }


    @EventHandler
    public void onPlayerChangedWorld(PlayerChangedWorldEvent event) {
        Player player = event.getPlayer();
        String playerName = player.getDisplayName();
        String fromWorld = event.getFrom().getName();
        String toWorld = player.getWorld().getName();

        sendWorldChangeToOtherServers(playerName, fromWorld, toWorld, player);
    }

    private void sendWorldChangeToOtherServers(String playerName, String fromWorld, String toWorld, Player player) {
        String worldChangeMessage = playerName + " hat die Welt gewechselt von " + fromWorld + " nach " + toWorld;

        for (Player onlinePlayer : Bukkit.getServer().getOnlinePlayers()) {
            onlinePlayer.sendPluginMessage(this, "BungeeCord", ("Forward" + "ALL" + "BungeeCord").getBytes());
        }

        redirectToServer(player, toWorld);
    }

    private void redirectToServer(Player player, String targetWorld) {
        Location targetLocation = getTargetLocation(player, targetWorld);

        try {
            ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
            DataOutputStream dataOutputStream = new DataOutputStream(byteArrayOutputStream);

            dataOutputStream.writeUTF("Connect");
            dataOutputStream.writeUTF(targetWorld);
            dataOutputStream.writeDouble(targetLocation.getX());
            dataOutputStream.writeDouble(targetLocation.getY());
            dataOutputStream.writeDouble(targetLocation.getZ());

            player.sendPluginMessage(this, "BungeeCord", byteArrayOutputStream.toByteArray());

            dataOutputStream.close();
            byteArrayOutputStream.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private Location getTargetLocation(Player player, String targetWorld) {
        World world = player.getWorld();

        if (targetWorld.equalsIgnoreCase("world_nether")) {
            return new Location(world, player.getLocation().getX() * 8, player.getLocation().getY(), player.getLocation().getZ() * 8);
        } else if (targetWorld.equalsIgnoreCase("world_end")) {
            return world.getSpawnLocation();
        } else {
            return player.getLocation();
        }
    }

    @Override
    public void onPluginMessageReceived(String channel, Player player, byte[] message) {
        if (channel.equals("BungeeCord")) {
            String receivedMessage = new String(message);

            getServer().broadcastMessage(receivedMessage);
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (command.getName().equalsIgnoreCase("listworlds")) {
            listAvailableWorlds(sender);
            return true;
        } else if (command.getName().equalsIgnoreCase("pingworldserver")) {
            if (args.length == 1) {
                pingServer(sender, args[0]);
                return true;
            } else {
                sender.sendMessage("Usage: /pingworldserver <server>");
                return false;
            }
        }
        return false;
    }

    private void listAvailableWorlds(CommandSender sender) {
        sender.sendMessage("Available Worlds:");
        for (String world : getServer().getWorlds().stream().map(World::getName).toArray(String[]::new)) {
            sender.sendMessage("- " + world);
        }
    }

    private void pingServer(CommandSender sender, String serverName) {
        sender.sendMessage("Pinging " + serverName + "...");
        if (pingServerStatus(serverName)) {
            sender.sendMessage(serverName + " is online!");
        } else {
            sender.sendMessage(serverName + " is offline or unreachable.");
        }
    }

    private boolean pingServerStatus(String serverName) {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress("localhost", getServerPort(serverName)), 1000);
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    private int getServerPort(String serverName) {
        if (serverName.equalsIgnoreCase("world_nether")) {
            return 25566; //Example Port
        } else if (serverName.equalsIgnoreCase("world_end")) {
            return 25567; //Example Port
        } else {
            return 25565; // Example Port
        }
    }
}
