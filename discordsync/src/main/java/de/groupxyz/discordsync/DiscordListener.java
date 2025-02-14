package de.groupxyz.discordsync;

import net.dv8tion.jda.api.JDAInfo;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;

import java.awt.Color;
import java.lang.management.ManagementFactory;

public class DiscordListener extends ListenerAdapter {
    private final Plugin plugin;

    public DiscordListener(Plugin plugin) {
        this.plugin = plugin;
        plugin.getConfig().addDefault("usesRoleColor", true);
        plugin.getConfig().options().copyDefaults(true);
        plugin.saveConfig();
    }

    @Override
    public void onMessageReceived(@NotNull MessageReceivedEvent event) {
        if (event.getAuthor().isBot()) return;

        String message = event.getMessage().getContentRaw();
        String channelid = plugin.getConfig().getString("discord-channel-id");
        if (event.getChannel().getId().equals(channelid)) {
            Member member = event.getMember();
            if (member != null && message.startsWith("!")) {
                if (message.equals("!uptime")) {
                    long uptimeMillis = ManagementFactory.getRuntimeMXBean().getUptime();

                    long um = uptimeMillis / (1000 * 60) % 60;
                    long uh = uptimeMillis / (1000 * 60 * 60) % 24;
                    long ud = uptimeMillis / (1000 * 60 * 60 * 24);

                    event.getChannel().sendMessage("[Server] " + "Server is online, uptime: " + ud + "D:" + uh + "H:" + um + "M.").queue();
                } else if (message.equals("!ping")) {
                    event.getChannel().sendMessage("[Command] " + "Pong").queue();
                } else if (message.equals("!players")) {
                    StringBuilder playerNames = new StringBuilder();
                    for (Player player : Bukkit.getServer().getOnlinePlayers()) {
                        playerNames.append(player.getName()).append(", ");
                    }
                    if (playerNames.length() > 0) {
                        playerNames.setLength(playerNames.length() - 2);
                    }
                    if (playerNames == null) {
                        event.getChannel().sendMessage("[Command] " + "Currently no player online.").queue();
                    } else {
                        event.getChannel().sendMessage("[Command] " + playerNames.toString()).queue();
                    }
                } else if (message.equals("!toggleRoleColor") || message.equals("!togglerolecolor")) {
                    if (member.hasPermission(Permission.ADMINISTRATOR)) {
                        boolean currentState = plugin.getConfig().getBoolean("usesRoleColor");
                        boolean newState = !currentState;
                        plugin.getConfig().set("usesRoleColor", newState);
                        plugin.saveConfig();
                        event.getChannel().sendMessage("[Command] " + "Role color " + (newState ? "enabled" : "disabled") + ".").queue();
                    } else {
                        event.getChannel().sendMessage("[Command] " + "You don't have the permission to do that (Administrator)");
                    }
                } else if (message.equals("!help")) {
                    event.getChannel().sendMessage("""
                            [Command]\s
                            Send Minecraft Commands with !yourcommandhere\s
                            Available Discord commands:\s
                            !help\s
                            !ping\s
                            !players\s
                            !togglerolecolor\s
                            !uptime\s""").queue();
                } else {
                    if (member.hasPermission(Permission.ADMINISTRATOR)) {
                        String command = message.substring(1);
                        executeMinecraftCommand(command, event);
                    } else {
                        event.getChannel().sendMessage("[Command] " + "You don't have the permission to do that (Administrator)");
                    }
                }
            } else {
                Color roleColor = member != null ? member.getColor() : null;
                String authorName = event.getAuthor().getName();
                if (roleColor != null && plugin.getConfig().getBoolean("usesRoleColor")) {
                    authorName = convertToMinecraftColorCode(roleColor) + authorName + ChatColor.RESET;
                }
                String finalMessage = "[Discord] " + authorName + ": " + message;
                Bukkit.getScheduler().runTask(plugin, () -> Bukkit.broadcastMessage(finalMessage));
            }
        }
    }

    private void executeMinecraftCommand(String command, MessageReceivedEvent event) {
        Bukkit.getScheduler().runTask(plugin, () -> {
            String result = CommandUtil.dispatchCommand(Bukkit.getConsoleSender(), command);
            event.getChannel().sendMessage("[Console] " + result).queue();
        });
    }

    private String convertToMinecraftColorCode(Color color) {
        if (color == null) {
            return ChatColor.WHITE.toString();
        }

        TextColor textColor = TextColor.color(color.getRed(), color.getGreen(), color.getBlue());
        NamedTextColor namedTextColor = NamedTextColor.nearestTo(textColor);

        return net.md_5.bungee.api.ChatColor.of(String.valueOf(namedTextColor)).toString();
    }

}


