package de.groupxyz.movingblocks;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.data.BlockData;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.ChatColor;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.hover.content.Text;

import java.io.File;
import java.io.IOException;
import java.util.*;

public class AnimationManager {
    private final Plugin plugin;
    private final Map<UUID, List<Location>> selectedBlocks;
    private final Map<UUID, Integer> currentFrame;
    private final Map<UUID, BukkitRunnable> runningAnimations;
    private final Map<String, UUID> globalAnimations;
    private final boolean useNbt;
    private final boolean skipUnloadedChunks;
    private AnimationSerializer serializer;

    private final Map<String, Animation> animations;
    private final Map<UUID, String> playerCurrentAnimation;

    private final Set<String> protectedAnimations;
    private final Map<Location, String> protectedBlocks;

    private final Set<String> oneTimeRunningAnimations;

    public AnimationManager(Plugin plugin) {
        this.plugin = plugin;
        this.selectedBlocks = new HashMap<>();
        this.currentFrame = new HashMap<>();
        this.runningAnimations = new HashMap<>();
        this.globalAnimations = new HashMap<>();
        this.animations = new HashMap<>();
        this.playerCurrentAnimation = new HashMap<>();
        this.protectedAnimations = new HashSet<>();
        this.protectedBlocks = new HashMap<>();
        this.oneTimeRunningAnimations = new HashSet<>();

        File configFile = new File(plugin.getDataFolder(), "animations.yml");
        if (configFile.exists()) {
            FileConfiguration config = YamlConfiguration.loadConfiguration(configFile);
            this.useNbt = config.getBoolean("useNbt", false);
            this.skipUnloadedChunks = config.getBoolean("skipUnloadedChunks", true);
        } else {
            this.useNbt = false;
            this.skipUnloadedChunks = true;
        }

        this.serializer = new AnimationSerializer(plugin, useNbt);

        plugin.getLogger().info("NBT support is " + (useNbt ? "enabled" : "disabled") +
                " for block animations");
        plugin.getLogger().info("Skip unloaded chunks is " + (skipUnloadedChunks ? "enabled" : "disabled") +
                " for performance optimization");

        loadAnimations();
    }

    static class Animation {
        private String name;
        private List<BlockFrame> frames;
        private int speed;
        private boolean enabled;
        private UUID owner;
        private boolean removeBlocksAfterFrame;
        private boolean isProtected;
        private Map<Integer, String> frameSounds;

        public UUID getOwner() { return owner; }
        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        public int getSpeed() { return speed; }
        public void setSpeed(int speed) { this.speed = speed; }
        public boolean isRemoveBlocksAfterFrame() { return removeBlocksAfterFrame; }
        public void setRemoveBlocksAfterFrame(boolean remove) { this.removeBlocksAfterFrame = remove; }
        public List<BlockFrame> getFrames() { return frames; }

        public Animation(String name, UUID owner) {
            this.name = name;
            this.frames = new ArrayList<>();
            this.speed = 20;
            this.enabled = true;
            this.owner = owner;
            this.removeBlocksAfterFrame = false;
            this.isProtected = false;
            this.frameSounds = new HashMap<>();
        }

        public boolean isProtected() {
            return isProtected;
        }

        public void setProtected(boolean isProtected) {
            this.isProtected = isProtected;
        }
        
        public void addSound(int frameIndex, String soundName) {
            frameSounds.put(frameIndex, soundName);
        }
        
        public void removeSound(int frameIndex) {
            frameSounds.remove(frameIndex);
        }
        
        public String getSound(int frameIndex) {
            return frameSounds.get(frameIndex);
        }
        
        public Map<Integer, String> getAllSounds() {
            return new HashMap<>(frameSounds);
        }
    }
    
    static class BlockFrame {
        private final Map<Location, BlockInfo> blocks;
        
        public BlockFrame() {
            this.blocks = new HashMap<>();
        }

        public BlockFrame(BlockFrame other, double offsetX, double offsetY, double offsetZ, World targetWorld) {
            this.blocks = new HashMap<>();
            for (Map.Entry<Location, BlockInfo> entry : other.blocks.entrySet()) {
                Location oldLoc = entry.getKey();
                Location newLoc = new Location(
                    targetWorld,
                    oldLoc.getX() + offsetX,
                    oldLoc.getY() + offsetY,
                    oldLoc.getZ() + offsetZ
                );
                this.blocks.put(newLoc, new BlockInfo(entry.getValue()));
            }
        }

        public void addBlock(Location loc, Block block) {
            blocks.put(loc.clone(), new BlockInfo(block));
        }
        
        public Map<Location, BlockInfo> getBlocks() {
            return blocks;
        }
    }
    
    static class BlockInfo {
        private final Material material;
        private final BlockData blockData;
        private final BlockState blockState;
        
        public BlockInfo(Block block) {
            this.material = block.getType();
            this.blockData = block.getBlockData().clone();
            this.blockState = block.getState();
        }
        
        public BlockInfo(BlockInfo other) {
            this.material = other.material;
            this.blockData = other.blockData.clone();
            this.blockState = other.blockState;
        }
        
        public Material getMaterial() {
            return material;
        }
        
        public BlockData getBlockData() {
            return blockData;
        }
        
        public BlockState getBlockState() {
            return blockState;
        }
        
        public void applyTo(Block target) {
            target.setType(material);
            target.setBlockData(blockData);

            try {
                BlockState newState = target.getState();
                if (blockState.getClass().equals(newState.getClass())) {
                    blockState.update(true, false);
                }
            } catch (Exception e) {
                Bukkit.getLogger().warning("Failed to apply NBT data: " + e.getMessage());
            }
        }
    }

    public ItemStack createSelectionStick() {
        ItemStack stick = new ItemStack(Material.STICK);
        ItemMeta meta = stick.getItemMeta();
        meta.setDisplayName(ChatColor.GOLD + "Block Selection Stick");
        List<String> lore = new ArrayList<>();
        lore.add(ChatColor.YELLOW + "Right-click to select blocks");
        lore.add(ChatColor.YELLOW + "Left-click to create a frame");
        meta.setLore(lore);
        stick.setItemMeta(meta);
        return stick;
    }

    public void toggleBlockSelection(Player player, Block block) {
        UUID playerUUID = player.getUniqueId();
        List<Location> blocks = selectedBlocks.computeIfAbsent(playerUUID, k -> new ArrayList<>());

        Location location = block.getLocation();
        if (blocks.contains(location)) {
            blocks.remove(location);
            player.sendMessage("§cBlock deselected!");
        } else {
            blocks.add(location);
            player.sendMessage("§aBlock selected! Total: " + blocks.size());
        }
    }

    public void deselectAllBlocks(Player player) {
        UUID playerUUID = player.getUniqueId();
        List<Location> blocks = selectedBlocks.get(playerUUID);

        if (blocks == null || blocks.isEmpty()) {
            player.sendMessage("§cNo blocks are currently selected!");
            return;
        }

        int count = blocks.size();
        blocks.clear();
        player.sendMessage("§aDeselected all " + count + " blocks!");
    }

    public void createAnimation(Player player, String name) {
        UUID playerUUID = player.getUniqueId();

        if (runningAnimations.containsKey(playerUUID)) {
            stopAnimation(player);
        }

        String currentAnim = playerCurrentAnimation.get(playerUUID);
        if (currentAnim != null && animations.containsKey(currentAnim)) {
            player.sendMessage("§cYou are currently working on animation '" + currentAnim + "'.");
            player.sendMessage("§cPlease finalize it (mb finalize) before creating a new one.");
            return;
        }

        if (animations.containsKey(name)) {
            player.sendMessage("§cAn animation with that name already exists!");
            return;
        }

        Animation animation = new Animation(name, playerUUID);
        animations.put(name, animation);
        playerCurrentAnimation.put(playerUUID, name);
        player.sendMessage("§aCreated animation '" + name + "' and set as current animation.");
    }

    public void selectAnimation(Player player, String name) {
        if (!animations.containsKey(name)) {
            player.sendMessage("§cAnimation '" + name + "' doesn't exist!");
            return;
        }

        Animation animation = animations.get(name);

        if (!animation.owner.equals(player.getUniqueId()) &&
                !player.hasPermission("movingblocks.admin")) {
            player.sendMessage("§cYou don't have permission to select this animation!");
            return;
        }

        playerCurrentAnimation.put(player.getUniqueId(), name);
        player.sendMessage("§aSelected animation: " + name);
    }

