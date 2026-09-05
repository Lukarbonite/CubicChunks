package io.github.opencubicchunks.cubicchunks.world;

/**
 * Internal duck interface injected onto {@code Level} (see {@code LevelMixin}) that holds a
 * CubicChunks build-height range overriding vanilla's.
 *
 * <p>On the client this is populated from {@code PacketCubicWorldData} on join, so the client level
 * reports the server's cubic height range through {@code IMinMaxHeight}. This is the internal
 * (non-API) counterpart to the read-only {@code IMinMaxHeight} seam.
 */
public interface ICubicWorldHeightData {

    /**
     * Sets the CubicChunks height range for this level.
     *
     * @param minHeight inclusive bottom Y
     * @param maxHeight exclusive top Y (the Y above the top block)
     */
    void cubicchunks$setHeightData(int minHeight, int maxHeight);

    /** True once a custom range has been set (otherwise vanilla height is reported). */
    boolean cubicchunks$hasCustomHeightData();
}
