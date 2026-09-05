package io.github.opencubicchunks.cubicchunks.network;

import io.github.opencubicchunks.cubicchunks.CubicChunksCommon;
import io.github.opencubicchunks.cubicchunks.world.ICubicWorldHeightData;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.level.Level;

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
    public void handle(ClientPacketContext ctx) {
        Level level = ctx.level();
        if (level instanceof ICubicWorldHeightData data) {
            data.cubicchunks$setHeightData(minHeight, maxHeight);
            CubicChunksCommon.LOGGER.info("Applied cubic world data to client level {}: minHeight={}, maxHeight(exclusive)={}",
                    level.dimension().identifier(), minHeight, maxHeight);
        } else {
            CubicChunksCommon.LOGGER.warn("Received cubic world data but client level {} is not ICubicWorldHeightData "
                            + "(LevelMixin did not apply)",
                    level == null ? "<none>" : level.dimension().identifier());
        }
    }

    public int minHeight() {
        return minHeight;
    }

    public int maxHeight() {
        return maxHeight;
    }
}
