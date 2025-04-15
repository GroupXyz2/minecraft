package de.groupxyz.movingblocks;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.io.File;
import java.io.IOException;
import java.util.*;

public class AnimationManager {
    private final Plugin plugin;
    private final Map<UUID, List<Location>> selectedBlocks;
    private final Map<UUID, Integer> currentFrame;
    private final Map<UUID, BukkitRunnable> runningAnimations;

    private final Map<String, Animation> animations;
    private final Map<UUID, String> playerCurrentAnimation;

    public AnimationManager(Plugin plugin) {
        this.plugin = plugin;
        this.selectedBlocks = new HashMap<>();
        this.currentFrame = new HashMap<>();
        this.runningAnimations = new HashMap<>();
        this.animations = new HashMap<>();
        this.playerCurrentAnimation = new HashMap<>();

        loadAnimations();
    }

    private static class Animation {
        private String name;
        private List<Map<Location, Material>> frames;
        private int speed;
        private boolean enabled;
        private UUID owner;
        private boolean removeBlocksAfterFrame;

        public Animation(String name, UUID owner) {
            this.name = name;
            this.frames = new ArrayList<>();
            this.speed = 20;
            this.enabled = true;
            this.owner = owner;
            this.removeBlocksAfterFrame = false;
        }
    }

