package de.groupxyz.movingblocks;

import de.groupxyz.movingblocks.AnimationCommand;
import de.groupxyz.movingblocks.BlockSelectionListener;
import de.groupxyz.movingblocks.AnimationManager;
import de.groupxyz.movingblocks.AnimationEventHandler;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;

public final class Movingblocks extends JavaPlugin implements Listener {

    int pluginId = 25777;
    private AnimationManager animationManager;
    private AnimationEventHandler animationEventHandler;
    private MetricsService metricsService;
    private UpdateChecker updateChecker;
    final Metrics metrics = new Metrics(this, pluginId);

    @Override
    public void onEnable() {
        getLogger().info("Movingblocks by GroupXyz starting...");
        
        saveDefaultConfig();
        
        animationManager = new AnimationManager(this);
        
        animationEventHandler = new AnimationEventHandler(this, animationManager);

        updateChecker = new UpdateChecker(this);

        if (updateChecker.isEnabled()) {
            int initialDelay = getConfig().getInt("update-checker.initial-delay", 6);
            int checkInterval = getConfig().getInt("update-checker.check-interval", 24);
            updateChecker.scheduleUpdateChecks(initialDelay, checkInterval);
        }
        
        getCommand("movingblocks").setExecutor(new AnimationCommand(this, animationManager, animationEventHandler, updateChecker));
        getServer().getPluginManager().registerEvents(new BlockSelectionListener(animationManager), this);
        getServer().getPluginManager().registerEvents(animationEventHandler, this);
        getServer().getPluginManager().registerEvents(this, this);
        
        metricsService = new MetricsService(this, animationManager, animationEventHandler);
        
        getLogger().info("Movingblocks initialized successfully!");
    }

    @Override
    public void onDisable() {
        animationManager.saveAllAnimations();
        
        if (animationEventHandler != null) {
            animationEventHandler.saveEvents();
        }
        
        getLogger().info("Movingblocks shutting down.");
    }

    public Metrics getMetrics() {
        return metrics;
    }
    
    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        if (updateChecker != null) {
            updateChecker.notifyPlayer(event.getPlayer());
        }
    }
}
