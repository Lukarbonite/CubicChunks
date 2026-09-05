package io.github.opencubicchunks.cubicchunks;

import io.github.opencubicchunks.cubicchunks.api.util.CubePos;
import io.github.opencubicchunks.cubicchunks.api.world.ICube;
import io.github.opencubicchunks.cubicchunks.api.world.IHeightMap;
import io.github.opencubicchunks.cubicchunks.api.world.IMinMaxHeight;
import io.github.opencubicchunks.cubicchunks.network.CubicNetwork;
import io.github.opencubicchunks.cubicchunks.platform.PlatformHelper;
import io.github.opencubicchunks.cubicchunks.platform.VersionHelper;
import io.github.opencubicchunks.cubicchunks.world.ServerHeightMap;
import io.github.opencubicchunks.cubicchunks.world.column.Column;
import io.github.opencubicchunks.cubicchunks.world.column.CubeMap;
import io.github.opencubicchunks.cubicchunks.world.cube.Cube;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
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

    /** Lowest block Y CubicChunks supports (leaves a margin below {@link Integer#MIN_VALUE}). */
    public static final int MIN_SUPPORTED_BLOCK_Y = Integer.MIN_VALUE + 4096;

    /** Highest block Y CubicChunks supports (leaves a margin below {@link Integer#MAX_VALUE}). */
    public static final int MAX_SUPPORTED_BLOCK_Y = Integer.MAX_VALUE - 4095;

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

    /**
     * Smoke-checks the core cube block storage: builds a cube, writes and reads a block, and checks
     * the empty-tracking transitions. Runs after the server starts, when the block registry is
     * populated (a cube's {@code PalettedContainer} needs {@code Block.BLOCK_STATE_REGISTRY}).
     */
    public static void verifyCubeStorage() {
        Cube cube = new Cube(new CubePos(0, 0, 0));
        boolean emptyInitially = cube.isEmpty();
        BlockState stone = Blocks.STONE.defaultBlockState();
        cube.setBlockState(1, 2, 3, stone);
        BlockState readBack = cube.getBlockState(1, 2, 3);
        boolean roundTripped = readBack == stone;
        cube.setBlockState(1, 2, 3, Blocks.AIR.defaultBlockState());
        LOGGER.info("Cube storage check: emptyInitially={}, roundTripStone={}, emptyAfterClear={}",
                emptyInitially, roundTripped, cube.isEmpty());

        // Column-side cube container: insert out of order, confirm sorted range + get/remove.
        CubeMap column = new CubeMap();
        column.put(new Cube(new CubePos(0, 5, 0)));
        column.put(new Cube(new CubePos(0, -3, 0)));
        column.put(new Cube(new CubePos(0, 1, 0)));
        StringBuilder order = new StringBuilder();
        for (Cube c : column.cubes(-8, 8)) {
            order.append(c.getY()).append(' ');
        }
        boolean getWorks = column.get(1) != null && column.get(99) == null;
        boolean removeWorks = column.remove(5) != null && column.get(5) == null;
        LOGGER.info("CubeMap check: sortedOrder=[{}], get={}, remove={}",
                order.toString().trim(), getWorks, removeWorks);
    }

    /**
     * Smoke-checks the column and the ported {@link ServerHeightMap}: column cube management via its
     * {@code CubeMap}, and a heightmap opacity write/read round trip.
     */
    public static void verifyColumn() {
        Column col = new Column(3, 4);
        col.addCube(new Cube(new CubePos(3, 0, 4)));
        ICube created = col.getCube(1); // get-or-create at y=1
        boolean columnOk = col.getX() == 3 && col.getZ() == 4
                && col.hasLoadedCubes()
                && col.getLoadedCube(0) != null
                && created != null
                && col.getLoadedCube(2) == null;

        IHeightMap heightMap = new ServerHeightMap(new int[ICube.SIZE * ICube.SIZE]);
        heightMap.onOpacityChange(0, 70, 0, 255);
        int topY = heightMap.getTopBlockY(0, 0);

        LOGGER.info("Column check: columnOk={}, heightMapTopY(after opaque@70)={}", columnOk, topY);
    }
}