    public ItemStack createSelectionStick() {
        ItemStack stick = new ItemStack(Material.STICK);
        ItemMeta meta = stick.getItemMeta();
        meta.setDisplayName("§6Block Selection Stick");
        List<String> lore = new ArrayList<>();
        lore.add("§7Right-click to select blocks");
        lore.add("§7Left-click to create a frame");
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

    public void startGlobalAnimation(String name) {
        if (!animations.containsKey(name)) {
            plugin.getLogger().warning("Animation '" + name + "' does not exist!");
            return;
        }

        Animation animation = animations.get(name);
        if (!animation.enabled) {
            plugin.getLogger().warning("Animation '" + name + "' is disabled and cannot be started!");
            return;
        }

        if (animation.frames.isEmpty()) {
            plugin.getLogger().warning("Animation '" + name + "' has no frames and cannot be started!");
            return;
        }

        BukkitRunnable task = new BukkitRunnable() {
            private int frameIndex = 0;

            @Override
            public void run() {
                if (frameIndex >= animation.frames.size()) {
                    frameIndex = 0;
                }

                playFrame(null, animation.frames.get(frameIndex), animation.removeBlocksAfterFrame);
                frameIndex++;
            }
        };

        task.runTaskTimer(plugin, 0, animation.speed);
        runningAnimations.put(UUID.randomUUID(), task);
        plugin.getLogger().info("Animation '" + name + "' started globally.");
    }

    public void stopGlobalAnimation(String name) {
        for (Map.Entry<UUID, BukkitRunnable> entry : new HashMap<>(runningAnimations).entrySet()) {
            UUID uuid = entry.getKey();
            BukkitRunnable task = entry.getValue();

            if (animations.containsKey(name) && animations.get(name).name.equals(name)) {
                task.cancel();
                runningAnimations.remove(uuid);
                plugin.getLogger().info("Animation '" + name + "' stopped globally.");
                return;
            }
        }

        plugin.getLogger().warning("Animation '" + name + "' is not running globally.");
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

        Map<Location, Material> frame = new HashMap<>();
        for (Location location : blocks) {
            frame.put(location.clone(), location.getBlock().getType());
        }

        animation.frames.add(frame);
        player.sendMessage("§aFrame added to animation '" + animName + "'. Total frames: " + animation.frames.size());
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

    public void startAnimation(Player player) {
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

        if (!animation.enabled) {
            player.sendMessage("§cThis animation is disabled!");
            return;
        }

        if (animation.frames == null || animation.frames.isEmpty()) {
            player.sendMessage("§cNo frames in this animation!");
            return;
        }

        stopAnimation(player);
        currentFrame.put(playerUUID, 0);

        BukkitRunnable task = new BukkitRunnable() {
            @Override
            public void run() {
                if (!player.isOnline()) {
                    cancel();
                    runningAnimations.remove(playerUUID);
                    return;
                }

                int frame = currentFrame.get(playerUUID);
                playFrame(player, animation.frames.get(frame), animation.removeBlocksAfterFrame);

                frame = (frame + 1) % animation.frames.size();
                currentFrame.put(playerUUID, frame);
            }
        };

        task.runTaskTimer(plugin, 0, animation.speed);
        runningAnimations.put(playerUUID, task);
        player.sendMessage("§aAnimation '" + animName + "' started with speed: " + animation.speed + " ticks");
    }

    public void stopAnimation(Player player) {
        UUID playerUUID = player.getUniqueId();
        BukkitRunnable task = runningAnimations.remove(playerUUID);

        if (task != null) {
            task.cancel();
            player.sendMessage("§cAnimation stopped.");
        }
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

        animation.speed = speed;
        player.sendMessage("§aAnimation speed set to " + speed + " ticks.");

        if (runningAnimations.containsKey(playerUUID)) {
            startAnimation(player);
        }
    }

    private void playFrame(Player player, Map<Location, Material> frame, boolean removeAfter) {
        Map<Location, Material> originalBlocks = new HashMap<>();

        if (removeAfter) {
            for (Location loc : frame.keySet()) {
                originalBlocks.put(loc, loc.getBlock().getType());
            }
        }

        for (Map.Entry<Location, Material> entry : frame.entrySet()) {
            entry.getKey().getBlock().setType(entry.getValue());
        }

        if (removeAfter) {
            Animation animation = animations.get(playerCurrentAnimation.get(player.getUniqueId()));
            new BukkitRunnable() {
                @Override
                public void run() {
                    for (Map.Entry<Location, Material> entry : originalBlocks.entrySet()) {
                        entry.getKey().getBlock().setType(entry.getValue());
                    }
                }
            }.runTaskLater(plugin, Math.max(animation.speed / 2, 1));
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
        Map<Location, Material> frame = animation.frames.get(frameIndex);
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

        for (Map<Location, Material> sourceFrame : sourceAnim.frames) {
            Map<Location, Material> newFrame = new HashMap<>();
            for (Map.Entry<Location, Material> entry : sourceFrame.entrySet()) {
                newFrame.put(entry.getKey().clone(), entry.getValue());
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
        File file = new File(plugin.getDataFolder(), "animations.yml");
        FileConfiguration config = YamlConfiguration.loadConfiguration(file);

        for (String key : config.getKeys(false)) {
            config.set(key, null);
        }

        List<String> runningAnimationNames = new ArrayList<>();
        for (Map.Entry<UUID, BukkitRunnable> entry : runningAnimations.entrySet()) {
            for (Map.Entry<String, Animation> animEntry : animations.entrySet()) {
                if (animEntry.getValue().name.equals(animEntry.getKey())) {
                    runningAnimationNames.add(animEntry.getKey());
                }
            }
        }
        config.set("runningAnimations", runningAnimationNames);

        for (Map.Entry<String, Animation> entry : animations.entrySet()) {
            String name = entry.getKey();
            Animation anim = entry.getValue();

            config.set(name + ".owner", anim.owner.toString());
            config.set(name + ".enabled", anim.enabled);
            config.set(name + ".speed", anim.speed);
            config.set(name + ".removeBlocksAfterFrame", anim.removeBlocksAfterFrame);

            int frameIndex = 0;
            for (Map<Location, Material> frame : anim.frames) {
                int blockIndex = 0;
                for (Map.Entry<Location, Material> blockEntry : frame.entrySet()) {
                    Location loc = blockEntry.getKey();
                    Material mat = blockEntry.getValue();

                    String path = name + ".frames." + frameIndex + "." + blockIndex;
                    config.set(path + ".world", loc.getWorld().getName());
                    config.set(path + ".x", loc.getX());
                    config.set(path + ".y", loc.getY());
                    config.set(path + ".z", loc.getZ());
                    config.set(path + ".material", mat.name());

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

        for (String animName : config.getKeys(false)) {
            if (animName.equals("runningAnimations")) continue;

            try {
                UUID owner = UUID.fromString(config.getString(animName + ".owner", ""));
                boolean enabled = config.getBoolean(animName + ".enabled", true);
                int speed = config.getInt(animName + ".speed", 20);
                boolean removeBlocksAfterFrame = config.getBoolean(animName + ".removeBlocksAfterFrame", false);

                Animation animation = new Animation(animName, owner);
                animation.enabled = enabled;
                animation.speed = speed;
                animation.removeBlocksAfterFrame = removeBlocksAfterFrame;

                ConfigurationSection framesSection = config.getConfigurationSection(animName + ".frames");
                if (framesSection != null) {
                    for (String frameIndexStr : framesSection.getKeys(false)) {
                        Map<Location, Material> frame = new HashMap<>();

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

                                frame.put(loc, mat);
                            }
                        }

                        if (!frame.isEmpty()) {
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
        for (String animName : runningAnimationNames) {
            if (animations.containsKey(animName)) {
                startGlobalAnimation(animName);
                plugin.getLogger().info("Restored global animation: " + animName);
            }
        }

        plugin.getLogger().info("Loaded " + animations.size() + " animations.");
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
}