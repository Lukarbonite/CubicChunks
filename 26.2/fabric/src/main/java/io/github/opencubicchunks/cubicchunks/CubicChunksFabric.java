package io.github.opencubicchunks.cubicchunks;

import io.github.opencubicchunks.cubicchunks.api.world.IMinMaxHeight;
import io.github.opencubicchunks.cubicchunks.network.CubicNetwork;
import io.github.opencubicchunks.cubicchunks.network.PacketCubicWorldData;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerChunkEvents;
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

        // Smoke-check the IMinMaxHeight height seam and the core cube storage once the server exists.
        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            CubicChunksCommon.verifyHeightSeam(server);
            CubicChunksCommon.verifyCubeStorage();
            CubicChunksCommon.verifyColumn();
            CubicChunksCommon.verifyCubeEntities(server);
            CubicChunksCommon.verifyCubeProvider();
            CubicChunksCommon.verifyCubicWorld(server);
            CubicChunksCommon.verifyCubeSerialization();
            CubicChunksCommon.verifyColumnSerialization();
            CubicChunksCommon.verifyCubeStorageDisk();
            CubicChunksCommon.verifyCubeProviderStorage();
            CubicChunksCommon.verifyBlockBridging(server);
            CubicChunksCommon.verifyHeightExtension(server);
            CubicChunksCommon.verifySectionStorage(server);
            CubicChunksCommon.verifyLiveSectionAccess(server);
            CubicChunksCommon.verifyPopulation(server);
            CubicChunksCommon.verifyPersistence();
            CubicChunksCommon.verifyExtendedHeight(server);
            CubicChunksCommon.verifyTicketSystem(server);
        });

        // When population is enabled (-Dcubicchunks.population=true), put each chunk into cubic mode as
        // it loads (restoring saved cube edits, else mirroring vanilla), and persist it on unload so
        // edits survive. Storages are closed when the server stops.
        ServerChunkEvents.CHUNK_LOAD.register(CubicChunksCommon::onChunkLoadPopulate);
        ServerChunkEvents.CHUNK_UNLOAD.register(CubicChunksCommon::onChunkUnloadPersist);
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> CubicChunksCommon.closeStorages());

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
