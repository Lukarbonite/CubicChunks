package io.github.opencubicchunks.cubicchunks.network;

import io.github.opencubicchunks.cubicchunks.CubicChunksCommon;
import net.minecraft.network.FriendlyByteBuf;

/**
 * Server-to-client packet announcing a world's cubic build-height range on join.
 *
 * <p>On 1.12.2 the equivalent packet also carried world-type and generator settings so the client
 * could set itself up as a cubic world. For now it carries just the height range (the seam
 * established by {@code LevelHeightAccessorMixin} / {@code IMinMaxHeight}); more fields are added as
 * the world subsystem is ported.
 */
public class PacketCubicWorldData implements CubicPacket {

    private final int minHeight;
    private final int maxHeight;

    public PacketCubicWorldData(int minHeight, int maxHeight) {
        this.minHeight = minHeight;
        this.maxHeight = maxHeight;
    }

    public PacketCubicWorldData(FriendlyByteBuf buf) {
        this.minHeight = buf.readInt();
        this.maxHeight = buf.readInt();
    }

    @Override
    public CubicPacketType<?> type() {
        return CubicNetwork.CUBIC_WORLD_DATA;
    }

    @Override
    public void write(FriendlyByteBuf buf) {
        buf.writeInt(minHeight);
        buf.writeInt(maxHeight);
    }

    @Override
    public void handle() {
        CubicChunksCommon.LOGGER.info("Client received cubic world data: minHeight={}, maxHeight(exclusive)={}",
                minHeight, maxHeight);
    }

    public int minHeight() {
        return minHeight;
    }

    public int maxHeight() {
        return maxHeight;
    }
}
