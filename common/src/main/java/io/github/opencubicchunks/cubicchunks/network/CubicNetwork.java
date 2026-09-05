package io.github.opencubicchunks.cubicchunks.network;

import net.minecraft.server.level.ServerPlayer;

/**
 * Central registry and entry point for CubicChunks networking. Declares the packet types and
 * routes sends through the platform {@link NetworkHelper}.
 *
 * <p>As the port grows this is where the remaining 1.12.2 packets (cube data, block changes,
 * skylight, heightmap updates, unloads) are re-registered.
 */
public final class CubicNetwork {

    /** Server-to-client world metadata (currently the cubic build-height range). */
    public static final CubicPacketType<PacketCubicWorldData> CUBIC_WORLD_DATA =
            new CubicPacketType<>("cubic_world_data", PacketCubicWorldData::new);

    private CubicNetwork() {
    }

    /** Registers all packet types. Call once during mod init (both sides). */
    public static void registerTypes() {
        NetworkHelper.INSTANCE.registerS2CType(CUBIC_WORLD_DATA);
    }

    /** Sends a packet to a single player (server-to-client). */
    public static void sendToPlayer(ServerPlayer player, CubicPacket packet) {
        NetworkHelper.INSTANCE.sendToPlayer(player, packet);
    }
}
