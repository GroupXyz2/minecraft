package de.groupxyz.sendplayer;

import org.bukkit.plugin.java.JavaPlugin;

public final class Sendplayer extends JavaPlugin {

    @Override
    public void onEnable() {
        getCommand("tphere").setExecutor(new TeleportCommand(this));
        getCommand("sendplayer").setExecutor(new SendCommand(this));
        getServer().getMessenger().registerOutgoingPluginChannel(this, "BungeeCord");
        getLogger().info("sendplayer plugin by GroupXyz starting...");
    }

    @Override
    public void onDisable() {
        getLogger().info("sendplayer plugin by GroupXyz shutting down...");
        // Plugin shutdown logic
    }
}
