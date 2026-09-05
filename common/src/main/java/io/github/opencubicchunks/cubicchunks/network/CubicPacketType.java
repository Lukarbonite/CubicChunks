package io.github.opencubicchunks.cubicchunks.network;

import net.minecraft.network.FriendlyByteBuf;

import java.util.function.Function;

/**
 * Type descriptor for a {@link CubicPacket}: a stable id (path within the {@code cubicchunks}
 * namespace) plus a decoder that reconstructs the packet from a buffer.
 *
 * @param <T> the concrete packet type
 */
public final class CubicPacketType<T extends CubicPacket> {

    private final String id;
    private final Function<FriendlyByteBuf, T> decoder;

    public CubicPacketType(String id, Function<FriendlyByteBuf, T> decoder) {
        this.id = id;
        this.decoder = decoder;
    }

    /** Path segment identifying this packet within the {@code cubicchunks} namespace. */
    public String id() {
        return id;
    }

    /** Reads a packet instance from the buffer. */
    public T decode(FriendlyByteBuf buf) {
        return decoder.apply(buf);
    }
}
