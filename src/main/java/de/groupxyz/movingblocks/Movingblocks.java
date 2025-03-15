package de.groupxyz.movingblocks;

import de.groupxyz.movingblocks.AnimationCommand;
import de.groupxyz.movingblocks.BlockSelectionListener;
import de.groupxyz.movingblocks.AnimationManager;
import org.bukkit.plugin.java.JavaPlugin;

public final class Movingblocks extends JavaPlugin {

    private AnimationManager animationManager;

    @Override
    public void onEnable() {
        getLogger().info("Movingblocks by GroupXyz starting...");
        animationManager = new AnimationManager(this);

        getCommand("movingblocks").setExecutor(new AnimationCommand(this, animationManager));
        getServer().getPluginManager().registerEvents(new BlockSelectionListener(animationManager), this);

        saveDefaultConfig();
    }

    @Override
    public void onDisable() {
        animationManager.saveAllAnimations();
        getLogger().info("Movingblocks shutting down.");
    }
}
