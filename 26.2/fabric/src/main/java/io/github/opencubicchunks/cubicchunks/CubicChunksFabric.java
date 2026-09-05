package io.github.opencubicchunks.cubicchunks;

import io.github.opencubicchunks.cubicchunks.api.world.IMinMaxHeight;
import io.github.opencubicchunks.cubicchunks.network.CubicNetwork;
import io.github.opencubicchunks.cubicchunks.network.PacketCubicWorldData;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.server.level.ServerPlayer;

/**
 * Fabric entry point for MC 26.2, thin wrapper only.
 *
 * <p>All shared logic lives in {@link CubicChunksCommon}; platform services are
 * resolved via {@code FabricPlatformHelper} and {@code FabricVersionHelper}
 * (registered as {@code META-INF/services} providers).
 */
public class CubicChunksFabric implements ModInitializer {

    @Override
    public void onInitialize() {
        CubicChunksCommon.init();

        // Smoke-check the IMinMaxHeight height seam once the server's levels exist.
        ServerLifecycleEvents.SERVER_STARTED.register(CubicChunksCommon::verifyHeightSeam);

        // On join, tell the client the world's cubic build-height range (S2C round-trip test).
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            ServerPlayer player = handler.player;
            if (player.level() instanceof IMinMaxHeight height) {
                CubicNetwork.sendToPlayer(player,
                        new PacketCubicWorldData(height.getMinHeight(), height.getMaxHeight()));
            }
        });
    }
}
