package io.github.opencubicchunks.cubicchunks.network;

import net.minecraft.network.FriendlyByteBuf;

/**
 * A platform-neutral CubicChunks packet.
 *
 * <p>Concrete packets live in {@code common} and implement serialization ({@link #write}) plus
 * receive-side logic ({@link #handle}); the loader layer ({@code NetworkHelper}) handles wire
 * registration and delivery. This replaces the 1.12.2 Forge {@code IMessage}/{@code IMessageHandler}
 * pair so the same packet code works across loaders.
 */
public interface CubicPacket {

    /** The registered type descriptor for this packet. */
    CubicPacketType<?> type();

    /** Writes this packet's payload to the buffer. */
    void write(FriendlyByteBuf buf);

    /**
     * Handles this packet on the receiving side. For S2C packets this runs on the client's main
     * thread (the loader layer schedules it there).
     */
    void handle();
}
