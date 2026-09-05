package io.github.opencubicchunks.cubicchunks.network;

import net.minecraft.server.level.ServerPlayer;

import java.util.ServiceLoader;

/**
 * SPI abstraction over the mod loader's networking layer (Fabric custom payloads,
 * Forge {@code SimpleNetworkWrapper}, etc.).
 *
 * <p>Each platform module provides one implementation, registered via
 * {@code META-INF/services/io.github.opencubicchunks.cubicchunks.network.NetworkHelper}.
 */
public interface NetworkHelper {

    NetworkHelper INSTANCE = ServiceLoader.load(NetworkHelper.class)
            .findFirst()
            .orElseThrow(() -> new IllegalStateException(
                    "No NetworkHelper implementation found on the classpath. " +
                    "Ensure the platform JAR provides META-INF/services/" +
                    "io.github.opencubicchunks.cubicchunks.network.NetworkHelper"));

    /**
     * Registers a server-to-client packet type. Must be called during mod init on both sides
     * (the server needs it to encode, the client to decode).
     */
    void registerS2CType(CubicPacketType<?> type);

    /** Sends a packet to a single player (server-to-client). */
    void sendToPlayer(ServerPlayer player, CubicPacket packet);
}
