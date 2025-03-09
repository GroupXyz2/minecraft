package de.groupxyz.chunker;

import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.config.ModConfigEvent;

@Mod.EventBusSubscriber(modid = Chunker.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
public class ChunkerConfig {
    private static final ForgeConfigSpec.Builder BUILDER = new ForgeConfigSpec.Builder();

    public static final ForgeConfigSpec.IntValue MAX_ENERGY;
    public static final ForgeConfigSpec.IntValue MAX_TRANSFER;
    public static final ForgeConfigSpec.IntValue ENERGY_PER_CHUNK;
    public static final ForgeConfigSpec.IntValue MAX_RADIUS;

    static {
        BUILDER.comment("Chunker Configuration");

        MAX_ENERGY = BUILDER
                .comment("Maximum energy storage (FE)")
                .defineInRange("maxEnergy", 100000, 1000, 10000000);

        MAX_TRANSFER = BUILDER
                .comment("Maximum energy transfer rate per tick (FE)")
                .defineInRange("maxTransfer", 1000, 10, 100000);

        ENERGY_PER_CHUNK = BUILDER
                .comment("Energy consumed per chunk per tick (FE)")
                .defineInRange("energyPerChunk", 20, 1, 10000);

        MAX_RADIUS = BUILDER
                .comment("Maximum loading radius (chunks)")
                .defineInRange("maxRadius", 3, 1, 10);
    }

    public static final ForgeConfigSpec SPEC = BUILDER.build();

    @SubscribeEvent
    public static void onLoad(final ModConfigEvent.Loading event) {
        // Config loaded
    }

    @SubscribeEvent
    public static void onReload(final ModConfigEvent.Reloading event) {
        // Config reloaded
    }
}
