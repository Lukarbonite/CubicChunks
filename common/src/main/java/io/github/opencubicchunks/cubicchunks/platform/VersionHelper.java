package io.github.opencubicchunks.cubicchunks.platform;

import java.util.ServiceLoader;

/**
 * SPI abstraction for Minecraft-version-specific bridges.
 *
 * <p>Where {@link PlatformHelper} abstracts over the mod loader (Fabric / NeoForge),
 * {@code VersionHelper} abstracts over differences between Minecraft versions whose
 * APIs diverge (renamed / re-signatured vanilla methods, changed world-height access,
 * etc.). Each version module provides one implementation, registered via
 * {@code META-INF/services/io.github.opencubicchunks.cubicchunks.platform.VersionHelper}.
 *
 * <p>Add version-divergent vanilla calls here as the port grows, keeping
 * {@code common} free of any per-version {@code net.minecraft} signatures.
 */
public interface VersionHelper {

    VersionHelper INSTANCE = ServiceLoader.load(VersionHelper.class)
            .findFirst()
            .orElseThrow(() -> new RuntimeException(
                    "No VersionHelper implementation found on the classpath. " +
                    "Ensure the version JAR provides META-INF/services/" +
                    "io.github.opencubicchunks.cubicchunks.platform.VersionHelper"));

    /** Human-readable Minecraft version this module was built against, e.g. {@code "26.2"}. */
    String minecraftVersion();
}
