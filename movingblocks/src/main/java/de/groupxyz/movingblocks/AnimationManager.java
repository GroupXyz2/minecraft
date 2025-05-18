package de.groupxyz.movingblocks;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
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
import net.md_5.bungee.api.chat.ComponentBuilder;
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

    private final Map<String, Animation> animations;
    private final Map<UUID, String> playerCurrentAnimation;

    private final Set<String> protectedAnimations;
    private final Map<Location, String> protectedBlocks;

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

        this.useNbt = true;
        
        plugin.getLogger().info("NBT support is " + (useNbt ? "enabled" : "disabled") + 
                " for block animations");

        loadAnimations();
    }

    private static class Animation {
        private String name;
        private List<BlockFrame> frames;
        private int speed;
        private boolean enabled;
        private UUID owner;
        private boolean removeBlocksAfterFrame;
        private boolean isProtected;
        private Map<Integer, String> frameSounds;

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
    
    private static class BlockFrame {
        private final Map<Location, BlockInfo> blocks;
        
        public BlockFrame() {
            this.blocks = new HashMap<>();
        }
        
        public void addBlock(Location loc, Block block) {
            blocks.put(loc.clone(), new BlockInfo(block));
        }
        
        public Map<Location, BlockInfo> getBlocks() {
            return blocks;
        }
    }
    
    private static class BlockInfo {
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
            player.sendMessage("§cPlease finish or deselect it before creating a new one.");
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

        player.sendMessage("§aAnimation '" + animName + "' finalized. You can now create or select another animation.");
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
                
                String sound = animation.getSound(frameIndex);                if (sound != null && !sound.isEmpty()) {
                    try {
                        Object[] soundInfo = parseSoundInfo(sound);
                        String soundName = (String) soundInfo[0];
                        float radius = (float) soundInfo[1];
                        boolean isGlobal = (boolean) soundInfo[2];
                        
                        BlockFrame frame = animation.frames.get(frameIndex);
                        Location centerLocation = calculateAnimationCenter(frame);

                        if (isGlobal) {
                            for (Player onlinePlayer : Bukkit.getOnlinePlayers()) {
                                onlinePlayer.playSound(onlinePlayer.getLocation(), soundName, 1.0f, 1.0f);
                            }
                        } else {
                            for (Player onlinePlayer : Bukkit.getOnlinePlayers()) {
                                if (centerLocation != null && 
                                    onlinePlayer.getLocation().getWorld().equals(centerLocation.getWorld()) &&
                                    onlinePlayer.getLocation().distanceSquared(centerLocation) <= radius * radius) {
                                    onlinePlayer.playSound(onlinePlayer.getLocation(), soundName, 1.0f, 1.0f);
                                }
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
                if (sound != null && !sound.isEmpty()) {
                    try {
                        Object[] soundInfo = parseSoundInfo(sound);                        
                        String soundName = (String) soundInfo[0];
                        float radius = (float) soundInfo[1];
                        boolean isGlobal = (boolean) soundInfo[2];
                        
                        BlockFrame currentFrame = animation.frames.get(frame);
                        Location centerLocation = calculateAnimationCenter(currentFrame);                        if (isGlobal) {
                            for (Player onlinePlayer : Bukkit.getOnlinePlayers()) {
                                try {
                                    Sound soundEnum = Sound.valueOf(soundName.toUpperCase().replace("MINECRAFT:", "").replace(".", "_"));
                                    onlinePlayer.playSound(onlinePlayer.getLocation(), soundEnum, 1.0f, 1.0f);
                                } catch (IllegalArgumentException ex) {
                                    try {
                                        onlinePlayer.playSound(onlinePlayer.getLocation(), soundName, org.bukkit.SoundCategory.MASTER, 1.0f, 1.0f);
                                    } catch (Exception e) {
                                        plugin.getLogger().warning("Could not play sound: " + soundName + " - " + e.getMessage());
                                    }
                                }
                            }
                        } else {
                            for (Player onlinePlayer : Bukkit.getOnlinePlayers()) {
                                if (centerLocation != null && 
                                    onlinePlayer.getLocation().getWorld().equals(centerLocation.getWorld()) &&
                                    onlinePlayer.getLocation().distanceSquared(centerLocation) <= radius * radius) {
                                    try {
                                        Sound soundEnum = Sound.valueOf(soundName.toUpperCase().replace("MINECRAFT:", "").replace(".", "_"));
                                        onlinePlayer.playSound(onlinePlayer.getLocation(), soundEnum, 1.0f, 1.0f);
                                    } catch (IllegalArgumentException ex) {
                                        try {
                                            onlinePlayer.playSound(onlinePlayer.getLocation(), soundName, org.bukkit.SoundCategory.MASTER, 1.0f, 1.0f);
                                        } catch (Exception e) {
                                            plugin.getLogger().warning("Could not play sound: " + soundName + " - " + e.getMessage());
                                        }
                                    }
                                }
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
                originalBlocks.put(loc.clone(), new BlockInfo(loc.getBlock()));
            }
        }

        for (Map.Entry<Location, BlockInfo> entry : frame.getBlocks().entrySet()) {
            Block targetBlock = entry.getKey().getBlock();
            entry.getValue().applyTo(targetBlock);
        }

        if (removeAfter) {
            Animation animation = animations.get(player != null ? 
                    playerCurrentAnimation.get(player.getUniqueId()) : null);
            
            new BukkitRunnable() {
                @Override
                public void run() {
                    for (Map.Entry<Location, BlockInfo> entry : originalBlocks.entrySet()) {
                        Block targetBlock = entry.getKey().getBlock();
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
    }    public void saveAllAnimations() {
        File file = new File(plugin.getDataFolder(), "animations.yml");
        FileConfiguration config = YamlConfiguration.loadConfiguration(file);

        for (String key : config.getKeys(false)) {
            config.set(key, null);
        }

        List<String> runningAnimationNames = new ArrayList<>();
        for (String animName : globalAnimations.keySet()) {
            runningAnimationNames.add(animName);
        }
        config.set("runningAnimations", runningAnimationNames);
        
        config.set("useNbt", useNbt);

        config.set("protectedAnimations", new ArrayList<>(protectedAnimations));

        for (Map.Entry<String, Animation> entry : animations.entrySet()) {
            String name = entry.getKey();
            Animation anim = entry.getValue();

            config.set(name + ".owner", anim.owner.toString());
            config.set(name + ".enabled", anim.enabled);
            config.set(name + ".speed", anim.speed);
            config.set(name + ".removeBlocksAfterFrame", anim.removeBlocksAfterFrame);
            config.set(name + ".isProtected", anim.isProtected());

            if (!anim.frameSounds.isEmpty()) {
                for (Map.Entry<Integer, String> soundEntry : anim.frameSounds.entrySet()) {
                    int frameIndex = soundEntry.getKey();
                    String soundName = soundEntry.getValue();
                    config.set(name + ".sounds." + frameIndex, soundName);
                }
            }

            int frameIndex = 0;
            for (BlockFrame frame : anim.frames) {
                int blockIndex = 0;
                for (Map.Entry<Location, BlockInfo> blockEntry : frame.getBlocks().entrySet()) {
                    Location loc = blockEntry.getKey();
                    BlockInfo blockInfo = blockEntry.getValue();

                    String path = name + ".frames." + frameIndex + "." + blockIndex;
                    config.set(path + ".world", loc.getWorld().getName());
                    config.set(path + ".x", loc.getX());
                    config.set(path + ".y", loc.getY());
                    config.set(path + ".z", loc.getZ());
                    config.set(path + ".material", blockInfo.getMaterial().name());
                    
                    config.set(path + ".blockData", blockInfo.getBlockData().getAsString());

                    blockIndex++;
                }
                frameIndex++;
            }
        }

        try {
            config.save(file);
            plugin.getLogger().info("Saved " + animations.size() + " animations to disk.");
        } catch (IOException e) {
            plugin.getLogger().severe("Failed to save animations: " + e.getMessage());
        }
    }

    private void loadAnimations() {
        File file = new File(plugin.getDataFolder(), "animations.yml");
        if (!file.exists()) {
            plugin.getLogger().info("No animations file found, creating new one.");
            return;
        }

        FileConfiguration config = YamlConfiguration.loadConfiguration(file);

        boolean configUseNbt = config.getBoolean("useNbt", true);
        if (configUseNbt != useNbt) {
            plugin.getLogger().info("NBT setting changed from " + configUseNbt + 
                    " to " + useNbt);
        }

        List<String> protectedAnimList = config.getStringList("protectedAnimations");
        protectedAnimations.addAll(protectedAnimList);

        for (String animName : config.getKeys(false)) {
            if (animName.equals("runningAnimations") || 
                animName.equals("useNbt") || 
                animName.equals("protectedAnimations")) continue;

            try {                UUID owner = UUID.fromString(config.getString(animName + ".owner", ""));
                boolean enabled = config.getBoolean(animName + ".enabled", true);
                int speed = config.getInt(animName + ".speed", 20);
                boolean removeBlocksAfterFrame = config.getBoolean(animName + ".removeBlocksAfterFrame", false);
                boolean isProtected = config.getBoolean(animName + ".isProtected", false);

                Animation animation = new Animation(animName, owner);
                animation.enabled = enabled;
                animation.speed = speed;
                animation.removeBlocksAfterFrame = removeBlocksAfterFrame;
                animation.setProtected(isProtected);
                
                // Lade Sound-Informationen
                ConfigurationSection soundsSection = config.getConfigurationSection(animName + ".sounds");
                if (soundsSection != null) {
                    for (String frameIndexStr : soundsSection.getKeys(false)) {
                        try {
                            int frameIndex = Integer.parseInt(frameIndexStr);
                            String soundName = config.getString(animName + ".sounds." + frameIndexStr);
                            if (soundName != null && !soundName.isEmpty()) {
                                animation.addSound(frameIndex, soundName);
                            }
                        } catch (NumberFormatException e) {
                            plugin.getLogger().warning("Invalid frame index in sound information: " + frameIndexStr);
                        }
                    }
                }

                ConfigurationSection framesSection = config.getConfigurationSection(animName + ".frames");
                if (framesSection != null) {
                    for (String frameIndexStr : framesSection.getKeys(false)) {
                        BlockFrame frame = new BlockFrame();

                        ConfigurationSection blockSection = framesSection.getConfigurationSection(frameIndexStr);
                        if (blockSection != null) {
                            for (String blockIndexStr : blockSection.getKeys(false)) {
                                String path = frameIndexStr + "." + blockIndexStr;

                                String worldName = config.getString(animName + ".frames." + path + ".world");
                                World world = Bukkit.getWorld(worldName);
                                if (world == null) continue;

                                double x = config.getDouble(animName + ".frames." + path + ".x");
                                double y = config.getDouble(animName + ".frames." + path + ".y");
                                double z = config.getDouble(animName + ".frames." + path + ".z");
                                Location loc = new Location(world, x, y, z);

                                String matName = config.getString(animName + ".frames." + path + ".material");
                                Material mat = Material.getMaterial(matName);
                                if (mat == null) continue;

                                BlockInfo blockInfo;
                                try {
                                    Block tempBlock = loc.getBlock();
                                    Material originalType = tempBlock.getType();

                                    tempBlock.setType(mat);

                                    String blockDataStr = config.getString(animName + ".frames." + path + ".blockData");
                                    if (blockDataStr != null) {
                                        BlockData blockData = Bukkit.createBlockData(blockDataStr);
                                        tempBlock.setBlockData(blockData);
                                    }

                                    blockInfo = new BlockInfo(tempBlock);

                                    tempBlock.setType(originalType);
                                } catch (Exception e) {
                                    plugin.getLogger().warning("Failed to load block data for animation " + 
                                            animName + ": " + e.getMessage());

                                    loc.getBlock().setType(mat);
                                    blockInfo = new BlockInfo(loc.getBlock());
                                }
                                
                                frame.getBlocks().put(loc, blockInfo);

                                if (animation.isProtected()) {
                                    protectedBlocks.put(loc.clone(), animName);
                                }
                            }
                        }

                        if (!frame.getBlocks().isEmpty()) {
                            animation.frames.add(frame);
                        }
                    }
                }

                animations.put(animName, animation);
            } catch (Exception e) {
                plugin.getLogger().warning("Failed to load animation '" + animName + "': " + e.getMessage());
            }
        }

        List<String> runningAnimationNames = config.getStringList("runningAnimations");
        Set<String> alreadyStarted = new HashSet<>();
        
        for (String animName : runningAnimationNames) {
            if (!alreadyStarted.contains(animName) && animations.containsKey(animName)) {
                startGlobalAnimation(animName, null);
                alreadyStarted.add(animName);
                //plugin.getLogger().info("Restored global animation: " + animName);
            }
        }

        plugin.getLogger().info("Loaded " + animations.size() + " animations.");
        plugin.getLogger().info("Protected animations: " + protectedAnimations.size());
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

        for (BlockFrame sourceFrame : animation.frames) {
            BlockFrame newFrame = new BlockFrame();
            
            for (Map.Entry<Location, BlockInfo> entry : sourceFrame.getBlocks().entrySet()) {
                Location oldLoc = entry.getKey();
                BlockInfo blockInfo = entry.getValue();

                Location newLoc = new Location(
                    targetLoc.getWorld(),
                    oldLoc.getX() + offsetX,
                    oldLoc.getY() + offsetY,
                    oldLoc.getZ() + offsetZ
                );

                newFrame.getBlocks().put(newLoc, new BlockInfo(blockInfo));
            }
            
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
                BlockFrame newFrame = new BlockFrame();
                
                for (Map.Entry<Location, BlockInfo> entry : sourceFrame.getBlocks().entrySet()) {
                    Location oldLoc = entry.getKey();
                    BlockInfo blockInfo = entry.getValue();

                    Location newLoc = new Location(
                        targetLoc.getWorld(),
                        oldLoc.getX() + offsetX,
                        oldLoc.getY() + offsetY,
                        oldLoc.getZ() + offsetZ
                    );

                    newFrame.getBlocks().put(newLoc, new BlockInfo(blockInfo));
                }
                
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

        final int[] frameIndex = {0};
        BukkitRunnable task = new BukkitRunnable() {
            @Override
            public void run() {
                if (frameIndex[0] >= animation.frames.size()) {
                    cancel();
                    plugin.getLogger().info("Animation '" + name + "' completed its one-time run.");
                    return;
                }

                playFrame(null, animation.frames.get(frameIndex[0]), animation.removeBlocksAfterFrame);
                frameIndex[0]++;
            }
        };

        UUID animationId = UUID.randomUUID();
        task.runTaskTimer(plugin, 0, animation.speed);
        runningAnimations.put(animationId, task);
        plugin.getLogger().info("Animation '" + name + "' started for a one-time run.");
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
        String[] parts = soundInfo.split(":");
        String soundName = parts[0];
        float radius = 10.0f; 
        boolean isGlobal = false;
        
        if (parts.length > 1) {
            try {
                radius = Float.parseFloat(parts[1]);
            } catch (NumberFormatException e) {
            }
        }
        
        if (parts.length > 2) {
            isGlobal = "1".equals(parts[2]) || "true".equalsIgnoreCase(parts[2]);
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
            return null;
        }
        
        Map<Location, BlockInfo> blockMap = frame.getBlocks();
        List<Location> locations = new ArrayList<>(blockMap.keySet());
        
        double minX = Double.MAX_VALUE, minY = Double.MAX_VALUE, minZ = Double.MAX_VALUE;
        double maxX = Double.MIN_VALUE, maxY = Double.MIN_VALUE, maxZ = Double.MIN_VALUE;
        
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
        return new Location(world, centerX, centerY, centerZ);
    }
}