package io.github.opencubicchunks.cubicchunks.platform;

import net.fabricmc.loader.api.FabricLoader;

import java.nio.file.Path;

/**
 * Fabric implementation of {@link PlatformHelper}.
 *
 * <p>Registered as a service provider via
 * {@code META-INF/services/io.github.opencubicchunks.cubicchunks.platform.PlatformHelper}.
 */
public final class FabricPlatformHelper implements PlatformHelper {

    @Override
    public Path getConfigDir() {
        return FabricLoader.getInstance().getConfigDir();
    }

    @Override
    public String getPlatformName() {
        return "fabric";
    }

    @Override
    public boolean isModLoadedEarly(String modId) {
        return FabricLoader.getInstance().isModLoaded(modId);
    }
}
