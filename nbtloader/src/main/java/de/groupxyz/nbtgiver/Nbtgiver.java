package de.groupxyz.nbtgiver;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.arguments.BoolArgumentType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraft.commands.Commands;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.util.UUID;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.nbt.NbtIo;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.network.chat.Component;

@Mod(Nbtgiver.MOD_ID)
public class Nbtgiver {

    public static final String MOD_ID = "nbtgiver";

    public Nbtgiver() {
        FMLJavaModLoadingContext.get().getModEventBus().addListener(this::onClientSetup);
    }

    private void onClientSetup(final FMLClientSetupEvent event) {
    }

    @Mod.EventBusSubscriber(modid = MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.DEDICATED_SERVER)
    public static class CommandRegistration {
        @SubscribeEvent
        public static void onServerStart(ServerStartedEvent event) {
            CommandDispatcher<CommandSourceStack> dispatcher = event.getServer().getCommands().getDispatcher();
            dispatcher.register(Commands.literal("loadinventory")
                    .requires(source -> source.hasPermission(2))
                    .then(Commands.argument("uuid", StringArgumentType.string())
                            .then(Commands.argument("playerName", StringArgumentType.string())
                                    .executes(context -> executeLoadInventory(context)))));
        }

        private static int executeLoadInventory(CommandContext<CommandSourceStack> context) {
            CommandSourceStack source = context.getSource();
            ServerPlayer player = source.getPlayer();

            String uuidString = StringArgumentType.getString(context, "uuid");
            String playerName = StringArgumentType.getString(context, "playerName");
            UUID targetUUID;
            try {
                targetUUID = UUID.fromString(uuidString);
            } catch (IllegalArgumentException e) {
                source.sendFailure(Component.literal("Invalid UUID format."));
                return 0;
            }
            ServerPlayer targetPlayer = source.getServer().getPlayerList().getPlayerByName(playerName);
            if (targetPlayer == null) {
                source.sendFailure(Component.literal("Player not found: " + playerName));
                return 0;
            }

            Path playerDataPath = source.getServer().getWorldPath(LevelResource.ROOT).resolve("playerdata").resolve(targetUUID + ".dat");
            File playerDataFile = playerDataPath.toFile();

            if (!playerDataFile.exists()) {
                source.sendFailure(Component.literal("Player data file not found for UUID: " + uuidString));
                return 0;
            }

            try (FileInputStream fis = new FileInputStream(playerDataFile)) {
                CompoundTag playerData = NbtIo.readCompressed(fis);

                if (playerData.contains("Inventory")) {
                    ListTag inventoryList = playerData.getList("Inventory", 10);
                    targetPlayer.getInventory().clearContent();

                    for (int i = 0; i < inventoryList.size(); i++) {
                        CompoundTag itemData = inventoryList.getCompound(i);
                        CompoundTag newItemData = new CompoundTag();

                        if (itemData.contains("Slot")) {
                            newItemData.putInt("Slot", itemData.getInt("Slot"));
                        }

                        if (itemData.contains("Count")) {
                            newItemData.putInt("Count", itemData.getInt("Count"));
                        } else if (itemData.contains("count")) {
                            newItemData.putInt("Count", itemData.getInt("count"));
                        }

                        if (itemData.contains("id")) {
                            newItemData.putString("id", itemData.getString("id"));
                        }

                        CompoundTag tagData = itemData.getCompound("tag");
                        if (itemData.contains("tag")) {
                            if (!tagData.isEmpty()) {
                                newItemData.put("tag", tagData);
                            }
                        } else {
                            newItemData.put("tag", new CompoundTag());
                        }

                        if (tagData.contains("Enchantments")) {
                            ListTag enchantments = tagData.getList("Enchantments", 10);
                            tagData.put("Enchantments", enchantments);
                        }

                        if (itemData.contains("components")) {
                            CompoundTag componentsData = itemData.getCompound("components");

                            if (componentsData.contains("enchantments")) {
                                CompoundTag enchantmentsData = componentsData.getCompound("enchantments");
                                ListTag enchantmentsList = new ListTag();
                                for (String enchantment : enchantmentsData.getAllKeys()) {
                                    int level = enchantmentsData.getInt(enchantment);
                                    CompoundTag enchantmentTag = new CompoundTag();
                                    enchantmentTag.putString("id", enchantment);
                                    enchantmentTag.putInt("lvl", level);
                                    enchantmentsList.add(enchantmentTag);
                                }
                                newItemData.getCompound("tag").put("Enchantments", enchantmentsList);
                            }

                            for (String key : componentsData.getAllKeys()) {
                                if (!key.equals("enchantments")) {
                                    newItemData.getCompound("tag").put(key, componentsData.get(key));
                                }
                            }
                        }

                        ItemStack itemStack = ItemStack.of(newItemData);
                        targetPlayer.getInventory().add(itemStack);
                        targetPlayer.sendSystemMessage(Component.literal("An admin loaded your inventory from a playerdata file."), true);
                    }

                    source.sendSuccess(() -> Component.literal("(Neo)Forge inventory successfully loaded from player data file!"), true);
                }
            } catch (IOException e) {
                source.sendFailure(Component.literal("Error reading player data file: " + e.getMessage()));
                e.printStackTrace();
            }

            return 1;
        }
    }
}


