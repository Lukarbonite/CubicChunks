package io.github.opencubicchunks.cubicchunks.network;

import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Fabric {@link CustomPacketPayload} wrapper around a platform-neutral {@link CubicPacket}.
 *
 * <p>One wrapper is used for every packet; each registered {@link CubicPacketType} gets its own
 * {@link CustomPacketPayload.Type}, carried per-instance so {@link #type()} resolves correctly.
 */
public record CubicPayload(CustomPacketPayload.Type<CubicPayload> payloadType, CubicPacket packet)
        implements CustomPacketPayload {

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return payloadType;
    }
}
