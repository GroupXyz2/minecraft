package de.groupxyz.fishfang;

import org.bukkit.Bukkit;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.HashMap;
import java.util.Map;

public class Fishfang extends JavaPlugin implements Listener {

    private final Map<Player, FishingChallenge> activeChallenges = new HashMap<>();

    @Override
    public void onEnable() {
        getLogger().info("Fishfang by GroupXyz starting.");
        Bukkit.getPluginManager().registerEvents(this, this);
    }

    @Override
    public void onDisable() {
        getLogger().info("Fishfang stopping.");
        activeChallenges.clear();
    }

    @EventHandler
    public void onPlayerFish(PlayerFishEvent event) {
        Player player = event.getPlayer();

        if (event.getState() == PlayerFishEvent.State.CAUGHT_FISH) {
            event.setCancelled(true); // Prevent normal fishing rewards
            if (activeChallenges.containsKey(player)) {
                return;
            }

            FishingChallenge challenge = new FishingChallenge(player, event);
            activeChallenges.put(player, challenge);
            challenge.start();
        }
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        if (!activeChallenges.containsKey(player)) return;

        FishingChallenge challenge = activeChallenges.get(player);
        if (challenge.isActive()) {
            challenge.incrementProgress();
        }
    }

    private class FishingChallenge {
        private final Player player;
        private final PlayerFishEvent event;
        private int progress = 0;
        private final int maxProgress = 20;
        private boolean active;

        public FishingChallenge(Player player, PlayerFishEvent event) {
            this.player = player;
            this.event = event;
            this.active = true;
        }

        public void start() {
            player.sendTitle("\u00a7eStart Clicking!", "\u00a7fFill the bar!", 10, 20, 10);

            new BukkitRunnable() {
                @Override
                public void run() {
                    if (!active) {
                        cancel();
                        return;
                    }

                    fail();
                }
            }.runTaskLater(Fishfang.this, 100L); // 5 seconds time
        }

        public boolean isActive() {
            return active;
        }

        public void incrementProgress() {
            if (!active) return;

            progress++;
            player.sendActionBar("\u00a7aProgress: " + progress + "/" + maxProgress);

            if (progress >= maxProgress) {
                succeed();
            }
        }

        private void succeed() {
            active = false;
            activeChallenges.remove(player);
            player.sendTitle("\u00a7aSuccess!", "\u00a7fYou caught the fish!", 10, 40, 10);
            if (event.getCaught() instanceof Item) {
                player.getInventory().addItem(((Item) event.getCaught()).getItemStack());
            }
        }

        private void fail() {
            active = false;
            activeChallenges.remove(player);
            player.sendTitle("\u00a7cFailed!", "\u00a7fYou missed the fish!", 10, 40, 10);
        }
    }
}