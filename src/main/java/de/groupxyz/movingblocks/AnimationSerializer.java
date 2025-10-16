package de.groupxyz.movingblocks;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class AnimationSerializer {
    private final Plugin plugin;
    private final boolean useNbt;

    public AnimationSerializer(Plugin plugin, boolean useNbt) {
        this.plugin = plugin;
        this.useNbt = useNbt;
    }

    public void saveAnimation(String name, AnimationManager.Animation animation) {
        File animationsDir = new File(plugin.getDataFolder(), "animations");
        if (!animationsDir.exists()) {
            animationsDir.mkdirs();
        }

        File animFile = new File(animationsDir, name + ".yml");
        FileConfiguration config = new YamlConfiguration();

        config.set("name", name);
        config.set("owner", animation.getOwner().toString());
        config.set("enabled", animation.isEnabled());
        config.set("speed", animation.getSpeed());
        config.set("removeBlocksAfterFrame", animation.isRemoveBlocksAfterFrame());
        config.set("isProtected", animation.isProtected());
        config.set("version", 1);

        if (!animation.getAllSounds().isEmpty()) {
            for (Map.Entry<Integer, String> soundEntry : animation.getAllSounds().entrySet()) {
                config.set("sounds." + soundEntry.getKey(), soundEntry.getValue());
            }
        }

        for (int frameIndex = 0; frameIndex < animation.getFrames().size(); frameIndex++) {
            AnimationManager.BlockFrame frame = animation.getFrames().get(frameIndex);
            int blockIndex = 0;

            for (Map.Entry<Location, AnimationManager.BlockInfo> blockEntry : frame.getBlocks().entrySet()) {
                Location loc = blockEntry.getKey();
                AnimationManager.BlockInfo blockInfo = blockEntry.getValue();

                String path = "frames." + frameIndex + "." + blockIndex;
                config.set(path + ".world", loc.getWorld().getName());
                config.set(path + ".x", loc.getX());
                config.set(path + ".y", loc.getY());
                config.set(path + ".z", loc.getZ());
                config.set(path + ".material", blockInfo.getMaterial().name());
                config.set(path + ".blockData", blockInfo.getBlockData().getAsString());

                blockIndex++;
            }
        }

        try {
            config.save(animFile);
        } catch (IOException e) {
            plugin.getLogger().severe("Failed to save animation '" + name + "': " + e.getMessage());
        }
    }

    public AnimationManager.Animation loadAnimation(String name) {
        File animationsDir = new File(plugin.getDataFolder(), "animations");
        File animFile = new File(animationsDir, name + ".yml");

        if (!animFile.exists()) {
            return null;
        }

        FileConfiguration config = YamlConfiguration.loadConfiguration(animFile);

        try {
            UUID owner = UUID.fromString(config.getString("owner", ""));
            boolean enabled = config.getBoolean("enabled", true);
            int speed = config.getInt("speed", 20);
            boolean removeBlocksAfterFrame = config.getBoolean("removeBlocksAfterFrame", false);
            boolean isProtected = config.getBoolean("isProtected", false);

            AnimationManager.Animation animation = new AnimationManager.Animation(name, owner);
            animation.setEnabled(enabled);
            animation.setSpeed(speed);
            animation.setRemoveBlocksAfterFrame(removeBlocksAfterFrame);
            animation.setProtected(isProtected);

            ConfigurationSection soundsSection = config.getConfigurationSection("sounds");
            if (soundsSection != null) {
                for (String frameIndexStr : soundsSection.getKeys(false)) {
                    try {
                        int frameIndex = Integer.parseInt(frameIndexStr);
                        String soundName = config.getString("sounds." + frameIndexStr);
                        if (soundName != null && !soundName.isEmpty()) {
                            animation.addSound(frameIndex, soundName);
                        }
                    } catch (NumberFormatException e) {
                        plugin.getLogger().warning("Invalid frame index in sound information: " + frameIndexStr);
                    }
                }
            }

            ConfigurationSection framesSection = config.getConfigurationSection("frames");
            if (framesSection != null) {
                Map<Integer, AnimationManager.BlockFrame> frameMap = new HashMap<>();

                for (String frameIndexStr : framesSection.getKeys(false)) {
                    int frameIndex = Integer.parseInt(frameIndexStr);
                    AnimationManager.BlockFrame frame = frameMap.computeIfAbsent(frameIndex, k -> new AnimationManager.BlockFrame());

                    ConfigurationSection blockSection = framesSection.getConfigurationSection(frameIndexStr);
                    if (blockSection != null) {
                        for (String blockIndexStr : blockSection.getKeys(false)) {
                            String path = frameIndexStr + "." + blockIndexStr;

                            String worldName = config.getString("frames." + path + ".world");
                            World world = Bukkit.getWorld(worldName);
                            if (world == null) continue;

                            double x = config.getDouble("frames." + path + ".x");
                            double y = config.getDouble("frames." + path + ".y");
                            double z = config.getDouble("frames." + path + ".z");
                            Location loc = new Location(world, x, y, z);

                            String matName = config.getString("frames." + path + ".material");
                            Material mat = Material.getMaterial(matName);
                            if (mat == null) continue;

                            AnimationManager.BlockInfo blockInfo = createBlockInfo(loc, mat,
                                    config.getString("frames." + path + ".blockData"));

                            frame.getBlocks().put(loc, blockInfo);
                        }
                    }
                }

                for (int i = 0; i < frameMap.size(); i++) {
                    if (frameMap.containsKey(i)) {
                        animation.getFrames().add(frameMap.get(i));
                    }
                }
            }

            return animation;

        } catch (Exception e) {
            plugin.getLogger().warning("Failed to load animation '" + name + "': " + e.getMessage());
            return null;
        }
    }

    private AnimationManager.BlockInfo createBlockInfo(Location loc, Material mat, String blockDataStr) {
        try {
            BlockData blockData;
            if (blockDataStr != null && !blockDataStr.isEmpty()) {
                blockData = Bukkit.createBlockData(blockDataStr);
            } else {
                blockData = Bukkit.createBlockData(mat);
            }

            Block tempBlock = loc.getBlock();
            Material originalType = tempBlock.getType();
            BlockData originalData = tempBlock.getBlockData().clone();

            tempBlock.setType(mat);
            tempBlock.setBlockData(blockData);

            AnimationManager.BlockInfo blockInfo = new AnimationManager.BlockInfo(tempBlock);

            tempBlock.setType(originalType);
            tempBlock.setBlockData(originalData);

            return blockInfo;
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to create block info: " + e.getMessage());
            Block block = loc.getBlock();
            Material original = block.getType();
            BlockData originalData = block.getBlockData().clone();

            block.setType(mat);
            AnimationManager.BlockInfo blockInfo = new AnimationManager.BlockInfo(block);

            block.setType(original);
            block.setBlockData(originalData);

            return blockInfo;
        }
    }
}