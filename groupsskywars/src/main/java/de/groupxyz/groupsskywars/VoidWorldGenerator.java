package de.groupxyz.groupsskywars;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.generator.WorldInfo;
import org.jetbrains.annotations.NotNull;

import java.util.Random;

public class VoidWorldGenerator extends ChunkGenerator {

    @Override
    public void generateNoise(@NotNull WorldInfo worldInfo, @NotNull Random random, int chunkX, int chunkZ, @NotNull ChunkData chunkData) {
        if (chunkX == 0 && chunkZ == 0) {
            chunkData.setBlock(0, 99, 0, Material.STONE);
        }
    }

    @Override
    public Location getFixedSpawnLocation(@NotNull World world, @NotNull Random random) {
        return new Location(world, 0.5, 100, 0.5);
    }

    @Override
    @SuppressWarnings("deprecation")
    public boolean shouldGenerateBedrock() {
        return false;
    }
}

