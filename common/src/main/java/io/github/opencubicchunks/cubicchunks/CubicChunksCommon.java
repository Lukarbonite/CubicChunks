package io.github.opencubicchunks.cubicchunks;

import io.github.opencubicchunks.cubicchunks.api.world.IMinMaxHeight;
import io.github.opencubicchunks.cubicchunks.network.CubicNetwork;
import io.github.opencubicchunks.cubicchunks.platform.PlatformHelper;
import io.github.opencubicchunks.cubicchunks.platform.VersionHelper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Platform-agnostic entry point for CubicChunks.
 *
 * <p>Each loader's thin entry point (e.g. {@code CubicChunksFabric}) calls {@link #init()};
 * all shared setup lives here. Platform services are reached through
 * {@link PlatformHelper} / {@link VersionHelper}, never through loader-specific APIs.
 */
public final class CubicChunksCommon {

    public static final String MOD_ID = "cubicchunks";
    public static final String MOD_NAME = "CubicChunks";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_NAME);

    private static boolean initialized = false;

    private CubicChunksCommon() {
    }

    /** Runs shared initialization. Safe to call once per loader entry point. */
    public static void init() {
        if (initialized) {
            return;
        }
        initialized = true;

        LOGGER.info("{} common init, platform '{}', Minecraft {}",
                MOD_NAME,
                PlatformHelper.get().getPlatformName(),
                VersionHelper.INSTANCE.minecraftVersion());

        CubicNetwork.registerTypes();
    }

    /**
     * Smoke-checks the {@link IMinMaxHeight} height seam once the server's levels exist: every
     * level should be castable to {@code IMinMaxHeight} (via {@code LevelHeightAccessorMixin}) and
     * report vanilla's build-height range. Logs a warning instead of throwing if the mixin did not
     * apply, so a mis-wired seam is visible without crashing the game.
     */
    public static void verifyHeightSeam(MinecraftServer server) {
        for (ServerLevel level : server.getAllLevels()) {
            String dim = level.dimension().identifier().toString();
            if (level instanceof IMinMaxHeight height) {
                LOGGER.info("Height seam OK for {}: min={}, max(exclusive)={} (vanilla minY={}, height={})",
                        dim, height.getMinHeight(), height.getMaxHeight(), level.getMinY(), level.getHeight());
            } else {
                LOGGER.warn("Height seam NOT applied for {}: level is not IMinMaxHeight "
                        + "(LevelHeightAccessorMixin did not inject)", dim);
            }
        }
    }
}
