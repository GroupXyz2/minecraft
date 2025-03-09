package de.groupxyz.chunker;

import com.mojang.logging.LogUtils;
import de.groupxyz.chunker.Chunker;
import de.groupxyz.chunker.ChunkerMenu;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.energy.EnergyStorage;
import net.minecraftforge.energy.IEnergyStorage;
import net.minecraftforge.server.ServerLifecycleHooks;
import org.slf4j.Logger;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.HashSet;
import java.util.Set;

public class ChunkerBlockEntity extends BlockEntity implements MenuProvider {
    private static final Logger LOGGER = LogUtils.getLogger();

    //private static final int MAX_ENERGY = 100000;
    //private static final int MAX_TRANSFER = 1000;
    //private static final int ENERGY_PER_CHUNK_PER_TICK = 20;
    //private static final int MAX_RADIUS = 3;

    private CustomEnergyStorage energyStorage;
    private final LazyOptional<IEnergyStorage> energy = LazyOptional.of(() -> energyStorage);

    private boolean isActive = false;
    private int radius = 1;
    private Set<ChunkPos> loadedChunks = new HashSet<>();
    private int tickCounter = 0;
    private long totalEnergyUsed = 0;
    private int uptime = 0;

    public ChunkerBlockEntity(BlockPos pos, BlockState state) {
        super(Chunker.CHUNKER_BLOCK_ENTITY.get(), pos, state);
        this.energyStorage = new CustomEnergyStorage(ChunkerConfig.MAX_ENERGY.get(),
                ChunkerConfig.MAX_TRANSFER.get(), this);
    }

    public void tick() {
        if (level == null || level.isClientSide) return;

        receiveEnergyFromNeighbors();

        tickCounter++;
        if (tickCounter >= 20) {
            tickCounter = 0;
            if (isActive) uptime++;
            sendUpdate();
        }

        int energyNeeded = calculateEnergyNeeded();

        if (energyStorage.getEnergyStored() >= energyNeeded) {
            if (!isActive) {
                activateChunkLoading();
            } else {
                energyStorage.extractEnergy(energyNeeded, false);
                totalEnergyUsed += energyNeeded;
            }
        } else if (isActive) {
            deactivateChunkLoading();
        }
    }