    public void finalizeAnimation(Player player) {
        UUID playerUUID = player.getUniqueId();
        String animName = playerCurrentAnimation.remove(playerUUID);

        if (animName == null) {
            player.sendMessage("§cNo animation selected to finalize!");
            return;
        }

        Animation animation = animations.get(animName);
        if (animation == null) {
            player.sendMessage("§cThe selected animation no longer exists!");
            return;
        }

        selectedBlocks.clear();
        player.sendMessage("§aAnimation '" + animName + "' finalized. You can now create or select another animation, selection has been cleared.");
    }

    public void startGlobalAnimation(String name, Player player) {
        boolean isPlayer = true;
        if (player == null) {
            isPlayer = false;
        }

        if (!animations.containsKey(name)) {
            if (isPlayer) {
                player.sendMessage("§cAnimation '" + name + "' does not exist!");
            } else {
                plugin.getLogger().warning("Animation '" + name + "' does not exist!");
            }
            return;
        }

        if (globalAnimations.containsKey(name)) {
            UUID existingId = globalAnimations.get(name);
            BukkitRunnable task = runningAnimations.get(existingId);
            if (task != null) {
                task.cancel();
                runningAnimations.remove(existingId);
            }
            globalAnimations.remove(name);
        }

        Animation animation = animations.get(name);
        if (!animation.enabled) {
            if (isPlayer) {
                player.sendMessage("§cAnimation '" + name + "' is disabled and cannot be started!");
            } else {
                plugin.getLogger().warning("Animation '" + name + "' is disabled and cannot be started!");
            }
            return;
        }

        if (animation.frames.isEmpty()) {
            if (isPlayer) {
                player.sendMessage("§cAnimation '" + name + "' has no frames and cannot be started!");
            } else {
                plugin.getLogger().warning("Animation '" + name + "' has no frames and cannot be started!");
            }
            return;
        }

        BukkitRunnable task = new BukkitRunnable() {
            private int frameIndex = 0;

            @Override
            public void run() {
                if (frameIndex >= animation.frames.size()) {
                    frameIndex = 0;
                }                playFrame(null, animation.frames.get(frameIndex), animation.removeBlocksAfterFrame);
                  String sound = animation.getSound(frameIndex);                
                if (sound != null && !sound.isEmpty()) {
                    try {
                        Object[] soundInfo = parseSoundInfo(sound);
                        String soundName = (String) soundInfo[0];
                        float radius = (float) soundInfo[1];
                        boolean isGlobal = (boolean) soundInfo[2];
                        //plugin.getLogger().info("Global animation - sound info parsed: name=" + soundName + ", radius=" + radius + ", isGlobal=" + isGlobal);
                        
                        BlockFrame frame = animation.frames.get(frameIndex);
                        Location centerLocation = calculateAnimationCenter(frame);

                        if (isGlobal) {
                            for (Player onlinePlayer : Bukkit.getOnlinePlayers()) {
                                try {
                                    Sound soundEnum = Sound.valueOf(soundName.toUpperCase().replace("MINECRAFT:", "").replace(".", "_"));
                                    onlinePlayer.playSound(onlinePlayer.getLocation(), soundEnum, 1.0f, 1.0f);
                                    //plugin.getLogger().info("(1) Playing sound: " + soundName + " to player: " + onlinePlayer.getName());
                                } catch (IllegalArgumentException ex) {
                                    try {
                                        onlinePlayer.playSound(onlinePlayer.getLocation(), soundName, org.bukkit.SoundCategory.MASTER, 1.0f, 1.0f);
                                    } catch (Exception e) {
                                        plugin.getLogger().warning("Could not play sound: " + soundName + " - " + e.getMessage());
                                    }
                                }
                            }
                        } else {
                            // For radius-based sounds, play at the animation center location
                            if (centerLocation != null) {
                                plugin.getLogger().info("[DEBUG] Playing radius-based sound. Center: " + centerLocation + ", Radius: " + radius);
                                for (Player onlinePlayer : Bukkit.getOnlinePlayers()) {
                                    double distance = onlinePlayer.getLocation().distance(centerLocation);
                                    boolean inWorld = onlinePlayer.getLocation().getWorld().equals(centerLocation.getWorld());
                                    boolean inRadius = distance <= radius;
                                    plugin.getLogger().info("[DEBUG] Player " + onlinePlayer.getName() +
                                        ": distance=" + distance + ", inWorld=" + inWorld + ", inRadius=" + inRadius);

                                    if (inWorld && inRadius) {
                                        try {
                                            Sound soundEnum = Sound.valueOf(soundName.toUpperCase().replace("MINECRAFT:", "").replace(".", "_"));
                                            onlinePlayer.playSound(centerLocation, soundEnum, 1.0f, 1.0f);
                                            plugin.getLogger().info("[DEBUG] Successfully played sound to " + onlinePlayer.getName());
                                        } catch (IllegalArgumentException ex) {
                                            try {
                                                onlinePlayer.playSound(centerLocation, soundName, org.bukkit.SoundCategory.MASTER, 1.0f, 1.0f);
                                                plugin.getLogger().info("[DEBUG] Successfully played custom sound to " + onlinePlayer.getName());
                                            } catch (Exception e) {
                                                plugin.getLogger().warning("Could not play sound: " + soundName + " - " + e.getMessage());
                                            }
                                        }
                                    } else {
                                        plugin.getLogger().info("[DEBUG] Player " + onlinePlayer.getName() + " not in range");
                                    }
                                }
                            } else {
                                plugin.getLogger().warning("[DEBUG] centerLocation is NULL for radius-based sound!");
                            }
                        }
                    } catch (Exception e) {
                        plugin.getLogger().warning("Failed to play sound '" + sound + "': " + e.getMessage());
                    }
                }
                
                frameIndex++;
            }
        };

        UUID animationId = UUID.randomUUID();
        task.runTaskTimer(plugin, 0, animation.speed);
        runningAnimations.put(animationId, task);
        globalAnimations.put(name, animationId);
        
        if (isPlayer) {
            player.sendMessage("§aAnimation '" + name + "' started globally.");
        } else {
            //plugin.getLogger().info("Animation '" + name + "' started globally.");
        }
    }

    public void stopGlobalAnimation(String name, Player player) {
        if (globalAnimations.containsKey(name)) {
            UUID animationId = globalAnimations.get(name);
            BukkitRunnable task = runningAnimations.get(animationId);
            if (task != null) {
                task.cancel();
                runningAnimations.remove(animationId);
                globalAnimations.remove(name);
                player.sendMessage("§aAnimation '" + name + "' stopped globally.");
                return;
            }
        }
        
        player.sendMessage("§cAnimation '" + name + "' is not running globally.");
    }
    
