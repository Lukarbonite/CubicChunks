package io.github.opencubicchunks.cubicchunks.network;

import net.minecraft.world.level.Level;

import javax.annotation.Nullable;

/**
 * Receive-side context handed to {@link CubicPacket#handle} for server-to-client packets.
 *
 * <p>Lets {@code common} packet logic reach the client's current level without referencing any
 * client-only classes directly (the loader layer supplies the level on the client thread).
 */
@FunctionalInterface
public interface ClientPacketContext {

    /** The client's current level, or {@code null} if none is loaded. */
    @Nullable
    Level level();
}
