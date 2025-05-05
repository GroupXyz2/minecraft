package de.groupxyz.movingblocks;

import de.groupxyz.movingblocks.AnimationCommand;
import de.groupxyz.movingblocks.BlockSelectionListener;
import de.groupxyz.movingblocks.AnimationManager;
import de.groupxyz.movingblocks.AnimationEventHandler;
import org.bukkit.plugin.java.JavaPlugin;

public final class Movingblocks extends JavaPlugin {

    private AnimationManager animationManager;
    private AnimationEventHandler animationEventHandler;

    @Override
    public void onEnable() {
        getLogger().info("Movingblocks by GroupXyz starting...");
        
        animationManager = new AnimationManager(this);
        
        animationEventHandler = new AnimationEventHandler(this, animationManager);
        
        getCommand("movingblocks").setExecutor(new AnimationCommand(this, animationManager, animationEventHandler));
        getServer().getPluginManager().registerEvents(new BlockSelectionListener(animationManager), this);
        getServer().getPluginManager().registerEvents(animationEventHandler, this);
        
        saveDefaultConfig();
        
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
}
