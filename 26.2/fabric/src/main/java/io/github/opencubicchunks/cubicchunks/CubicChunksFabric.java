package io.github.opencubicchunks.cubicchunks;

import net.fabricmc.api.ModInitializer;

/**
 * Fabric entry point for MC 26.2 — thin wrapper only.
 *
 * <p>All shared logic lives in {@link CubicChunksCommon}; platform services are
 * resolved via {@code FabricPlatformHelper} and {@code FabricVersionHelper}
 * (registered as {@code META-INF/services} providers).
 */
public class CubicChunksFabric implements ModInitializer {

    @Override
    public void onInitialize() {
        CubicChunksCommon.init();
    }
}