    public void sendUpdate() {
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    private int calculateEnergyNeeded() {
        //Might not work so keeping old commented out
        //return loadedChunks.size() * ENERGY_PER_CHUNK_PER_TICK;
        return ChunkerConfig.ENERGY_PER_CHUNK.get() * ((2 * radius + 1) * (2 * radius + 1));
    }

    private void receiveEnergyFromNeighbors() {
        if (level == null || level.isClientSide) return;

        if (energyStorage.getEnergyStored() >= energyStorage.getMaxEnergyStored()) return;

        boolean didReceiveEnergy = false;

        for (Direction direction : Direction.values()) {
            BlockPos neighborPos = worldPosition.relative(direction);
            BlockEntity neighborEntity = level.getBlockEntity(neighborPos);

            if (neighborEntity == null) continue;

            neighborEntity.getCapability(ForgeCapabilities.ENERGY, direction.getOpposite()).ifPresent(neighborEnergy -> {
                if (neighborEnergy.canExtract()) {
                    int maxExtract = Math.min(ChunkerConfig.MAX_TRANSFER.get(),
                            energyStorage.getMaxEnergyStored() - energyStorage.getEnergyStored());

                    int energyReceived = neighborEnergy.extractEnergy(maxExtract, false);

                    if (energyReceived > 0) {
                        energyStorage.receiveEnergy(energyReceived, false);
                        setChanged();
                    }
                }
            });
        }
    }

    private void activateChunkLoading() {
        if (level instanceof ServerLevel serverLevel) {
            isActive = true;
            ChunkerBlock.setActive(level, worldPosition, level.getBlockState(worldPosition), true);
            loadedChunks.clear();

            ChunkPos centerChunk = new ChunkPos(worldPosition);
            for (int x = -radius; x <= radius; x++) {
                for (int z = -radius; z <= radius; z++) {
                    ChunkPos chunkPos = new ChunkPos(centerChunk.x + x, centerChunk.z + z);
                    loadedChunks.add(chunkPos);
                    serverLevel.setChunkForced(chunkPos.x, chunkPos.z, true);
                }
            }

            setChanged();
            sendUpdate();

            LOGGER.debug("Chunker activated at " + worldPosition +
                    " (energy: " + energyStorage.getEnergyStored() + "/" + ChunkerConfig.MAX_ENERGY.get() +
                    ", chunks: " + loadedChunks.size() + ")");
        }
    }

    public void deactivateChunkLoading() {
        if (level instanceof ServerLevel serverLevel) {
            isActive = false;

            BlockState state = level.getBlockState(worldPosition);
            if (state.getBlock() instanceof ChunkerBlock) {
                ChunkerBlock.setActive(level, worldPosition, state, false);
            }

            for (ChunkPos chunkPos : loadedChunks) {
                serverLevel.setChunkForced(chunkPos.x, chunkPos.z, false);
            }
            loadedChunks.clear();
            setChanged();
            sendUpdate();
        }
    }

    private final ContainerData data = new ContainerData() {
        @Override
        public int get(int index) {
            return switch (index) {
                case 0 -> radius;
                case 1 -> isActive ? 1 : 0;
                case 2 -> isActive ? calculateTotalChunks() : 0;
                case 3 -> calculateEnergyNeeded();
                case 4 -> ChunkerConfig.MAX_RADIUS.get();
                case 5 -> ChunkerConfig.ENERGY_PER_CHUNK.get();
                case 6 -> ChunkerConfig.MAX_ENERGY.get();
                default -> 0;
            };
        }

        @Override
        public void set(int index, int value) {
            switch (index) {
                case 0 -> radius = value;
                case 1 -> isActive = value > 0;
            }
        }

        @Override
        public int getCount() {
            return 7;
        }
    };

    private int calculateTotalChunks() {
        return (2 * radius + 1) * (2 * radius + 1);
    }


    private long lastRadiusChangeTime = 0;

    public void setRadius(int newRadius) {
        int configMaxRadius = ChunkerConfig.MAX_RADIUS.get();
        if (newRadius >= 0 && newRadius <= configMaxRadius) {
            long now = System.currentTimeMillis();
            if (now - lastRadiusChangeTime < 200) {
                return;
            }
            lastRadiusChangeTime = now;

            boolean wasActive = isActive;
            if (wasActive) {
                deactivateChunkLoading();
            }

            radius = newRadius;
            setChanged();

            if (wasActive && level != null && !level.isClientSide) {
                activateChunkLoading();
            } else {
                sendUpdate();
            }
        }
    }

    public int getRadius() {
        return radius;
    }

    public int getMaxRadius() {
        return ChunkerConfig.MAX_RADIUS.get();
    }

    public boolean isActive() {
        return isActive;
    }

    public int getLoadedChunksCount() {
        return loadedChunks.size();
    }

    public long getTotalEnergyUsed() {
        return totalEnergyUsed;
    }

    public int getUptime() {
        return uptime;
    }

    public int getEnergyPerTick() {
        return calculateEnergyNeeded();
    }

    @Override
    public void onLoad() {
        super.onLoad();
    }

    @Override
    public void invalidateCaps() {
        super.invalidateCaps();
        energy.invalidate();
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        energyStorage.setEnergy(tag.getInt("Energy"));
        radius = tag.getInt("Radius");
        isActive = tag.getBoolean("Active");
        totalEnergyUsed = tag.getLong("TotalEnergyUsed");
        uptime = tag.getInt("Uptime");
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putInt("Energy", energyStorage.getEnergyStored());
        tag.putInt("Radius", radius);
        tag.putBoolean("Active", isActive);
        tag.putLong("TotalEnergyUsed", totalEnergyUsed);
        tag.putInt("Uptime", uptime);
        tag.putInt("ChunksCount", loadedChunks.size());
    }

    @Nonnull
    @Override
    public <T> LazyOptional<T> getCapability(@Nonnull Capability<T> cap, @Nullable Direction side) {
        if (cap == ForgeCapabilities.ENERGY) {
            return energy.cast();
        }
        return super.getCapability(cap, side);
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("block.chunker.chunker");
    }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory inventory, Player player) {
        return new ChunkerMenu(containerId, inventory, this, this.data);
    }

    private static class CustomEnergyStorage extends EnergyStorage {
        private final ChunkerBlockEntity blockEntity;

        public CustomEnergyStorage(int capacity, int maxTransfer, ChunkerBlockEntity blockEntity) {
            super(capacity, maxTransfer, maxTransfer);
            this.blockEntity = blockEntity;
        }

        protected void setEnergy(int energy) {
            int oldEnergy = this.energy;
            this.energy = Math.max(0, Math.min(capacity, energy));
            if (oldEnergy != this.energy) {
                blockEntity.setChanged();
            }
        }

        @Override
        public int receiveEnergy(int maxReceive, boolean simulate) {
            int energyReceived = super.receiveEnergy(maxReceive, simulate);
            if (energyReceived > 0 && !simulate) {
                blockEntity.setChanged();
            }
            return energyReceived;
        }

        @Override
        public int extractEnergy(int maxExtract, boolean simulate) {
            int energyExtracted = super.extractEnergy(maxExtract, simulate);
            if (energyExtracted > 0 && !simulate) {
                blockEntity.setChanged();
            }
            return energyExtracted;
        }
    }

    @Override
    public CompoundTag getUpdateTag() {
        CompoundTag tag = new CompoundTag();
        saveAdditional(tag);
        return tag;
    }

    @Override
    public void setChanged() {
        super.setChanged();
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    @Nullable
    @Override
    public net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket getUpdatePacket() {
        return net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public void onDataPacket(net.minecraft.network.Connection net, net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket pkt) {
        CompoundTag tag = pkt.getTag();
        if (tag != null) {
            handleUpdateTag(tag);
        }
    }

    @Override
    public void handleUpdateTag(CompoundTag tag) {
        load(tag);
    }
}
