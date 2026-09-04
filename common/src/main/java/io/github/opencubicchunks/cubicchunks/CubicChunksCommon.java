package io.github.opencubicchunks.cubicchunks;

import io.github.opencubicchunks.cubicchunks.platform.PlatformHelper;
import io.github.opencubicchunks.cubicchunks.platform.VersionHelper;
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

        LOGGER.info("{} common init — platform '{}', Minecraft {}",
                MOD_NAME,
                PlatformHelper.get().getPlatformName(),
                VersionHelper.INSTANCE.minecraftVersion());
    }
}
