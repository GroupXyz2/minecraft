package de.groupxyz.chunker;

import com.mojang.logging.LogUtils;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.extensions.IForgeMenuType;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import org.slf4j.Logger;

@Mod(Chunker.MODID)
public class Chunker {

    public static final String MODID = "chunker";
    private static final Logger LOGGER = LogUtils.getLogger();

    public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, MODID);
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, MODID);
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, MODID);
    public static final DeferredRegister<MenuType<?>> MENU_TYPES =
            DeferredRegister.create(ForgeRegistries.MENU_TYPES, MODID);
    public static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, MODID);

    public static final RegistryObject<Block> CHUNKER_BLOCK = BLOCKS.register("chunker", ChunkerBlock::new);

    public static final RegistryObject<Item> CHUNKER_ITEM = ITEMS.register("chunker",
            () -> new BlockItem(CHUNKER_BLOCK.get(), new Item.Properties()));

    @SuppressWarnings("unused")
    public static final RegistryObject<CreativeModeTab> CHUNKER_TAB = CREATIVE_MODE_TABS.register("chunker_tab",
            () -> CreativeModeTab.builder()
                    .icon(() -> new ItemStack(CHUNKER_BLOCK.get()))
                    .title(Component.translatable("itemGroup.chunker"))
                    .displayItems((parameters, output) -> {
                        output.accept(CHUNKER_ITEM.get());
                    })
                    .build());

    public static final RegistryObject<BlockEntityType<ChunkerBlockEntity>> CHUNKER_BLOCK_ENTITY =
            BLOCK_ENTITIES.register("chunker",
                    () -> BlockEntityType.Builder.of(ChunkerBlockEntity::new, CHUNKER_BLOCK.get()).build(null));

    public static final RegistryObject<MenuType<ChunkerMenu>> CHUNKER_MENU =
            MENU_TYPES.register("chunker",
                    () -> IForgeMenuType.create((windowId, inv, data) -> new ChunkerMenu(windowId, inv, data)));

    public Chunker() {
        this(FMLJavaModLoadingContext.get().getModEventBus());
    }

    public static final SimpleChannel PACKET_HANDLER = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(MODID, "main"),
            () -> "1.0",
            s -> true,
            s -> true
    );

    public Chunker(IEventBus modEventBus) {
        modEventBus.addListener(this::commonSetup);

        BLOCKS.register(modEventBus);
        ITEMS.register(modEventBus);
        BLOCK_ENTITIES.register(modEventBus);
        MENU_TYPES.register(modEventBus);
        CREATIVE_MODE_TABS.register(modEventBus);

        try {
            Object context = Class.forName("net.minecraftforge.fml.ModLoadingContext")
                    .getMethod("get")
                    .invoke(null);

            context.getClass()
                    .getMethod("registerConfig", ModConfig.Type.class, Class.forName("net.minecraftforge.common.ForgeConfigSpec"))
                    .invoke(context, ModConfig.Type.COMMON, ChunkerConfig.SPEC);
        } catch (Exception e) {
            ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, ChunkerConfig.SPEC);
        }

        MinecraftForge.EVENT_BUS.register(this);
    }

    @Mod.EventBusSubscriber(modid = MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static class ClientModEvents {
        @SubscribeEvent
        public static void onClientSetup(FMLClientSetupEvent event) {
            event.enqueueWork(() -> {
                MenuScreens.register(Chunker.CHUNKER_MENU.get(), ChunkerScreen::new);
            });
        }
    }

    private void commonSetup(final FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            int index = 0;
            PACKET_HANDLER.registerMessage(
                    index++,
                    PacketChangeRadius.class,
                    PacketChangeRadius::encode,
                    PacketChangeRadius::decode,
                    PacketChangeRadius::handle
            );
        });
        LOGGER.info("Chunker by GroupXyz loading...");
    }
}