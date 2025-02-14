package de.groupxyz.discordsync;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.requests.GatewayIntent;
import org.bukkit.ChatColor;
import org.bukkit.configuration.Configuration;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

public class Discordsync extends JavaPlugin {
    private JDA jda;
    public Boolean hasChannel = false;
    double @NotNull [] serverTPS = getServer().getTPS();
    double recentTPS = serverTPS[serverTPS.length - 1];
    String serverVersion = getServer().getName();

    @Override
    public void onEnable() {
        getLogger().info("discordsync by GroupXyz starting...");
        saveDefaultConfig();
        String token = getConfig().getString("discord-bot-token");
        try {
            jda = JDABuilder.createLight(token, GatewayIntent.GUILD_MESSAGES, GatewayIntent.MESSAGE_CONTENT, GatewayIntent.GUILD_MEMBERS)
                    .addEventListeners(new DiscordListener(this))
                    .build();
        } catch (Exception e) {
            e.printStackTrace();
        }
        try {
            sendStartupInfo();
        } catch (Exception e) {
            e.printStackTrace();
        }

        getServer().getPluginManager().registerEvents(new MinecraftListener(this), this);

        int pluginId = 22327;
        Metrics metrics = new Metrics(this, pluginId);
        metrics.addCustomChart(new Metrics.SimplePie("server_version", () -> {
            return String.valueOf(serverVersion);
        }));
        metrics.addCustomChart(new Metrics.SimplePie("server_tps", () -> {
            return String.valueOf(recentTPS);
        }));
        metrics.addCustomChart(new Metrics.SimplePie("channel_linked", () -> {
            return String.valueOf(hasChannel);
        }));
        metrics.addCustomChart(new Metrics.SimplePie("uses_role_colour", () -> {
            return usesRoleColour();
        }));
    }

    @Override
    public void onDisable() {
        getLogger().info("discordsync shutting down...");
        if (jda != null) {
            try {
                sendShutdownInfo();
            } catch (Exception e) {
                e.printStackTrace();
            }
            jda.shutdown();
        }
    }

    public JDA getJDA() {
        return jda;
    }

    public void sendShutdownInfo() {
        String channelid = getConfig().getString("discord-channel-id");
        TextChannel channel = jda.getTextChannelById(channelid);
        if (channel != null) {
            channel.sendMessage("[Server] " + "Server shutting down.").queue();
            hasChannel = true;
        }
    }

    public void sendStartupInfo() {
        String channelid = getConfig().getString("discord-channel-id");
        TextChannel channel = jda.getTextChannelById(channelid);
        if (channel != null) {
            channel.sendMessage("[Server] " + "Server starting up.").queue();
            hasChannel = true;
        }
    }

    public String usesRoleColour() {
        if (getConfig().contains("usesRoleColor")) {
            return String.valueOf(getConfig().getBoolean("usesRoleColor"));
        }
        return "false";
    }
}

