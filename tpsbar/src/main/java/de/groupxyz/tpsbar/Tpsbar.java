package de.groupxyz.tpsbar;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;

public class Tpsbar extends JavaPlugin {

    private BossBar tpsBossBar;
    private boolean isEnabled = false;

    @Override
    public void onEnable() {
        getLogger().info("Tpsbar by GroupXyz starting...");
        tpsBossBar = Bukkit.createBossBar(
                ChatColor.GOLD + "TPS: " + ChatColor.GREEN + "20.0",
                BarColor.BLUE,
                BarStyle.SEGMENTED_10
        );
        startTpsUpdater();
    }

    @Override
    public void onDisable() {
        getLogger().info("Tpsbar stopping...");
        if (tpsBossBar != null) {
            tpsBossBar.removeAll();
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (label.equalsIgnoreCase("toggletps")) {
            if (!(sender instanceof Player)) {
                sender.sendMessage(ChatColor.RED + "Only players can toggle the TPS Bossbar.");
                return true;
            }
            Player player = (Player) sender;
            if (args.length == 1) {
                try{
                    player = Bukkit.getPlayer(args[0]);
                } catch (Exception e) {
                    player.sendMessage(ChatColor.RED + "Player not found.");
                    return true;
                }
            }

            if (isEnabled) {
                tpsBossBar.removePlayer(player);
                player.sendMessage(ChatColor.RED + "TPS Bossbar disabled.");
            } else {
                tpsBossBar.addPlayer(player);
                player.sendMessage(ChatColor.GREEN + "TPS Bossbar enabled.");
            }
            isEnabled = !isEnabled;
            return true;
        }
        return false;
    }

    private void startTpsUpdater() {
        Bukkit.getScheduler().runTaskTimer(this, () -> {
            double tps = getServerTPS();
            String tpsText = ChatColor.GOLD + "TPS: " + ChatColor.GREEN + String.format("%.1f", tps);
            tpsBossBar.setTitle(tpsText);

            tpsBossBar.setProgress(Math.min(1.0, tps / 20.0));
        }, 0L, 40L); // Every 2 seconds (40 ticks).
    }

    private double getServerTPS() {
        try {
            double[] tpsArray = Bukkit.getServer().getTPS();
            return tpsArray[0];
        } catch (NoSuchMethodError e) {
            getLogger().warning("TPS retrieval method not found. Defaulting to 20 TPS.");
            return 20.0;
        }
    }
}
