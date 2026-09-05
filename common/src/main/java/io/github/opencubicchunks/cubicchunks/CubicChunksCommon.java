package io.github.opencubicchunks.cubicchunks;

import io.github.opencubicchunks.cubicchunks.api.util.CubePos;
import io.github.opencubicchunks.cubicchunks.api.world.ICube;
import io.github.opencubicchunks.cubicchunks.api.world.IColumn;
import io.github.opencubicchunks.cubicchunks.api.world.ICubicWorldServer;
import io.github.opencubicchunks.cubicchunks.api.world.IHeightMap;
import io.github.opencubicchunks.cubicchunks.api.world.IMinMaxHeight;
import io.github.opencubicchunks.cubicchunks.network.CubicNetwork;
import io.github.opencubicchunks.cubicchunks.api.world.ICubeProviderServer;
import io.github.opencubicchunks.cubicchunks.api.world.storage.ICubicStorage;
import io.github.opencubicchunks.cubicchunks.platform.PlatformHelper;
import io.github.opencubicchunks.cubicchunks.platform.VersionHelper;
import io.github.opencubicchunks.cubicchunks.server.CubeProviderServer;
import io.github.opencubicchunks.cubicchunks.world.ServerHeightMap;
import io.github.opencubicchunks.cubicchunks.world.column.Column;
import io.github.opencubicchunks.cubicchunks.world.column.CubeMap;
import io.github.opencubicchunks.cubicchunks.world.cube.Cube;
import io.github.opencubicchunks.cubicchunks.world.cube.EntityContainer;
import io.github.opencubicchunks.cubicchunks.world.storage.NbtFileCubicStorage;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.concurrent.atomic.AtomicInteger;

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

    /**
     * Smoke-checks the cube entity and block-entity slice: a fresh cube starts with no entities or
     * block entities, then a real entity (a pig, created against the overworld but never added to the
     * world) is added, counted and removed through the {@link Cube}'s {@link EntityContainer}. Needs a
     * running server so an {@code EntityType} can construct an entity.
     */
    public static void verifyCubeEntities(MinecraftServer server) {
        Cube cube = new Cube(new CubePos(0, 0, 0));
        boolean emptyStart = cube.getEntities().isEmpty()
                && cube.getBlockEntities().isEmpty()
                && cube.getBlockEntity(new BlockPos(1, 2, 3)) == null;

        ServerLevel overworld = server.overworld();
        EntityType<?> pigType = BuiltInRegistries.ENTITY_TYPE.getValue(Identifier.withDefaultNamespace("pig"));
        Entity pig = pigType.create(overworld, EntitySpawnReason.COMMAND);
        boolean addRemoveOk = false;
        if (pig != null) {
            cube.addEntity(pig);
            boolean added = cube.getEntities().size() == 1 && cube.getEntities().contains(pig);
            boolean removed = cube.removeEntity(pig) && cube.getEntities().isEmpty();
            addRemoveOk = added && removed;
        } else {
            LOGGER.warn("Cube entity check: could not create a test entity (pig create returned null)");
        }

        LOGGER.info("Cube entity check: emptyStart={}, addRemoveOk={}", emptyStart, addRemoveOk);
    }

    /**
     * Smoke-checks the in-memory {@link CubeProviderServer}: cached lookups miss before anything is
     * provided, {@code provideColumn} caches and is idempotent, cube lookups route through the column,
     * and {@code GET_CACHED} never creates while higher requirements do. Purely in-memory, so no
     * server is needed.
     */
    public static void verifyCubeProvider() {
        CubeProviderServer provider = new CubeProviderServer();

        boolean emptyStart = provider.getLoadedColumn(2, 3) == null
                && provider.getColumn(2, 3, ICubeProviderServer.Requirement.GET_CACHED) == null
                && provider.getLoadedColumnCount() == 0;

        IColumn provided = provider.provideColumn(2, 3);
        boolean columnCached = provided != null
                && provider.getLoadedColumn(2, 3) == provided
                && provider.provideColumn(2, 3) == provided // idempotent
                && provider.getLoadedColumnCount() == 1;

        boolean cubeMissBeforeCreate = provider.getLoadedCube(2, 5, 3) == null
                && provider.getCube(2, 5, 3, ICubeProviderServer.Requirement.GET_CACHED) == null;

        ICube cube = provider.getCube(new CubePos(2, 5, 3));
        boolean cubeCreated = cube != null
                && provider.getLoadedCube(2, 5, 3) == cube
                && provider.isCubeGenerated(2, 5, 3)
                && !provider.isCubeGenerated(9, 9, 9);

        boolean unloadOk = provider.unloadColumn(2, 3) == provided
                && provider.getLoadedColumn(2, 3) == null
                && provider.getLoadedColumnCount() == 0;

        LOGGER.info("Cube provider check: emptyStart={}, columnCached={}, cubeMissBeforeCreate={}, "
                        + "cubeCreated={}, unloadOk={}",
                emptyStart, columnCached, cubeMissBeforeCreate, cubeCreated, unloadOk);
    }

    /**
     * Smoke-checks the world-side integration: every server level should be castable to
     * {@link ICubicWorldServer} (via {@code ServerLevelMixin}), expose a lazily-cached cube cache, and
     * route {@code getCubeFromCubeCoords}/{@code getCubeFromBlockCoords} through that provider so the
     * same cube instance comes back either way. Logs a warning instead of throwing if the mixin did
     * not apply.
     */
    public static void verifyCubicWorld(MinecraftServer server) {
        ServerLevel overworld = server.overworld();
        if (!(overworld instanceof ICubicWorldServer world)) {
            LOGGER.warn("Cubic world NOT applied for overworld: level is not ICubicWorldServer "
                    + "(ServerLevelMixin did not inject)");
            return;
        }

        boolean cacheOk = world.isCubicWorld()
                && world.getCubeCache() != null
                && world.getCubeCache() == world.getCubeCache(); // lazily cached, same instance

        ICube viaCubeCoords = world.getCubeFromCubeCoords(10, 2, 10);
        BlockPos inside = new BlockPos(10 * ICube.SIZE + 1, 2 * ICube.SIZE + 1, 10 * ICube.SIZE + 1);
        ICube viaBlockCoords = world.getCubeFromBlockCoords(inside);
        boolean routingOk = viaCubeCoords != null
                && viaBlockCoords == viaCubeCoords
                && world.getCubeCache().getLoadedCube(10, 2, 10) == viaCubeCoords
                && world.isBlockColumnLoaded(inside);

        boolean heightOk = world.getMinGenerationHeight() == world.getMinHeight()
                && world.getMaxGenerationHeight() == world.getMaxHeight();

        LOGGER.info("Cubic world check: cacheOk={}, routingOk={}, heightOk={}, genRange=[{}, {}]",
                cacheOk, routingOk, heightOk, world.getMinGenerationHeight(), world.getMaxGenerationHeight());
    }

    /**
     * Smoke-checks cube block-storage serialization: a cube with a couple of blocks set survives a
     * write/read NBT round trip (states and empty-tracking preserved), and an all-air cube round trips
     * back to empty. Runs after the server starts so the block registry is populated.
     */
    public static void verifyCubeSerialization() {
        BlockState stone = Blocks.STONE.defaultBlockState();
        BlockState dirt = Blocks.DIRT.defaultBlockState();

        Cube source = new Cube(new CubePos(0, 0, 0));
        source.setBlockState(1, 2, 3, stone);
        source.setBlockState(4, 5, 6, dirt);

        CompoundTag tag = source.writeToNbt(new CompoundTag());

        Cube restored = new Cube(new CubePos(0, 0, 0));
        restored.readFromNbt(tag);

        boolean statesOk = restored.getBlockState(1, 2, 3) == stone
                && restored.getBlockState(4, 5, 6) == dirt
                && restored.getBlockState(0, 0, 0) == Blocks.AIR.defaultBlockState();
        boolean emptyTrackingOk = !restored.isEmpty();

        Cube emptySource = new Cube(new CubePos(0, 0, 0));
        Cube emptyRestored = new Cube(new CubePos(0, 0, 0));
        emptyRestored.readFromNbt(emptySource.writeToNbt(new CompoundTag()));
        boolean emptyRoundTripOk = emptyRestored.isEmpty();

        LOGGER.info("Cube serialization check: statesOk={}, emptyTrackingOk={}, emptyRoundTripOk={}",
                statesOk, emptyTrackingOk, emptyRoundTripOk);
    }

    /**
     * Smoke-checks column serialization: a column with a couple of cubes (one holding a block) and a
     * non-trivial opacity index survives a write/read NBT round trip. The restored column has the same
     * cubes at the same Ys, the block state is preserved, and the height map reports the same top block.
     */
    public static void verifyColumnSerialization() {
        BlockState stone = Blocks.STONE.defaultBlockState();

        Column source = new Column(3, 4);
        Cube bottom = new Cube(new CubePos(3, 0, 4));
        bottom.setBlockState(1, 1, 1, stone);
        source.addCube(bottom);
        source.addCube(new Cube(new CubePos(3, 5, 4))); // empty cube higher up
        source.getOpacityIndex().onOpacityChange(2, 70, 2, 255);

        CompoundTag tag = source.writeToNbt(new CompoundTag());

        Column restored = new Column(3, 4);
        restored.readFromNbt(tag);

        ICube restoredBottom = restored.getLoadedCube(0);
        boolean cubesOk = restoredBottom != null
                && restored.getLoadedCube(5) != null
                && restored.getLoadedCube(1) == null;
        boolean blockOk = restoredBottom != null
                && restoredBottom.getBlockState(new BlockPos(3 * ICube.SIZE + 1, 1, 4 * ICube.SIZE + 1)) == stone;
        boolean heightMapOk = restored.getOpacityIndex().getTopBlockY(2, 2) == 70;

        LOGGER.info("Column serialization check: cubesOk={}, blockOk={}, heightMapOk={}",
                cubesOk, blockOk, heightMapOk);
    }

    /**
     * Smoke-checks the {@link NbtFileCubicStorage} disk backend end to end in a temporary directory:
     * write a column and a cube, confirm existence and that missing positions read back {@code null},
     * read them back and verify a block state survived the disk round trip, and confirm the
     * {@code forEach} iterators enumerate exactly what was written. The temp directory is removed
     * afterwards.
     */
    public static void verifyCubeStorageDisk() {
        BlockState stone = Blocks.STONE.defaultBlockState();
        Path dir = null;
        try {
            dir = Files.createTempDirectory("cubicchunks-storage-test");
            try (ICubicStorage storage = new NbtFileCubicStorage(dir)) {
                ChunkPos columnPos = new ChunkPos(3, 4);
                CubePos cubePos = new CubePos(3, 0, 4);

                Column column = new Column(3, 4);
                Cube cube = new Cube(cubePos);
                cube.setBlockState(1, 1, 1, stone);
                column.addCube(cube);

                storage.writeColumn(columnPos, column.writeToNbt(new CompoundTag()));
                storage.writeCube(cubePos, cube.writeToNbt(new CompoundTag()));
                storage.flush();

                boolean existsOk = storage.columnExists(columnPos)
                        && storage.cubeExists(cubePos)
                        && !storage.columnExists(new ChunkPos(99, 99))
                        && !storage.cubeExists(new CubePos(99, 99, 99))
                        && storage.readColumn(new ChunkPos(99, 99)) == null;

                CompoundTag cubeTag = storage.readCube(cubePos);
                Cube restored = new Cube(cubePos);
                restored.readFromNbt(cubeTag);
                boolean readOk = cubeTag != null
                        && restored.getBlockState(1, 1, 1) == stone;

                AtomicInteger columnCount = new AtomicInteger();
                AtomicInteger cubeCount = new AtomicInteger();
                storage.forEachColumn(pos -> columnCount.incrementAndGet());
                storage.forEachCube(pos -> cubeCount.incrementAndGet());
                boolean iterOk = columnCount.get() == 1 && cubeCount.get() == 1;

                LOGGER.info("Cube storage disk check: existsOk={}, readOk={}, iterOk={}",
                        existsOk, readOk, iterOk);
            }
        } catch (IOException e) {
            LOGGER.error("Cube storage disk check FAILED with IO error", e);
        } finally {
            deleteRecursively(dir);
        }
    }

    /**
     * Smoke-checks the provider/storage integration: a storage-backed provider generates a cube, has a
     * block set on it, and saves. A fresh provider over the same storage then misses in cache but loads
     * the cube (and its column) from disk on {@code LOAD}, with the block preserved, while a position
     * that was never written stays {@code null} under {@code LOAD}. Uses a temporary directory.
     */
    public static void verifyCubeProviderStorage() {
        BlockState stone = Blocks.STONE.defaultBlockState();
        Path dir = null;
        try {
            dir = Files.createTempDirectory("cubicchunks-provider-storage-test");
            try (ICubicStorage storage = new NbtFileCubicStorage(dir)) {
                CubeProviderServer writer = new CubeProviderServer(storage);
                Cube cube = (Cube) writer.getCube(7, 1, 7, ICubeProviderServer.Requirement.GENERATE);
                cube.setBlockState(2, 2, 2, stone);
                writer.saveAll();

                CubeProviderServer reader = new CubeProviderServer(storage);
                boolean cacheMiss = reader.getLoadedCube(7, 1, 7) == null
                        && reader.getColumn(7, 7, ICubeProviderServer.Requirement.GET_CACHED) == null;

                ICube loaded = reader.getCube(7, 1, 7, ICubeProviderServer.Requirement.LOAD);
                BlockPos worldPos = new BlockPos(7 * ICube.SIZE + 2, 1 * ICube.SIZE + 2, 7 * ICube.SIZE + 2);
                boolean loadOk = loaded != null && loaded.getBlockState(worldPos) == stone;

                boolean nowCached = reader.getColumn(7, 7, ICubeProviderServer.Requirement.GET_CACHED) != null
                        && reader.getLoadedCube(7, 1, 7) != null;

                boolean missStaysNull = reader.getCube(50, 50, 50, ICubeProviderServer.Requirement.LOAD) == null;
                boolean generatedFlags = reader.isCubeGenerated(7, 1, 7) && !reader.isCubeGenerated(50, 50, 50);

                LOGGER.info("Cube provider/storage check: cacheMiss={}, loadOk={}, nowCached={}, "
                                + "missStaysNull={}, generatedFlags={}",
                        cacheMiss, loadOk, nowCached, missStaysNull, generatedFlags);
            }
        } catch (IOException e) {
            LOGGER.error("Cube provider/storage check FAILED with IO error", e);
        } finally {
            deleteRecursively(dir);
        }
    }

    private static void deleteRecursively(@javax.annotation.Nullable Path dir) {
        if (dir == null) {
            return;
        }
        try (var paths = Files.walk(dir)) {
            paths.sorted(Comparator.reverseOrder()).forEach(p -> {
                try {
                    Files.deleteIfExists(p);
                } catch (IOException ignored) {
                    // best-effort cleanup of the temp directory
                }
            });
        } catch (IOException ignored) {
            // best-effort cleanup of the temp directory
        }
    }
}
