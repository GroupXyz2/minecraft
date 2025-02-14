package de.groupxyz.discordsync;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityDamageEvent;

public class MinecraftListener implements Listener {
    private final Plugin plugin;

    public MinecraftListener(Plugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onPlayerChat(AsyncPlayerChatEvent event) {
        JDA jda = ((Discordsync) plugin).getJDA();
        if (jda != null) {
            String channelid = plugin.getConfig().getString("discord-channel-id");
            TextChannel channel = jda.getTextChannelById(channelid);
            if (channel != null) {
                channel.sendMessage("[Minecraft] " + event.getPlayer().getName() + ": " + event.getMessage()).queue();
            }
        }
    }

    @EventHandler
    public void onPlayerDeath(EntityDeathEvent event) {
        if (event.getEntity() instanceof Player) {
            Player player = (Player) event.getEntity();
            JDA jda = ((Discordsync) plugin).getJDA();
            if (jda != null) {
                String channelid = plugin.getConfig().getString("discord-channel-id");
                TextChannel channel = jda.getTextChannelById(channelid);
                if (channel != null) {
                    EntityDamageEvent lastDamageEvent = player.getLastDamageCause();

                    String deathMessage = player.getName() + " has died";

                    if (lastDamageEvent != null) {
                        switch (lastDamageEvent.getCause()) {
                            case ENTITY_ATTACK:
                                String killer = event.getEntity().getKiller().getName();
                                if (killer != null) {
                                    deathMessage = player.getName() + " was killed by " + killer;
                                } else {
                                    deathMessage = player.getName() + " was killed by a monster";
                                }
                                break;
                            case FALL:
                                deathMessage = player.getName() + " died by falling";
                                break;
                            case DROWNING:
                                deathMessage = player.getName() + " died by drowning";
                                break;
                            case FIRE:
                            case FIRE_TICK:
                                deathMessage = player.getName() + " burned";
                                break;
                            case LAVA:
                                deathMessage = player.getName() + " felt in lava";
                                break;
                            case SUFFOCATION:
                                deathMessage = player.getName() + " suffocated";
                                break;
                            case STARVATION:
                                deathMessage = player.getName() + " starved";
                                break;
                            case POISON:
                                deathMessage = player.getName() + " died by poison";
                                break;
                            case MAGIC:
                                deathMessage = player.getName() + " died by magic";
                                break;
                            case VOID:
                                deathMessage = player.getName() + " felt in the void";
                                break;
                            default:
                                deathMessage = player.getName() + " died by " + lastDamageEvent.getCause();
                                break;
                        }
                    }

                    channel.sendMessage("[Death] " + deathMessage).queue();
                }
            }
        }
    }
}

