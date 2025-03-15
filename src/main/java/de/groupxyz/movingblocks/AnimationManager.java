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

        public Animation(String name, UUID owner) {
            this.name = name;
            this.frames = new ArrayList<>();
            this.speed = 20;
            this.enabled = true;
            this.owner = owner;
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

    public void createAnimation(Player player, String name) {
        if (animations.containsKey(name)) {
            player.sendMessage("§cAn animation with that name already exists!");
            return;
        }

        Animation animation = new Animation(name, player.getUniqueId());
        animations.put(name, animation);
        playerCurrentAnimation.put(player.getUniqueId(), name);
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

    public void createFrame(Player player) {
        UUID playerUUID = player.getUniqueId();
        String animName = playerCurrentAnimation.get(playerUUID);

        if (animName == null) {
            player.sendMessage("§cNo animation selected! Create or select one first.");
            return;
        }

        List<Location> blocks = selectedBlocks.get(playerUUID);
        if (blocks == null || blocks.isEmpty()) {
            player.sendMessage("§cNo blocks selected!");
            return;
        }

        Animation animation = animations.get(animName);
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

    public void startAnimation(Player player) {
        UUID playerUUID = player.getUniqueId();
        String animName = playerCurrentAnimation.get(playerUUID);

        if (animName == null) {
            player.sendMessage("§cNo animation selected!");
            return;
        }

        Animation animation = animations.get(animName);

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
                playFrame(player, animation.frames.get(frame));

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
        animation.speed = speed;
        player.sendMessage("§aAnimation speed set to " + speed + " ticks.");

        if (runningAnimations.containsKey(playerUUID)) {
            startAnimation(player);
        }
    }

    private void playFrame(Player player, Map<Location, Material> frame) {
        for (Map.Entry<Location, Material> entry : frame.entrySet()) {
            entry.getKey().getBlock().setType(entry.getValue());
        }
    }

    public void clearFrames(Player player) {
        UUID playerUUID = player.getUniqueId();
        String animName = playerCurrentAnimation.get(playerUUID);

        if (animName == null) {
            player.sendMessage("§cNo animation selected!");
            return;
        }

        stopAnimation(player);
        Animation animation = animations.get(animName);
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
            player.sendMessage("§e" + entry.getKey() + " §7- " + status + " §7- " +
                    ownership + " §7- §e" + anim.frames.size() + " frames");
        }
    }

    public void saveAllAnimations() {
        File file = new File(plugin.getDataFolder(), "animations.yml");
        FileConfiguration config = YamlConfiguration.loadConfiguration(file);

        for (String key : config.getKeys(false)) {
            config.set(key, null);
        }

        for (Map.Entry<String, Animation> entry : animations.entrySet()) {
            String name = entry.getKey();
            Animation anim = entry.getValue();

            ConfigurationSection animSection = config.createSection(name);
            animSection.set("owner", anim.owner.toString());
            animSection.set("enabled", anim.enabled);
            animSection.set("speed", anim.speed);

            List<Map<String, Object>> framesList = new ArrayList<>();
            for (Map<Location, Material> frame : anim.frames) {
                Map<String, Object> frameMap = new HashMap<>();
                int blockIndex = 0;

                for (Map.Entry<Location, Material> blockEntry : frame.entrySet()) {
                    Location loc = blockEntry.getKey();
                    Material mat = blockEntry.getValue();

                    ConfigurationSection blockSection =
                            config.createSection(name + ".frames." + blockIndex);

                    blockSection.set("world", loc.getWorld().getName());
                    blockSection.set("x", loc.getX());
                    blockSection.set("y", loc.getY());
                    blockSection.set("z", loc.getZ());
                    blockSection.set("material", mat.name());

                    blockIndex++;
                }
            }

            animSection.set("frameCount", anim.frames.size());
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
            try {
                ConfigurationSection animSection = config.getConfigurationSection(animName);
                if (animSection == null) continue;

                UUID owner = UUID.fromString(animSection.getString("owner", ""));
                boolean enabled = animSection.getBoolean("enabled", true);
                int speed = animSection.getInt("speed", 20);

                Animation animation = new Animation(animName, owner);
                animation.enabled = enabled;
                animation.speed = speed;

                ConfigurationSection framesSection = animSection.getConfigurationSection("frames");
                if (framesSection != null) {
                    Map<Integer, Map<Location, Material>> tempFrames = new HashMap<>();

                    for (String frameKey : framesSection.getKeys(false)) {
                        ConfigurationSection blockSection = framesSection.getConfigurationSection(frameKey);
                        if (blockSection == null) continue;

                        String worldName = blockSection.getString("world");
                        World world = Bukkit.getWorld(worldName);
                        if (world == null) continue;

                        double x = blockSection.getDouble("x");
                        double y = blockSection.getDouble("y");
                        double z = blockSection.getDouble("z");
                        Location loc = new Location(world, x, y, z);

                        String matName = blockSection.getString("material");
                        Material mat = Material.getMaterial(matName);
                        if (mat == null) continue;

                        int frameIndex = Integer.parseInt(frameKey);

                        Map<Location, Material> frame = tempFrames.computeIfAbsent(frameIndex, k -> new HashMap<>());
                        frame.put(loc, mat);
                    }

                    for (int i = 0; i < tempFrames.size(); i++) {
                        Map<Location, Material> frame = tempFrames.get(i);
                        if (frame != null) {
                            animation.frames.add(frame);
                        }
                    }
                }

                animations.put(animName, animation);
            } catch (Exception e) {
                plugin.getLogger().warning("Failed to load animation '" + animName + "': " + e.getMessage());
            }
        }

        plugin.getLogger().info("Loaded " + animations.size() + " animations.");
    }
}