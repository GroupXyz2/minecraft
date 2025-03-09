package de.groupxyz.chunker;

import de.groupxyz.chunker.ChunkerBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class PacketChangeRadius {
    private final BlockPos pos;
    private final int radius;

    public PacketChangeRadius(BlockPos pos, int radius) {
        this.pos = pos;
        this.radius = radius;
    }

    public static void encode(PacketChangeRadius msg, FriendlyByteBuf buf) {
        buf.writeBlockPos(msg.pos);
        buf.writeInt(msg.radius);
    }

    public static PacketChangeRadius decode(FriendlyByteBuf buf) {
        return new PacketChangeRadius(buf.readBlockPos(), buf.readInt());
    }

    public static void handle(PacketChangeRadius msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player != null) {
                BlockEntity be = player.level().getBlockEntity(msg.pos);
                if (be instanceof ChunkerBlockEntity chunker) {
                    int maxRadius = ChunkerConfig.MAX_RADIUS.get();
                    int newRadius = Math.min(Math.max(0, msg.radius), maxRadius);
                    chunker.setRadius(newRadius);
                }
            }
        });
        ctx.get().setPacketHandled(true);
    }
}