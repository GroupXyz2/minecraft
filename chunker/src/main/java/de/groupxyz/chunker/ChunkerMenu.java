package de.groupxyz.chunker;
import de.groupxyz.chunker.Chunker;
import de.groupxyz.chunker.ChunkerBlockEntity;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.energy.IEnergyStorage;

public class ChunkerMenu extends AbstractContainerMenu {
    private final ChunkerBlockEntity blockEntity;
    private final ContainerData data;

    public ChunkerMenu(int containerId, Inventory inv, FriendlyByteBuf extraData) {
        this(containerId, inv, (ChunkerBlockEntity) inv.player.level().getBlockEntity(extraData.readBlockPos()),
                new SimpleContainerData(7));
    }

    public ChunkerMenu(int containerId, Inventory inv, ChunkerBlockEntity entity, ContainerData data) {
        super(Chunker.CHUNKER_MENU.get(), containerId);
        this.blockEntity = entity;
        this.data = data;

    /*
    for (int i = 0; i < 3; ++i) {
        for (int j = 0; j < 9; ++j) {
            this.addSlot(new Slot(inv, j + i * 9 + 9, 8 + j * 18, 84 + i * 18));
        }
    }

    for (int k = 0; k < 9; ++k) {
        this.addSlot(new Slot(inv, k, 8 + k * 18, 142));
    }
    */

        addDataSlots(data);
    }

    public boolean isActive() {
        return data.get(1) > 0;
    }

    public int getRadius() {
        return data.get(0);
    }

    public int getLoadedChunksCount() {
        return data.get(2);
    }

    public int getMaxRadius() {
        return data.get(4);
    }

    public int getEnergyPerChunk() {
        return data.get(5);
    }

    public int getEnergyPerTick() {
        return data.get(3); // Already implemented
    }

    public int getConfigMaxEnergy() {
        return data.get(6);
    }

    public int getEnergyStored() {
        return blockEntity.getCapability(ForgeCapabilities.ENERGY)
                .map(IEnergyStorage::getEnergyStored).orElse(0);
    }

    public int getMaxEnergy() {
        return blockEntity.getCapability(ForgeCapabilities.ENERGY).map(IEnergyStorage::getMaxEnergyStored).orElse(1);
    }

    public long getTotalEnergyUsed() {
        return blockEntity.getTotalEnergyUsed();
    }

    public int getUptime() {
        return blockEntity.getUptime();
    }

    public void setRadius(int radius) {
        if (blockEntity.getLevel() != null && blockEntity.getLevel().isClientSide) {
            Chunker.PACKET_HANDLER.sendToServer(new PacketChangeRadius(blockEntity.getBlockPos(), radius));
        } else {
            blockEntity.setRadius(radius);
        }
    }

    @Override
    public boolean stillValid(Player player) {
        return stillValid(
                ContainerLevelAccess.create(blockEntity.getLevel(), blockEntity.getBlockPos()),
                player,
                Chunker.CHUNKER_BLOCK.get()
        );
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        ItemStack itemstack = ItemStack.EMPTY;
        Slot slot = this.slots.get(index);
        if (slot != null && slot.hasItem()) {
            ItemStack itemstack1 = slot.getItem();
            itemstack = itemstack1.copy();

            if (index < 36) {
                if (!this.moveItemStackTo(itemstack1, 36, this.slots.size(), true)) {
                    return ItemStack.EMPTY;
                }
            } else if (!this.moveItemStackTo(itemstack1, 0, 36, false)) {
                return ItemStack.EMPTY;
            }

            if (itemstack1.isEmpty()) {
                slot.set(ItemStack.EMPTY);
            } else {
                slot.setChanged();
            }
        }
        return itemstack;
    }
}