    public void selectArea(Player player, Location first, Location second) {
        UUID playerUUID = player.getUniqueId();
        List<Location> blocks = selectedBlocks.computeIfAbsent(playerUUID, k -> new ArrayList<>());

        int minX = Math.min(first.getBlockX(), second.getBlockX());
        int minY = Math.min(first.getBlockY(), second.getBlockY());
        int minZ = Math.min(first.getBlockZ(), second.getBlockZ());
        int maxX = Math.max(first.getBlockX(), second.getBlockX());
        int maxY = Math.max(first.getBlockY(), second.getBlockY());
        int maxZ = Math.max(first.getBlockZ(), second.getBlockZ());

        World world = first.getWorld();
        if (!first.getWorld().equals(second.getWorld())) {
            player.sendMessage("§cThe points have to be in the same world!");
            return;
        }

        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    Location loc = new Location(world, x, y, z);
                    if (!blocks.contains(loc)) {
                        blocks.add(loc);
                    }
                }
            }
        }

        player.sendMessage("§aArea selected: " + blocks.size() + " blocks.");
    }

    public void createFrame(Player player) {
        UUID playerUUID = player.getUniqueId();
        String animName = playerCurrentAnimation.get(playerUUID);

        if (animName == null) {
            player.sendMessage("§cNo animation selected! Create or select one first.");
            return;
        }

        Animation animation = animations.get(animName);
        if (animation == null) {
            player.sendMessage("§cThe selected animation no longer exists!");
            playerCurrentAnimation.remove(playerUUID);
            return;
        }

        List<Location> blocks = selectedBlocks.get(playerUUID);
        if (blocks == null || blocks.isEmpty()) {
            player.sendMessage("§cNo blocks selected!");
            return;
        }

        BlockFrame frame = new BlockFrame();
        for (Location location : blocks) {
            frame.addBlock(location.clone(), location.getBlock());
        }

        animation.frames.add(frame);
        player.sendMessage("§aFrame added to animation '" + animName + "'. Total frames: " + animation.frames.size() +
                (useNbt ? " §9(with NBT data)" : ""));
    }

    public void deleteFrame(Player player, int frameIndex) {
        UUID playerUUID = player.getUniqueId();
        String animName = playerCurrentAnimation.get(playerUUID);

        if (animName == null) {
            player.sendMessage("§cNo animation selected!");
            return;
        }

        Animation animation = animations.get(animName);
        if (animation == null) {
            player.sendMessage("§cThe selected animation no longer exists!");
            playerCurrentAnimation.remove(playerUUID);
            return;
        }

        if (frameIndex < 0 || frameIndex >= animation.frames.size()) {
            player.sendMessage("§cInvalid frame index! Must be between 0 and " + (animation.frames.size() - 1));
            return;
        }

        animation.frames.remove(frameIndex);
        player.sendMessage("§aDeleted frame " + frameIndex + " from animation '" + animName + "'");
    }

    public void toggleAnimationStatus(Player player, String name) {
        if (!animations.containsKey(name)) {
            player.sendMessage("§cAnimation '" + name + "' doesn't exist!");
            return;
        }

        Animation animation = animations.get(name);
        animation.enabled = !animation.enabled;
        player.sendMessage("§aAnimation '" + name + "' is now " +
                (animation.enabled ? "§aenabled" : "§cdisabled"));
    }

    public void toggleAnimationMode(Player player, String name) {
        if (!animations.containsKey(name)) {
            player.sendMessage("§cAnimation '" + name + "' doesn't exist!");
            return;
        }

        Animation animation = animations.get(name);
        animation.removeBlocksAfterFrame = !animation.removeBlocksAfterFrame;
        player.sendMessage("§aAnimation '" + name + "' mode set to: " +
                (animation.removeBlocksAfterFrame ? "§bPlace and Remove" : "§bReplace"));
    }

    public void startAnimation(Player player, String name) {
        if (name == null) {
            UUID playerUUID = player.getUniqueId();
            name = playerCurrentAnimation.get(playerUUID);

            if (name == null) {
                player.sendMessage("§cNo animation selected! Create or select one first.");
                return;
            }
        }

        if (!animations.containsKey(name)) {
            player.sendMessage("§cAnimation '" + name + "' does not exist!");
            return;
        }

        Animation animation = animations.get(name);
        if (!animation.enabled) {
            player.sendMessage("§cAnimation '" + name + "' is disabled!");
            return;
        }

        if (animation.frames.isEmpty()) {
            player.sendMessage("§cNo frames in animation '" + name + "'!");
            return;
        }

        UUID playerUUID = player.getUniqueId();
        stopAnimation(player);
        currentFrame.put(playerUUID, 0);

        BukkitRunnable task = new BukkitRunnable() {
            @Override
            public void run() {
                if (!player.isOnline()) {
                    cancel();
                    runningAnimations.remove(playerUUID);
                    return;
                }                int frame = currentFrame.get(playerUUID);
                playFrame(player, animation.frames.get(frame), animation.removeBlocksAfterFrame);
                  String sound = animation.getSound(frame);
                  //plugin.getLogger().info("Checking for sound at frame " + frame + ": " + (sound != null ? sound : "null"));
                if (sound != null && !sound.isEmpty()) {
                    try {
                        //plugin.getLogger().info("Raw sound info before parsing: " + sound);
                        Object[] soundInfo = parseSoundInfo(sound);                        
                        String soundName = (String) soundInfo[0];
                        float radius = (float) soundInfo[1];
                        boolean isGlobal = (boolean) soundInfo[2];
                        //plugin.getLogger().info("Sound info parsed: name=" + soundName + ", radius=" + radius + ", isGlobal=" + isGlobal);
                        
                        BlockFrame currentFrame = animation.frames.get(frame);
                        Location centerLocation = calculateAnimationCenter(currentFrame);                        if (isGlobal) {
                            for (Player onlinePlayer : Bukkit.getOnlinePlayers()) {
                                try {
                                    Sound soundEnum = Sound.valueOf(soundName.toUpperCase().replace("MINECRAFT:", "").replace(".", "_"));
                                    onlinePlayer.playSound(onlinePlayer.getLocation(), soundEnum, 1.0f, 1.0f);
                                    //plugin.getLogger().info("(3) Playing sound: " + soundName + " to player: " + onlinePlayer.getName());
                                } catch (IllegalArgumentException ex) {
                                    try {
                                        onlinePlayer.playSound(onlinePlayer.getLocation(), soundName, org.bukkit.SoundCategory.MASTER, 1.0f, 1.0f);
                                    } catch (Exception e) {
                                        plugin.getLogger().warning("Could not play sound: " + soundName + " - " + e.getMessage());
                                    }
                                }
                            }
                        } else {
                            if (centerLocation != null) {
                                for (Player onlinePlayer : Bukkit.getOnlinePlayers()) {
                                    double distance = onlinePlayer.getLocation().distance(centerLocation);
                                    boolean inWorld = onlinePlayer.getLocation().getWorld().equals(centerLocation.getWorld());
                                    boolean inRadius = distance <= radius;

                                    if (inWorld && inRadius) {
                                        try {
                                            Sound soundEnum = Sound.valueOf(soundName.toUpperCase().replace("MINECRAFT:", "").replace(".", "_"));
                                            onlinePlayer.playSound(centerLocation, soundEnum, 1.0f, 1.0f);
                                        } catch (IllegalArgumentException ex) {
                                            try {
                                                onlinePlayer.playSound(centerLocation, soundName, org.bukkit.SoundCategory.MASTER, 1.0f, 1.0f);
                                            } catch (Exception e) {
                                                plugin.getLogger().warning("Could not play sound: " + soundName + " - " + e.getMessage());
                                            }
                                        }
                                    }
                                }
                            } else {
                                plugin.getLogger().warning("centerLocation is NULL for radius-based sound!");
                            }
                        }
                    } catch (Exception e) {
                        plugin.getLogger().warning("Failed to play sound '" + sound + "': " + e.getMessage());
                    }
                }

                frame = (frame + 1) % animation.frames.size();
                currentFrame.put(playerUUID, frame);
            }
        };

        task.runTaskTimer(plugin, 0, animation.speed);
        runningAnimations.put(playerUUID, task);
        player.sendMessage("§aAnimation '" + name + "' started with speed: " + animation.speed + " ticks");
    }

    public void startAnimation(Player player) {
        UUID playerUUID = player.getUniqueId();
        String animName = playerCurrentAnimation.get(playerUUID);

        if (false) { //Debugging def

            if (animName != null) {
                Animation animation = animations.get(animName);
                if (animation != null) {
                    Map<Integer, String> sounds = animation.getAllSounds();
                    plugin.getLogger().info("Animation '" + animName + "' has " + sounds.size() + " sounds:");
                    for (Map.Entry<Integer, String> entry : sounds.entrySet()) {
                        plugin.getLogger().info("  Frame " + entry.getKey() + ": " + entry.getValue());
                    }
                }
            }
        }
        
        startAnimation(player, null);
    }

    public void stopAnimation(Player player, String name) {
        if (name != null) {
            if (globalAnimations.containsKey(name)) {
                stopGlobalAnimation(name, player);
                return;
            }

            UUID playerUUID = player.getUniqueId();
            String currentAnim = playerCurrentAnimation.get(playerUUID);
            if (name.equals(currentAnim)) {
                stopAnimation(player);
            } else {
                player.sendMessage("§cYou are not running animation '" + name + "', you might need to select it first.");
            }
            return;
        }

        UUID playerUUID = player.getUniqueId();
        BukkitRunnable task = runningAnimations.remove(playerUUID);

        if (task != null) {
            task.cancel();
            player.sendMessage("§cAnimation stopped.");
        }
    }

    public void stopAnimation(Player player) {
        stopAnimation(player, null);
    }

    public void setAnimationSpeed(Player player, int speed) {
        if (speed < 1) speed = 1;
        if (speed > 100) speed = 100;

        UUID playerUUID = player.getUniqueId();
        String animName = playerCurrentAnimation.get(playerUUID);

        if (animName == null) {
            player.sendMessage("§cNo animation selected!");
            return;
        }

        Animation animation = animations.get(animName);
        if (animation == null) {
            player.sendMessage("§cThe selected animation no longer exists!");
            playerCurrentAnimation.remove(playerUUID);
            return;
        }

        int oldSpeed = animation.speed;
        animation.speed = speed;
        player.sendMessage("§aAnimation speed set to " + speed + " ticks.");

        if (runningAnimations.containsKey(playerUUID)) {
            BukkitRunnable task = runningAnimations.get(playerUUID);
            task.cancel();
            startAnimation(player);
        }

        if (globalAnimations.containsKey(animName)) {
            UUID animationId = globalAnimations.get(animName);
            BukkitRunnable task = runningAnimations.get(animationId);
            if (task != null) {
                task.cancel();
                runningAnimations.remove(animationId);
                globalAnimations.remove(animName);
                startGlobalAnimation(animName, player);
                player.sendMessage("§aUpdated running global animation with new speed: " + speed + " ticks");
            }
        }
    }

    private void playFrame(Player player, BlockFrame frame, boolean removeAfter) {
        Map<Location, BlockInfo> originalBlocks = new HashMap<>();

        if (removeAfter) {
            for (Location loc : frame.getBlocks().keySet()) {
                if (skipUnloadedChunks && !loc.getWorld().isChunkLoaded(loc.getBlockX() >> 4, loc.getBlockZ() >> 4)) {
                    continue;
                }
                originalBlocks.put(loc.clone(), new BlockInfo(loc.getBlock()));
            }
        }

        for (Map.Entry<Location, BlockInfo> entry : frame.getBlocks().entrySet()) {
            Location loc = entry.getKey();
            if (skipUnloadedChunks && !loc.getWorld().isChunkLoaded(loc.getBlockX() >> 4, loc.getBlockZ() >> 4)) {
                continue;
            }

            Block targetBlock = loc.getBlock();
            entry.getValue().applyTo(targetBlock);
        }

        if (removeAfter && !originalBlocks.isEmpty()) {
            Animation animation = animations.get(player != null ?
                    playerCurrentAnimation.get(player.getUniqueId()) : null);

            new BukkitRunnable() {
                @Override
                public void run() {
                    for (Map.Entry<Location, BlockInfo> entry : originalBlocks.entrySet()) {
                        Location loc = entry.getKey();
                        if (skipUnloadedChunks && !loc.getWorld().isChunkLoaded(loc.getBlockX() >> 4, loc.getBlockZ() >> 4)) {
                            continue;
                        }
                        Block targetBlock = loc.getBlock();
                        entry.getValue().applyTo(targetBlock);
                    }
                }
            }.runTaskLater(plugin, Math.max(animation != null ? animation.speed / 2 : 10, 1));
        }
    }

    public void previewFrame(Player player, int frameIndex) {
        UUID playerUUID = player.getUniqueId();
        String animName = playerCurrentAnimation.get(playerUUID);

        if (animName == null) {
            player.sendMessage("§cNo animation selected!");
            return;
        }

        Animation animation = animations.get(animName);
        if (animation == null) {
            player.sendMessage("§cThe selected animation no longer exists!");
            playerCurrentAnimation.remove(playerUUID);
            return;
        }

        if (frameIndex < 0 || frameIndex >= animation.frames.size()) {
            player.sendMessage("§cInvalid frame index! Must be between 0 and " + (animation.frames.size() - 1));
            return;
        }

        stopAnimation(player);
        BlockFrame frame = animation.frames.get(frameIndex);
        playFrame(player, frame, false);
        player.sendMessage("§aPreviewing frame " + frameIndex);
    }

    public void duplicateAnimation(Player player, String sourceName, String targetName) {
        if (!animations.containsKey(sourceName)) {
            player.sendMessage("§cSource animation '" + sourceName + "' doesn't exist!");
            return;
        }

        if (animations.containsKey(targetName)) {
            player.sendMessage("§cAn animation with name '" + targetName + "' already exists!");
            return;
        }

        Animation sourceAnim = animations.get(sourceName);
        Animation newAnim = new Animation(targetName, player.getUniqueId());

        newAnim.speed = sourceAnim.speed;
        newAnim.removeBlocksAfterFrame = sourceAnim.removeBlocksAfterFrame;

        for (BlockFrame sourceFrame : sourceAnim.frames) {
            BlockFrame newFrame = new BlockFrame();
            for (Map.Entry<Location, BlockInfo> entry : sourceFrame.getBlocks().entrySet()) {
                Location clonedLoc = entry.getKey().clone();
                newFrame.getBlocks().put(clonedLoc, new BlockInfo(entry.getValue()));
            }
            newAnim.frames.add(newFrame);
        }

        animations.put(targetName, newAnim);
        player.sendMessage("§aAnimation '" + sourceName + "' duplicated as '" + targetName + "'");
    }

    public void renameAnimation(Player player, String oldName, String newName) {
        if (!animations.containsKey(oldName)) {
            player.sendMessage("§cAnimation '" + oldName + "' doesn't exist!");
            return;
        }

        if (animations.containsKey(newName)) {
            player.sendMessage("§cAn animation with name '" + newName + "' already exists!");
            return;
        }

        Animation anim = animations.remove(oldName);
        anim.name = newName;
        animations.put(newName, anim);

        for (Map.Entry<UUID, String> entry : new HashMap<>(playerCurrentAnimation).entrySet()) {
            if (entry.getValue().equals(oldName)) {
                playerCurrentAnimation.put(UUID.fromString(entry.getValue()), newName);
            }
        }

        File animationsDir = new File(plugin.getDataFolder(), "animations");
        File oldFile = new File(animationsDir, oldName + ".yml");

        serializer.saveAnimation(newName, anim);

        if (oldFile.exists() && !oldFile.delete()) {
            plugin.getLogger().warning("Failed to delete old animation file: " + oldFile.getName());
        }

        player.sendMessage("§aAnimation renamed from '" + oldName + "' to '" + newName + "'");
    }

    public void clearFrames(Player player) {
        UUID playerUUID = player.getUniqueId();
        String animName = playerCurrentAnimation.get(playerUUID);

        if (animName == null) {
            player.sendMessage("§cNo animation selected!");
            return;
        }

        Animation animation = animations.get(animName);
        if (animation == null) {
            player.sendMessage("§cThe selected animation no longer exists!");
            playerCurrentAnimation.remove(playerUUID);
            return;
        }

        stopAnimation(player);
        animation.frames.clear();
        player.sendMessage("§aAll frames cleared from animation '" + animName + "'!");
    }

    public void deleteAnimation(Player player, String name) {
        if (!animations.containsKey(name)) {
            player.sendMessage("§cAnimation '" + name + "' doesn't exist!");
            return;
        }

        Animation animation = animations.get(name);

        if (!animation.owner.equals(player.getUniqueId()) &&
                !player.hasPermission("movingblocks.admin")) {
            player.sendMessage("§cYou don't have permission to delete this animation!");
            return;
        }

        for (UUID uuid : new ArrayList<>(runningAnimations.keySet())) {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null && name.equals(playerCurrentAnimation.get(uuid))) {
                stopAnimation(p);
            }
        }
        
        if (globalAnimations.containsKey(name)) {
            UUID animationId = globalAnimations.get(name);
            BukkitRunnable task = runningAnimations.get(animationId);
            if (task != null) {
                task.cancel();
                runningAnimations.remove(animationId);
            }
            globalAnimations.remove(name);
            player.sendMessage("§aGlobally running animation '" + name + "' has been stopped.");
        }
        
        if (animation.isProtected()) {
            for (BlockFrame frame : animation.frames) {
                for (Location loc : frame.getBlocks().keySet()) {
                    protectedBlocks.remove(loc);
                }
            }
            protectedAnimations.remove(name);
        }
        
        for (Map.Entry<UUID, String> entry : new HashMap<>(playerCurrentAnimation).entrySet()) {
            if (name.equals(entry.getValue())) {
                playerCurrentAnimation.remove(entry.getKey());
            }
        }

        File animationsDir = new File(plugin.getDataFolder(), "animations");
        File animFile = new File(animationsDir, name + ".yml");
        if (animFile.exists() && !animFile.delete()) {
            plugin.getLogger().warning("Failed to delete animation file: " + animFile.getName());
        }

        animations.remove(name);
        player.sendMessage("§aDeleted animation '" + name + "'");
    }

    public void listAnimations(Player player) {
        if (animations.isEmpty()) {
            player.sendMessage("§eThere are no animations available.");
            return;
        }

        player.sendMessage("§6§l==== Available Animations ====");
        for (Map.Entry<String, Animation> entry : animations.entrySet()) {
            Animation anim = entry.getValue();
            boolean isOwner = anim.owner.equals(player.getUniqueId());
            String status = anim.enabled ? "§aEnabled" : "§cDisabled";
            String ownership = isOwner ? "§aYours" : "§eOther";
            String mode = anim.removeBlocksAfterFrame ? "§bPlace+Remove" : "§bReplace";
            player.sendMessage("§e" + entry.getKey() + " §7- " + status + " §7- " +
                    ownership + " §7- " + mode + " §7- §e" + anim.frames.size() + " frames");
        }
    }

    public void saveAllAnimations() {
        File globalFile = new File(plugin.getDataFolder(), "global.yml");
        FileConfiguration globalConfig = YamlConfiguration.loadConfiguration(globalFile);

        List<String> runningAnimationNames = new ArrayList<>();
        for (String animName : globalAnimations.keySet()) {
            runningAnimationNames.add(animName);
        }
        globalConfig.set("runningAnimations", runningAnimationNames);
        globalConfig.set("useNbt", useNbt);
        globalConfig.set("protectedAnimations", new ArrayList<>(protectedAnimations));

        try {
            globalConfig.save(globalFile);
        } catch (IOException e) {
            plugin.getLogger().severe("Failed to save global config: " + e.getMessage());
        }

        for (Map.Entry<String, Animation> entry : animations.entrySet()) {
            serializer.saveAnimation(entry.getKey(), entry.getValue());
        }

        plugin.getLogger().info("Saved " + animations.size() + " animations to individual files.");
    }

    private void loadAnimations() {
        migrateFromOldFormat();
        loadIndividualAnimations();
        loadGlobalConfig();
    }

    private void migrateFromOldFormat() {
        File oldFile = new File(plugin.getDataFolder(), "animations.yml");
        if (!oldFile.exists()) {
            return;
        }

        plugin.getLogger().info("Migrating animations from old format...");

        FileConfiguration oldConfig = YamlConfiguration.loadConfiguration(oldFile);
        int migratedCount = 0;

        for (String animName : oldConfig.getKeys(false)) {
            if (animName.equals("runningAnimations") ||
                    animName.equals("useNbt") ||
                    animName.equals("protectedAnimations")) {
                continue;
            }

            try {
                Animation animation = loadAnimationFromOldFormat(oldConfig, animName);
                if (animation != null) {
                    animations.put(animName, animation);
                    serializer.saveAnimation(animName, animation);
                    migratedCount++;
                }
            } catch (Exception e) {
                plugin.getLogger().warning("Failed to migrate animation '" + animName + "': " + e.getMessage());
            }
        }

        File globalFile = new File(plugin.getDataFolder(), "global.yml");
        FileConfiguration globalConfig = new YamlConfiguration();

        globalConfig.set("runningAnimations", oldConfig.getStringList("runningAnimations"));
        globalConfig.set("useNbt", oldConfig.getBoolean("useNbt", useNbt));
        globalConfig.set("protectedAnimations", oldConfig.getStringList("protectedAnimations"));

        try {
            globalConfig.save(globalFile);
        } catch (IOException e) {
            plugin.getLogger().severe("Failed to save migrated global config: " + e.getMessage());
        }

        File backupFile = new File(plugin.getDataFolder(), "animations_backup_" + System.currentTimeMillis() + ".yml");
        if (oldFile.renameTo(backupFile)) {
            plugin.getLogger().info("Successfully migrated " + migratedCount + " animations. Old file backed up to: " + backupFile.getName());
        } else {
            plugin.getLogger().warning("Migration completed but failed to backup old file.");
        }
    }

    private void loadGlobalConfig() {
        File globalFile = new File(plugin.getDataFolder(), "global.yml");
        if (!globalFile.exists()) {
            return;
        }

        FileConfiguration globalConfig = YamlConfiguration.loadConfiguration(globalFile);

        List<String> protectedAnimList = globalConfig.getStringList("protectedAnimations");
        protectedAnimations.addAll(protectedAnimList);

        List<String> runningAnimationNames = globalConfig.getStringList("runningAnimations");

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            int startedCount = 0;
            for (String animName : runningAnimationNames) {
                if (animations.containsKey(animName)) {
                    Animation animation = animations.get(animName);
                    if (animation != null && animation.enabled && !animation.frames.isEmpty()) {
                        startGlobalAnimation(animName, null);
                        startedCount++;
                    }
                } else {
                    plugin.getLogger().warning("Could not restart animation '" + animName + "' - animation not found");
                }
            }

            if (startedCount > 0) {
                plugin.getLogger().info("Restarted " + startedCount + " global animations");
            }
        }, 20L);
    }

    private void loadIndividualAnimations() {
        File animationsDir = new File(plugin.getDataFolder(), "animations");
        if (!animationsDir.exists()) {
            plugin.getLogger().info("No animations directory found, creating new one.");
            return;
        }

        File[] animationFiles = animationsDir.listFiles((dir, name) -> name.endsWith(".yml"));
        if (animationFiles == null) {
            return;
        }

        int loadedCount = 0;
        for (File animFile : animationFiles) {
            String animName = animFile.getName().replace(".yml", "");

            Animation animation = serializer.loadAnimation(animName);
            if (animation != null) {
                animations.put(animName, animation);

                if (animation.isProtected()) {
                    for (BlockFrame frame : animation.getFrames()) {
                        for (Location loc : frame.getBlocks().keySet()) {
                            protectedBlocks.put(loc.clone(), animName);
                        }
                    }
                }

                loadedCount++;
            }
        }

        plugin.getLogger().info("Loaded " + loadedCount + " animations from individual files.");
    }

    private Animation loadAnimationFromOldFormat(FileConfiguration oldConfig, String animName) {
        try {
            UUID owner = UUID.fromString(oldConfig.getString(animName + ".owner", ""));
            boolean enabled = oldConfig.getBoolean(animName + ".enabled", true);
            int speed = oldConfig.getInt(animName + ".speed", 20);
            boolean removeBlocksAfterFrame = oldConfig.getBoolean(animName + ".removeBlocksAfterFrame", false);
            boolean isProtected = oldConfig.getBoolean(animName + ".isProtected", false);

            Animation animation = new Animation(animName, owner);
            animation.enabled = enabled;
            animation.speed = speed;
            animation.removeBlocksAfterFrame = removeBlocksAfterFrame;
            animation.setProtected(isProtected);

            ConfigurationSection soundsSection = oldConfig.getConfigurationSection(animName + ".sounds");
            if (soundsSection != null) {
                for (String frameIndexStr : soundsSection.getKeys(false)) {
                    try {
                        int frameIndex = Integer.parseInt(frameIndexStr);
                        String soundInfo = soundsSection.getString(frameIndexStr);
                        animation.addSound(frameIndex, soundInfo);
                    } catch (NumberFormatException e) {
                        plugin.getLogger().warning("Invalid frame index in sound section for animation '" + animName + "': " + frameIndexStr);
                    }
                }
            }

            ConfigurationSection framesSection = oldConfig.getConfigurationSection(animName + ".frames");
            if (framesSection != null) {
                Map<Integer, BlockFrame> frameMap = new HashMap<>();

                for (String frameIndexStr : framesSection.getKeys(false)) {
                    try {
                        int frameIndex = Integer.parseInt(frameIndexStr);
                        BlockFrame frame = frameMap.computeIfAbsent(frameIndex, k -> new BlockFrame());

                        ConfigurationSection blockSection = framesSection.getConfigurationSection(frameIndexStr);
                        if (blockSection != null) {
                            for (String blockIndexStr : blockSection.getKeys(false)) {
                                try {
                                    String worldName = blockSection.getString(blockIndexStr + ".world");
                                    double x = blockSection.getDouble(blockIndexStr + ".x");
                                    double y = blockSection.getDouble(blockIndexStr + ".y");
                                    double z = blockSection.getDouble(blockIndexStr + ".z");
                                    String materialName = blockSection.getString(blockIndexStr + ".material");
                                    String blockDataStr = blockSection.getString(blockIndexStr + ".blockData");

                                    World world = Bukkit.getWorld(worldName);
                                    if (world == null) {
                                        plugin.getLogger().warning("World '" + worldName + "' not found for animation '" + animName + "'");
                                        continue;
                                    }

                                    Material material = Material.valueOf(materialName);
                                    Location location = new Location(world, x, y, z);
                                    BlockInfo blockInfo = createBlockInfo(location, material, blockDataStr);

                                    frame.getBlocks().put(location.clone(), blockInfo);
                                } catch (Exception e) {
                                    plugin.getLogger().warning("Failed to load block at index '" + blockIndexStr + "' for animation '" + animName + "': " + e.getMessage());
                                }
                            }
                        }
                    } catch (NumberFormatException e) {
                        plugin.getLogger().warning("Invalid frame index for animation '" + animName + "': " + frameIndexStr);
                    }
                }

                for (int i = 0; i < frameMap.size(); i++) {
                    if (frameMap.containsKey(i)) {
                        animation.frames.add(frameMap.get(i));
                    }
                }
            }

            return animation;
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to load animation '" + animName + "' from old format: " + e.getMessage());
            return null;
        }
    }

    private BlockInfo createBlockInfo(Location loc, Material mat, String blockDataStr) {
        try {
            Block tempBlock = loc.getBlock();
            Material originalType = tempBlock.getType();
            BlockData originalData = tempBlock.getBlockData().clone();

            tempBlock.setType(mat);

            if (blockDataStr != null && !blockDataStr.isEmpty()) {
                BlockData blockData = Bukkit.createBlockData(blockDataStr);
                tempBlock.setBlockData(blockData);
            }

            BlockInfo blockInfo = new BlockInfo(tempBlock);

            tempBlock.setType(originalType);
            tempBlock.setBlockData(originalData);

            return blockInfo;
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to create block info: " + e.getMessage());
            Block block = loc.getBlock();
            Material original = block.getType();
            BlockData originalData = block.getBlockData().clone();
            
            block.setType(mat);
            BlockInfo result = new BlockInfo(block);
            
            block.setType(original);
            block.setBlockData(originalData);
            
            return result;
        }
    }

    public void toggleProtection(Player player, String name, boolean protect) {
        if (!animations.containsKey(name)) {
            player.sendMessage("§cAnimation '" + name + "' doesn't exist!");
            return;
        }

        Animation animation = animations.get(name);

        if (!animation.owner.equals(player.getUniqueId()) && !player.hasPermission("movingblocks.admin")) {
            player.sendMessage("§cYou don't have permission to modify this animation!");
            return;
        }

        if (animation.isProtected()) {
            for (BlockFrame frame : animation.frames) {
                for (Location loc : frame.getBlocks().keySet()) {
                    if (protectedBlocks.containsKey(loc) &&
                            protectedBlocks.get(loc).equals(name)) {
                        protectedBlocks.remove(loc);
                    }
                }
            }
            protectedAnimations.remove(name);
        }

        animation.setProtected(protect);

        if (protect) {
            protectedAnimations.add(name);
            for (BlockFrame frame : animation.frames) {
                for (Location loc : frame.getBlocks().keySet()) {
                    protectedBlocks.put(loc.clone(), name);
                }
            }
            player.sendMessage("§aProtection §aenabled §afor animation '" + name +
                    "'. All its blocks are now protected.");
        } else {
            player.sendMessage("§cProtection §cdisabled §cfor animation '" + name +
                    "'. Blocks are no longer protected.");
        }
    }

    public boolean isBlockProtected(Location location) {
        return protectedBlocks.containsKey(location);
    }

    public void showAnimationInfo(Player player, String name) {
        if (!animations.containsKey(name)) {
            player.sendMessage("§cAnimation '" + name + "' doesn't exist!");
            return;
        }

        Animation animation = animations.get(name);

        player.sendMessage("§6§l≡≡≡≡≡≡≡≡≡≡≡≡≡≡≡≡≡≡≡≡≡≡≡≡≡≡≡≡≡≡≡≡≡≡≡≡≡≡≡≡≡");
        player.sendMessage("§6§l  Animation Information: §e" + name);
        player.sendMessage("§6§l≡≡≡≡≡≡≡≡≡≡≡≡≡≡≡≡≡≡≡≡≡≡≡≡≡≡≡≡≡≡≡≡≡≡≡≡≡≡≡≡≡");

        player.sendMessage("§6› §eOwner: §f" + Bukkit.getOfflinePlayer(animation.owner).getName());
        player.sendMessage("§6› §eStatus: " + (animation.enabled ? "§aEnabled" : "§cDisabled"));
        player.sendMessage("§6› §eProtection: " + (animation.isProtected() ? "§aEnabled" : "§cDisabled"));
        player.sendMessage("§6› §eSpeed: §f" + animation.speed + " ticks (" +
                String.format("%.1f", animation.speed / 20.0) + " seconds)");
        player.sendMessage("§6› §eMode: §f" +
                (animation.removeBlocksAfterFrame ? "Place and Remove" : "Replace"));

        int totalFrames = animation.frames.size();
        player.sendMessage("§6› §eFrames: §f" + totalFrames);

        if (totalFrames > 0) {
            StringBuilder frameInfo = new StringBuilder("§6› §eBlocks per frame: §f");
            for (int i = 0; i < totalFrames; i++) {
                frameInfo.append(animation.frames.get(i).getBlocks().size());
                if (i < totalFrames - 1) frameInfo.append(", ");

                if (i > 0 && i % 8 == 0) {
                    player.sendMessage(frameInfo.toString());
                    frameInfo = new StringBuilder("§6  §e                   §f");
                }
            }
            player.sendMessage(frameInfo.toString());

            double avgBlocks = 0;
            Map<Material, Integer> blockTypes = new HashMap<>();

            for (BlockFrame frame : animation.frames) {
                avgBlocks += frame.getBlocks().size();

                for (BlockInfo blockInfo : frame.getBlocks().values()) {
                    Material type = blockInfo.getMaterial();
                    blockTypes.put(type, blockTypes.getOrDefault(type, 0) + 1);
                }
            }

            avgBlocks /= totalFrames;
            player.sendMessage("§6› §eAverage blocks per frame: §f" + String.format("%.1f", avgBlocks));

            List<Map.Entry<Material, Integer>> sortedBlocks = new ArrayList<>(blockTypes.entrySet());
            sortedBlocks.sort((a, b) -> b.getValue().compareTo(a.getValue()));

            player.sendMessage("§6› §eTop block types:");
            int limit = Math.min(5, sortedBlocks.size());
            for (int i = 0; i < limit; i++) {
                Map.Entry<Material, Integer> entry = sortedBlocks.get(i);
                player.sendMessage("§6  §7- §f" + formatMaterialName(entry.getKey()) +
                        " §7(" + entry.getValue() + ")");
            }

            double totalTime = (totalFrames * animation.speed) / 20.0;
            player.sendMessage("§6› §eAnimation duration: §f" +
                    String.format("%.1f", totalTime) + " seconds");
        }

        boolean isRunningGlobally = isAnimationRunningGlobally(name);
        player.sendMessage("§6› §eRunning status: " +
                (isRunningGlobally ? "§aRunning globally" : "§cNot running globally"));

        player.sendMessage("§6§l≡≡≡≡≡≡≡≡≡≡≡≡≡≡≡≡≡≡≡≡≡≡≡≡≡≡≡≡≡≡≡≡≡≡≡≡≡≡≡≡≡");

        String cmdPrefix = "/mb";
        player.sendMessage("§7Click on an action to perform it:");

        TextComponent container = new TextComponent("§8[ ");

        TextComponent actionButton;
        if (isRunningGlobally) {
            actionButton = new TextComponent("§c§l[Stop]");
            actionButton.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, cmdPrefix + " stop " + name));
            actionButton.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                new Text("§eClick to stop the animation")));
        } else {
            actionButton = new TextComponent("§a§l[Play]");
            actionButton.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, cmdPrefix + " play " + name + " global"));
            actionButton.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                new Text("§eClick to stop the animation globally")));
        }
        container.addExtra(actionButton);

        TextComponent separator = new TextComponent(" §8| ");
        container.addExtra(separator);

        if (animation.enabled) {
            actionButton = new TextComponent("§c§l[Disable]");
            actionButton.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, cmdPrefix + " disable " + name));
            actionButton.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                new Text("§eClick to deactivate the animation")));
        } else {
            actionButton = new TextComponent("§a§l[Enable]");
            actionButton.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, cmdPrefix + " enable " + name));
            actionButton.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                new Text("§eClick to enable the animation")));
        }
        container.addExtra(actionButton);

        container.addExtra(new TextComponent(" §8| "));

        if (animation.isProtected()) {
            actionButton = new TextComponent("§c§l[Unprotect]");
            actionButton.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, cmdPrefix + " protect " + name + " off"));
            actionButton.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                new Text("§eClick to unprotect the animation")));
        } else {
            actionButton = new TextComponent("§a§l[Protect]");
            actionButton.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, cmdPrefix + " protect " + name + " on"));
            actionButton.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                new Text("§eClick to protect the animation")));
        }
        container.addExtra(actionButton);

        container.addExtra(new TextComponent(" §8| "));

        actionButton = new TextComponent("§e§l[Paste]");
        actionButton.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, cmdPrefix + " paste " + name));
        actionButton.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
            new Text("§eClick to copy the animation")));
        container.addExtra(actionButton);

        container.addExtra(new TextComponent(" §8| "));

        actionButton = new TextComponent("§b§l[Select]");
        actionButton.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, cmdPrefix + " select " + name));
        actionButton.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
            new Text("§eClick to select the animation")));
        container.addExtra(actionButton);

        container.addExtra(new TextComponent(" §8]"));

        player.spigot().sendMessage(container);
    }

    private String formatMaterialName(Material material) {
        String name = material.name().toLowerCase();
        String[] parts = name.split("_");
        StringBuilder result = new StringBuilder();

        for (String part : parts) {
            if (part.length() > 0) {
                result.append(Character.toUpperCase(part.charAt(0)))
                      .append(part.substring(1))
                      .append(" ");
            }
        }

        return result.toString().trim();
    }

    public List<String> getAnimationNames() {
        return new ArrayList<>(animations.keySet());
    }

    public String getPlayerCurrentAnimation(UUID playerUUID) {
        return playerCurrentAnimation.get(playerUUID);
    }

    public boolean isAnimationRunning(UUID playerUUID) {
        return runningAnimations.containsKey(playerUUID);
    }

    public boolean isAnimationEnabled(String name) {
        Animation animation = animations.get(name);
        return animation != null && animation.enabled;
    }

    public List<String> getRunningGlobalAnimations() {
        return new ArrayList<>(globalAnimations.keySet());
    }

    public boolean isAnimationRunningGlobally(String name) {
        return globalAnimations.containsKey(name);
    }

    public boolean isAnimationRunningAnywhere(String animationName) {
        if (globalAnimations.containsKey(animationName)) {
            UUID animationId = globalAnimations.get(animationName);
            if (runningAnimations.containsKey(animationId)) {
                return true;
            }
        }

        for (Map.Entry<UUID, String> entry : playerCurrentAnimation.entrySet()) {
            UUID playerUUID = entry.getKey();
            String currentAnimName = entry.getValue();
            if (animationName.equals(currentAnimName) && runningAnimations.containsKey(playerUUID)) {
                return true;
            }
        }

        if (oneTimeRunningAnimations.contains(animationName)) {
            return true;
        }

        return false;
    }

    public void pasteAnimation(Player player, String animName, Location targetLoc) {
        if (!animations.containsKey(animName)) {
            player.sendMessage("§cAnimation '" + animName + "' doesn't exist!");
            return;
        }

        Animation animation = animations.get(animName);

        if (animation.frames.isEmpty()) {
            player.sendMessage("§cAnimation '" + animName + "' has no frames to paste!");
            return;
        }

        if (targetLoc == null) {
            targetLoc = player.getLocation().clone();
            targetLoc.setX(Math.floor(targetLoc.getX()));
            targetLoc.setY(Math.floor(targetLoc.getY()));
            targetLoc.setZ(Math.floor(targetLoc.getZ()));
        }

        BlockFrame firstFrame = animation.frames.get(0);
        if (firstFrame.getBlocks().isEmpty()) {
            player.sendMessage("§cFirst frame of animation '" + animName + "' has no blocks!");
            return;
        }

        Location minLoc = null;
        for (Location loc : firstFrame.getBlocks().keySet()) {
            if (minLoc == null) {
                minLoc = loc.clone();
            } else {
                if (loc.getX() < minLoc.getX()) minLoc.setX(loc.getX());
                if (loc.getY() < minLoc.getY()) minLoc.setY(loc.getY());
                if (loc.getZ() < minLoc.getZ()) minLoc.setZ(loc.getZ());
            }
        }

        if (minLoc == null) {
            player.sendMessage("§cCould not determine reference point for animation!");
            return;
        }

        double offsetX = targetLoc.getX() - minLoc.getX();
        double offsetY = targetLoc.getY() - minLoc.getY();
        double offsetZ = targetLoc.getZ() - minLoc.getZ();

        String newName = animName + "_pasted_" + System.currentTimeMillis() % 1000;
        Animation newAnimation = new Animation(newName, player.getUniqueId());
        newAnimation.speed = animation.speed;
        newAnimation.removeBlocksAfterFrame = animation.removeBlocksAfterFrame;
        newAnimation.setProtected(animation.isProtected());

        for (Map.Entry<Integer, String> soundEntry : animation.getAllSounds().entrySet()) {
            newAnimation.addSound(soundEntry.getKey(), soundEntry.getValue());
        }

        for (BlockFrame sourceFrame : animation.frames) {
            BlockFrame newFrame = new BlockFrame(sourceFrame, offsetX, offsetY, offsetZ, targetLoc.getWorld());
            newAnimation.frames.add(newFrame);
        }

        animations.put(newName, newAnimation);

        playerCurrentAnimation.put(player.getUniqueId(), newName);

        player.sendMessage("§aAnimation '" + animName + "' pasted at your location as '" +
                newName + "' with " + newAnimation.frames.size() + " frames.");
        player.sendMessage("§aThe new animation has been selected as your current animation.");
    }

    public void pasteAnimationFrame(Player player, String animName, Location targetLoc, boolean onlyFirstFrame) {
        if (!animations.containsKey(animName)) {
            player.sendMessage("§cAnimation '" + animName + "' doesn't exist!");
            return;
        }

        Animation animation = animations.get(animName);

        if (animation.frames.isEmpty()) {
            player.sendMessage("§cAnimation '" + animName + "' has no frames to paste!");
            return;
        }

        if (targetLoc == null) {
            targetLoc = player.getLocation().clone();
            targetLoc.setX(Math.floor(targetLoc.getX()));
            targetLoc.setY(Math.floor(targetLoc.getY()));
            targetLoc.setZ(Math.floor(targetLoc.getZ()));
        }

        BlockFrame firstFrame = animation.frames.get(0);
        if (firstFrame.getBlocks().isEmpty()) {
            player.sendMessage("§cFirst frame of animation '" + animName + "' has no blocks!");
            return;
        }

        Location minLoc = null;
        for (Location loc : firstFrame.getBlocks().keySet()) {
            if (minLoc == null) {
                minLoc = loc.clone();
            } else {
                if (loc.getX() < minLoc.getX()) minLoc.setX(loc.getX());
                if (loc.getY() < minLoc.getY()) minLoc.setY(loc.getY());
                if (loc.getZ() < minLoc.getZ()) minLoc.setZ(loc.getZ());
            }
        }

        if (minLoc == null) {
            player.sendMessage("§cCould not determine reference point for animation!");
            return;
        }

        double offsetX = targetLoc.getX() - minLoc.getX();
        double offsetY = targetLoc.getY() - minLoc.getY();
        double offsetZ = targetLoc.getZ() - minLoc.getZ();

        int blocksPlaced = 0;

        for (Map.Entry<Location, BlockInfo> entry : firstFrame.getBlocks().entrySet()) {
            Location oldLoc = entry.getKey();
            BlockInfo blockInfo = entry.getValue();

            Location newLoc = new Location(
                targetLoc.getWorld(),
                oldLoc.getX() + offsetX,
                oldLoc.getY() + offsetY,
                oldLoc.getZ() + offsetZ
            );

            try {
                Block targetBlock = newLoc.getBlock();
                blockInfo.applyTo(targetBlock);
                blocksPlaced++;
            } catch (Exception e) {
                player.sendMessage("§cError placing block at " +
                        newLoc.getBlockX() + "," + newLoc.getBlockY() + "," + newLoc.getBlockZ() +
                        ": " + e.getMessage());
            }
        }

        if (onlyFirstFrame) {
            player.sendMessage("§aPasted " + blocksPlaced + " blocks from the first frame of animation '" +
                    animName + "' at your location.");
        } else {
            String newName = animName + "_pasted_" + System.currentTimeMillis() % 1000;
            Animation newAnimation = new Animation(newName, player.getUniqueId());
            newAnimation.speed = animation.speed;
            newAnimation.removeBlocksAfterFrame = animation.removeBlocksAfterFrame;

            for (BlockFrame sourceFrame : animation.frames) {
                BlockFrame newFrame = new BlockFrame(sourceFrame, offsetX, offsetY, offsetZ, targetLoc.getWorld());
                newAnimation.frames.add(newFrame);
            }

            animations.put(newName, newAnimation);

            playerCurrentAnimation.put(player.getUniqueId(), newName);

            player.sendMessage("§aAnimation '" + animName + "' pasted at your location as '" +
                    newName + "' with " + newAnimation.frames.size() + " frames and first frame placed in world.");
            player.sendMessage("§aThe new animation has been selected as your current animation.");
        }
    }

    public boolean isAnimationProtected(String name) {
        Animation animation = animations.get(name);
        return animation != null && animation.isProtected();
    }

    public Plugin getPlugin() {
        return plugin;
    }

    public void playAnimationOnce(String name) {
        if (!animations.containsKey(name)) {
            plugin.getLogger().warning("Animation '" + name + "' does not exist! Cannot play it.");
            return;
        }

        Animation animation = animations.get(name);
        if (!animation.enabled) {
            plugin.getLogger().warning("Animation '" + name + "' is disabled! Cannot play it.");
            return;
        }

        if (animation.frames.isEmpty()) {
            plugin.getLogger().warning("Animation '" + name + "' has no frames! Cannot play it.");
            return;
        }

        oneTimeRunningAnimations.add(name);

        final int[] frameIndex = {0};
        BukkitRunnable task = new BukkitRunnable() {
            @Override
            public void run() {
                if (frameIndex[0] >= animation.frames.size()) {
                    cancel();
                    oneTimeRunningAnimations.remove(name);
                    //plugin.getLogger().info("Animation '" + name + "' completed its one-time run.");
                    return;
                }

                playFrame(null, animation.frames.get(frameIndex[0]), animation.removeBlocksAfterFrame);
                frameIndex[0]++;
            }
        };

        UUID animationId = UUID.randomUUID();
        task.runTaskTimer(plugin, 0, animation.speed);
        runningAnimations.put(animationId, task);
        //plugin.getLogger().info("Animation '" + name + "' started for a one-time run.");
    }

    public boolean isAnimationExists(String name) {
        return animations.containsKey(name);
    }

    public int getAnimationFrameCount(String animationName) {
        Animation animation = animations.get(animationName);
        if (animation == null) {
            return 0;
        }
        return animation.frames.size();
    }

    public int getAnimationTotalBlocks(String animationName) {
        Animation animation = animations.get(animationName);
        if (animation == null) {
            return 0;
        }

        int blockCount = 0;
        for (BlockFrame frame : animation.frames) {
            blockCount += frame.getBlocks().size();
        }
        return blockCount;
    }

    public boolean isAnimationRemoveBlocksAfterFrame(String animationName) {
        Animation animation = animations.get(animationName);
        if (animation == null) {
            return false;
        }
        return animation.removeBlocksAfterFrame;
    }

    public void addSoundToFrame(Player player, int frameIndex, String soundName) {
        addSoundToFrame(player, frameIndex, soundName, 10.0f, false);
    }

    public void removeSoundFromFrame(Player player, int frameIndex) {
        UUID playerUUID = player.getUniqueId();
        String animName = playerCurrentAnimation.get(playerUUID);

        if (animName == null) {
            player.sendMessage("§cNo animation selected!");
            return;
        }

        Animation animation = animations.get(animName);
        if (animation == null) {
            player.sendMessage("§cThe selected animation no longer exists!");
            playerCurrentAnimation.remove(playerUUID);
            return;
        }

        if (frameIndex < 0 || frameIndex >= animation.frames.size()) {
            player.sendMessage("§cInvalid frame index! Must be between 0 and " + (animation.frames.size() - 1));
            return;
        }

        String existingSound = animation.getSound(frameIndex);
        if (existingSound == null) {
            player.sendMessage("§cNo sound exists for frame " + frameIndex);
            return;
        }

        animation.removeSound(frameIndex);
        player.sendMessage("§aRemoved sound '" + existingSound + "' from frame " + frameIndex);
    }

    public void listSounds(Player player) {
        UUID playerUUID = player.getUniqueId();
        String animName = playerCurrentAnimation.get(playerUUID);

        if (animName == null) {
            player.sendMessage("§cNo animation selected!");
            return;
        }

        Animation animation = animations.get(animName);
        if (animation == null) {
            player.sendMessage("§cThe selected animation no longer exists!");
            playerCurrentAnimation.remove(playerUUID);
            return;
        }

        Map<Integer, String> sounds = animation.getAllSounds();        if (sounds.isEmpty()) {
            player.sendMessage("§eNo sounds have been added to animation '" + animName + "'");
            return;
        }
        player.sendMessage("§6§l==== Sounds for Animation '" + animName + "' ====");
        for (Map.Entry<Integer, String> entry : sounds.entrySet()) {
            Object[] soundInfo = parseSoundInfo(entry.getValue());
            String soundName = (String) soundInfo[0];
            float radius = (float) soundInfo[1];
            boolean isGlobal = (boolean) soundInfo[2];

            String displayText = "§eFrame " + entry.getKey() + ": §f" + soundName;
            if (isGlobal) {
                displayText += " §7(Global)";
            } else {
                displayText += " §7(Radius: " + radius + ")";
            }
            player.sendMessage(displayText);
        }
    }
       private Object[] parseSoundInfo(String soundInfo) {
        if (soundInfo == null) return new Object[] {"", 0f, false};

        // Find the last two colons which separate the radius and isGlobal flag
        int lastColon = soundInfo.lastIndexOf(':');
        int secondLastColon = lastColon > 0 ? soundInfo.lastIndexOf(':', lastColon - 1) : -1;

        String soundName;
        float radius = 10.0f;
        boolean isGlobal = false;

        // If we have at least two colons, parse as expected format: soundName:radius:isGlobal
        if (lastColon > 0 && secondLastColon > 0) {
            // Extract soundName (preserving any colons in the name itself)
            soundName = soundInfo.substring(0, secondLastColon);

            // Extract and parse radius
            try {
                radius = Float.parseFloat(soundInfo.substring(secondLastColon + 1, lastColon));
            } catch (NumberFormatException e) {
                // Use default radius if parsing fails
            }

            // Extract and parse isGlobal flag
            String globalFlag = soundInfo.substring(lastColon + 1);
            isGlobal = "1".equals(globalFlag) || "true".equalsIgnoreCase(globalFlag);
        } else {
            // If we don't have the expected format, use the whole string as the sound name
            soundName = soundInfo;
        }

        return new Object[] {soundName, radius, isGlobal};
    }

    public void addSoundToFrame(Player player, int frameIndex, String soundName, float radius, boolean isGlobal) {
        UUID playerUUID = player.getUniqueId();
        String animName = playerCurrentAnimation.get(playerUUID);

        if (animName == null) {
            player.sendMessage("§cNo animation selected!");
            return;
        }

        Animation animation = animations.get(animName);
        if (animation == null) {
            player.sendMessage("§cThe selected animation no longer exists!");
            playerCurrentAnimation.remove(playerUUID);
            return;
        }

        if (frameIndex < 0 || frameIndex >= animation.frames.size()) {
            player.sendMessage("§cInvalid frame index! Must be between 0 and " + (animation.frames.size() - 1));
            return;
        }

        String soundInfo = soundName + ":" + radius + ":" + (isGlobal ? "1" : "0");
        animation.addSound(frameIndex, soundInfo);

        String radiusMsg = radius > 0 ? " with radius " + radius : " at player location";
        String globalMsg = isGlobal ? " (global)" : "";
        player.sendMessage("§aAdded sound '" + soundName + "'" + radiusMsg + globalMsg + " to frame " + frameIndex + " in animation '" + animName + "'");
    }

    public List<Integer> getFrameNumbers(Player player) {
        UUID playerUUID = player.getUniqueId();
        String animName = playerCurrentAnimation.get(playerUUID);

        if (animName == null) {
            return new ArrayList<>();
        }

        Animation animation = animations.get(animName);
        if (animation == null) {
            return new ArrayList<>();
        }

        List<Integer> frameNumbers = new ArrayList<>();
        for (int i = 0; i < animation.frames.size(); i++) {
            frameNumbers.add(i);
        }

        return frameNumbers;
    }

    public List<String> getSuggestableSounds() {
        List<String> sounds = new ArrayList<>();

        try {
            for (org.bukkit.Sound sound : org.bukkit.Sound.values()) {
                String soundName = sound.getKey().toString();
                sounds.add(soundName);
            }

            Collections.sort(sounds);
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to get all sound keys: " + e.getMessage());
            sounds.add("minecraft:block.note_block.bass");
            sounds.add("minecraft:block.note_block.bell");
            sounds.add("minecraft:entity.player.levelup");
            sounds.add("minecraft:ui.button.click");
        }

        return sounds;
    }

    private Location calculateAnimationCenter(BlockFrame frame) {
        if (frame == null || frame.getBlocks().isEmpty()) {
            plugin.getLogger().warning("Error calculating animation center: Frame is null or empty!");
            return null;
        }

        Map<Location, BlockInfo> blockMap = frame.getBlocks();
        List<Location> locations = new ArrayList<>(blockMap.keySet());

        for (int i = 0; i < Math.min(3, locations.size()); i++) {
            Location loc = locations.get(i);
        }

        for (Player p : Bukkit.getOnlinePlayers()) {
            Location pLoc = p.getLocation();
        }

        double minX = Double.MAX_VALUE, minY = Double.MAX_VALUE, minZ = Double.MAX_VALUE;
        double maxX = -Double.MAX_VALUE, maxY = -Double.MAX_VALUE, maxZ = -Double.MAX_VALUE;

        for (Location loc : locations) {
            minX = Math.min(minX, loc.getX());
            minY = Math.min(minY, loc.getY());
            minZ = Math.min(minZ, loc.getZ());
            maxX = Math.max(maxX, loc.getX());
            maxY = Math.max(maxY, loc.getY());
            maxZ = Math.max(maxZ, loc.getZ());
        }

        double centerX = minX + (maxX - minX) / 2;
        double centerY = minY + (maxY - minY) / 2;
        double centerZ = minZ + (maxZ - minZ) / 2;

        World world = locations.get(0).getWorld();
        Location center = new Location(world, centerX, centerY, centerZ);

        return center;
    }
}
