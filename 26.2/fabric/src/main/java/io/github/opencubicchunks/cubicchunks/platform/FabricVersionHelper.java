package io.github.opencubicchunks.cubicchunks.platform;

/**
 * MC 26.2 implementation of {@link VersionHelper}.
 *
 * <p>Registered as a service provider via
 * {@code META-INF/services/io.github.opencubicchunks.cubicchunks.platform.VersionHelper}.
 * Version-divergent vanilla calls are added here as the port grows.
 */
public final class FabricVersionHelper implements VersionHelper {

    @Override
    public String minecraftVersion() {
        return "26.2";
    }
}
