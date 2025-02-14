package de.groupxyz.cmdsign;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.Sign;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.SignChangeEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class Cmdsign extends JavaPlugin implements Listener {

    private FileConfiguration config;
    private Map<String, String> signCommands = new HashMap<>();
    private Map<UUID, Location> pendingCommands = new HashMap<>();

    @Override
    public void onEnable() {
        getLogger().info("Cmdsign by GroupXyz starting...");
        saveDefaultConfig();
        config = getConfig();

        if (!config.contains("sign-commands")) {
            config.createSection("sign-commands");
            saveConfig();
        }

        loadSignCommands();
        getServer().getPluginManager().registerEvents(this, this);
    }

    @Override
    public void onDisable() {
        saveSignCommands();
        getLogger().info("Cmdsign shutting down.");
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        boolean firstJoinOnly = config.getBoolean("first-join-only", false);

        if (!firstJoinOnly || !player.hasPlayedBefore()) {
            String joinCommand = config.getString("join-command", "").replace("{player}", player.getName());

            if (!joinCommand.isEmpty()) {
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), joinCommand);
            }
        }
    }

    @EventHandler
    public void onSignChange(SignChangeEvent event) {
        Player player = event.getPlayer();
        if (player.isOp() && event.getLine(0).equalsIgnoreCase("[Command]")) {
            pendingCommands.put(player.getUniqueId(), event.getBlock().getLocation());
            player.sendMessage(ChatColor.YELLOW + "Gib den Befehl im Chat ein, den dieses Schild ausfuehren soll. Verwende {player} als Platzhalter fuer den Spielernamen.");
        }
    }

    @EventHandler
    public void onPlayerChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        if (pendingCommands.containsKey(player.getUniqueId())) {
            Location loc = pendingCommands.remove(player.getUniqueId());
            String command = event.getMessage();
            String signKey = getSignKey(loc);

            signCommands.put(signKey, command);
            player.sendMessage(ChatColor.GREEN + "Befehl '" + command + "' fuer das Schild gespeichert.");

            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onSignClick(PlayerInteractEvent event) {
        if (event.getAction() == Action.RIGHT_CLICK_BLOCK && event.getClickedBlock() != null) {
            Block block = event.getClickedBlock();
            if (block.getType() == Material.OAK_SIGN || block.getType() == Material.OAK_WALL_SIGN) {
                Sign sign = (Sign) block.getState();
                String line0 = ChatColor.stripColor(sign.getLine(0));

                if (line0.equalsIgnoreCase("[Command]")) {
                    Player player = event.getPlayer();
                    String signKey = getSignKey(block.getLocation());

                    if (signCommands.containsKey(signKey)) {
                        String command = signCommands.get(signKey).replace("{player}", player.getName());
                        Bukkit.dispatchCommand(player, command);
                        event.setCancelled(true);
                    } else {
                        player.sendMessage(ChatColor.RED + "Kein Befehl fuer dieses Schild festgelegt.");
                    }
                }
            }
        }
    }

    private void loadSignCommands() {
        if (config.contains("sign-commands")) {
            for (String key : config.getConfigurationSection("sign-commands").getKeys(false)) {
                String command = config.getString("sign-commands." + key);
                signCommands.put(key, command);
            }
        }
    }

    private void saveSignCommands() {
        for (Map.Entry<String, String> entry : signCommands.entrySet()) {
            config.set("sign-commands." + entry.getKey(), entry.getValue());
        }
        saveConfig();
    }

    private String getSignKey(Location location) {
        World world = location.getWorld();
        return world.getName() + ":" + location.getBlockX() + "," + location.getBlockY() + "," + location.getBlockZ();
    }
}
