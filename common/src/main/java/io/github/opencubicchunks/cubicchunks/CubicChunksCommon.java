package io.github.opencubicchunks.cubicchunks;

import io.github.opencubicchunks.cubicchunks.api.util.Coords;
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
import io.github.opencubicchunks.cubicchunks.world.column.CubeSectionStorage;
import io.github.opencubicchunks.cubicchunks.world.cube.Cube;
import io.github.opencubicchunks.cubicchunks.world.cube.EntityContainer;
import io.github.opencubicchunks.cubicchunks.world.CubicLevelHeightAccessor;
import io.github.opencubicchunks.cubicchunks.world.ICubicLevelChunk;
import io.github.opencubicchunks.cubicchunks.world.storage.NbtFileCubicStorage;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ThreadedLevelLightEngine;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.PalettedContainerFactory;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
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

    /**
     * When true, chunks are mirrored into cube-backed columns and put into cubic mode as they load, so
     * the running game reads and writes through cubes (bounded to the vanilla height window). Off by
     * default; enable with {@code -Dcubicchunks.population=true}.
     *
     * <p><b>Experimental.</b> This is a memory-only demonstration: cube edits are not yet guaranteed to
     * be persisted (save-path redirection is not implemented), so do not enable it on a world you care
     * about.
     */
    public static volatile boolean populationEnabled = false;

    /**
     * Extended build-height window applied to levels while {@link #populationEnabled} is on. A bounded,
     * section-aligned range (not the full cubic range) so vanilla's eager per-chunk section allocation
     * stays affordable, while still letting you build well beyond vanilla's -64..320. Vanilla terrain
     * generates within its own noise range; the extra space is air you can build in, backed by cubes.
     */
    public static final int EXTENDED_MIN_Y = -128;

    /**
     * Height in blocks of the {@link #EXTENDED_MIN_Y} window (section-aligned). Gives a max Y of 447
     * (36 sections). Kept modest on purpose: the extended range multiplies per-chunk section objects
     * and client render sections, and an over-large window can exhaust RAM/VRAM. Raise cautiously.
     */
    public static final int EXTENDED_HEIGHT = 576;

    /** Per-level on-disk cubic storage (world save dir), created lazily when population is active. */
    private static final Map<ServerLevel, ICubicStorage> CUBIC_STORAGES = new HashMap<>();

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

        populationEnabled = Boolean.getBoolean("cubicchunks.population");
        if (populationEnabled) {
            LOGGER.warn("Cubic population ENABLED: chunks will run on cubes (memory-only, edits may not "
                    + "persist). Do not use on a world you care about.");
        }

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

    /**
     * Smoke-checks vanilla block-access bridging: a real {@link LevelChunk} from the overworld is put
     * into cubic mode (backed by a fresh {@link Column}), and block get/set through the chunk's own
     * vanilla API is verified to route into the column's cubes (not vanilla sections). Cubic mode is
     * disabled again in a {@code finally} so the live chunk is left exactly as vanilla had it (the
     * cube writes cancel vanilla's path, so no vanilla section is ever mutated).
     */
    public static void verifyBlockBridging(MinecraftServer server) {
        ServerLevel overworld = server.overworld();
        LevelChunk chunk = overworld.getChunk(0, 0);
        if (!(chunk instanceof ICubicLevelChunk cubicChunk)) {
            LOGGER.warn("Block bridging NOT applied: LevelChunk is not ICubicLevelChunk "
                    + "(LevelChunkMixin did not inject)");
            return;
        }

        BlockState stone = Blocks.STONE.defaultBlockState();
        BlockPos testPos = new BlockPos(1, 40, 1);
        Column column = new Column(chunk.getPos().x(), chunk.getPos().z());
        boolean routedOk = false;
        try {
            cubicChunk.cubicchunks$setCubic(column);

            BlockState before = chunk.getBlockState(testPos);        // routes to (empty) cube -> air
            chunk.setBlockState(testPos, stone, 0);                  // routes into the cube
            BlockState after = chunk.getBlockState(testPos);         // reads it back from the cube

            ICube cube = column.getLoadedCube(Coords.blockToCube(testPos.getY()));
            boolean cubeHoldsIt = cube != null && cube.getBlockState(testPos) == stone;

            routedOk = before.isAir() && after == stone && cubeHoldsIt
                    && cubicChunk.cubicchunks$isCubic();
        } finally {
            cubicChunk.cubicchunks$clearCubic();
        }
        // With cubic disabled the chunk is back on the vanilla path, and we never mutated a section.
        boolean revertedOk = !cubicChunk.cubicchunks$isCubic();

        LOGGER.info("Block bridging check: routedOk={}, revertedOk={}", routedOk, revertedOk);
    }

    /**
     * Smoke-checks the extended-height coordinate model: our {@link CubicLevelHeightAccessor} matches
     * the live overworld's section math for the vanilla range (proving it agrees with vanilla), and
     * reports correct section counts and index math for a large but {@code int}-safe extended window.
     * This is the coordinate foundation for height extension; it is not yet applied to live levels.
     */
    public static void verifyHeightExtension(MinecraftServer server) {
        ServerLevel overworld = server.overworld();

        CubicLevelHeightAccessor mirror = new CubicLevelHeightAccessor(overworld.getMinY(), overworld.getHeight());
        boolean matchesVanilla = mirror.getMinY() == overworld.getMinY()
                && mirror.getMaxY() == overworld.getMaxY()
                && mirror.getSectionsCount() == overworld.getSectionsCount()
                && mirror.getMinSectionY() == overworld.getMinSectionY()
                && mirror.getSectionIndex(0) == overworld.getSectionIndex(0)
                && mirror.getSectionIndex(overworld.getMaxY()) == overworld.getSectionIndex(overworld.getMaxY());

        CubicLevelHeightAccessor ext = new CubicLevelHeightAccessor(-2048, 4096);
        boolean extendedOk = ext.getMinY() == -2048
                && ext.getMaxY() == 2047
                && ext.getSectionsCount() == 256
                && ext.getMinSectionY() == -128
                && ext.getSectionIndex(-2048) == 0
                && ext.getSectionIndex(2047) == 255
                && ext.isOutsideBuildHeight(-2049)
                && ext.isOutsideBuildHeight(2048)
                && !ext.isOutsideBuildHeight(0);

        CubicLevelHeightAccessor fromSeam = CubicLevelHeightAccessor.fromCubicWorld((IMinMaxHeight) overworld);
        boolean fromSeamOk = fromSeam.getMinY() == overworld.getMinY()
                && fromSeam.getHeight() == overworld.getHeight();

        LOGGER.info("Height extension check: matchesVanilla={}, extendedSections={}, extendedOk={}, fromSeamOk={}",
                matchesVanilla, ext.getSectionsCount(), extendedOk, fromSeamOk);
    }

    /**
     * Smoke-checks the cube/section bridge and the sparse section store that will replace a cubic
     * chunk's fixed section array: a cube's lazily-created {@link LevelChunkSection} shares the cube's
     * block storage (writes flow both ways and keep the section's counts valid), and a
     * {@link CubeSectionStorage} over a column serves sections at arbitrary, far-apart section-Ys while
     * only materializing the ones that exist.
     */
    public static void verifySectionStorage(MinecraftServer server) {
        PalettedContainerFactory factory = PalettedContainerFactory.create(server.registryAccess());
        BlockState stone = Blocks.STONE.defaultBlockState();

        Cube cube = new Cube(new CubePos(0, 0, 0));
        cube.setBlockState(1, 2, 3, stone);                 // written before the section exists
        LevelChunkSection sec = cube.getOrCreateSection(factory);
        boolean sharedRead = sec.getBlockState(1, 2, 3) == stone && !cube.isEmpty() && !sec.hasOnlyAir();

        sec.setBlockState(4, 5, 6, stone);                  // write via section -> visible on cube
        boolean sectionToCube = cube.getBlockState(4, 5, 6) == stone;

        cube.setBlockState(7, 8, 9, stone);                 // write via cube -> now routes through section
        boolean cubeToSection = sec.getBlockState(7, 8, 9) == stone;

        Column column = new Column(0, 0);
        CubeSectionStorage store = new CubeSectionStorage(column, factory);
        boolean beforeCreate = !store.hasSection(-100) && store.getLoadedSection(200) == null;

        LevelChunkSection low = store.getOrCreateSection(-100);
        store.getOrCreateSection(200);
        low.setBlockState(0, 0, 0, stone);
        boolean sparseOk = store.hasSection(-100) && store.hasSection(200) && !store.hasSection(0)
                && store.getLoadedSection(-100) == low
                && column.getLoadedCube(-100).getBlockState(new BlockPos(0, -100 * ICube.SIZE, 0)) == stone;

        LOGGER.info("Section storage check: sharedRead={}, sectionToCube={}, cubeToSection={}, "
                        + "beforeCreate={}, sparseOk={}",
                sharedRead, sectionToCube, cubeToSection, beforeCreate, sparseOk);
    }

    /**
     * Smoke-checks the live section-accessor redirect: a real overworld chunk is put into cubic mode,
     * and its vanilla {@code getSection(index)} / {@code getSections()} are verified to return the
     * cube-backed sections from {@code CubeSectionStorage} (not the fixed array). Cubic mode is
     * disabled again in a {@code finally}; the real section array is never touched (the redirects
     * cancel the vanilla path).
     */
    public static void verifyLiveSectionAccess(MinecraftServer server) {
        ServerLevel overworld = server.overworld();
        LevelChunk chunk = overworld.getChunk(0, 0);
        if (!(chunk instanceof ICubicLevelChunk cubicChunk)) {
            LOGGER.warn("Live section access NOT applied: LevelChunk is not ICubicLevelChunk");
            return;
        }

        BlockState stone = Blocks.STONE.defaultBlockState();
        int index = 4;
        int sectionY = overworld.getMinSectionY() + index;
        Column column = new Column(chunk.getPos().x(), chunk.getPos().z());
        boolean sectionRoutedOk = false;
        boolean sectionsArrayOk = false;
        try {
            cubicChunk.cubicchunks$setCubic(column);

            LevelChunkSection viaChunk = chunk.getSection(index);   // redirected to the cube's section
            viaChunk.setBlockState(1, 2, 3, stone);

            ICube cube = column.getLoadedCube(sectionY);
            boolean backedByCube = cube instanceof Cube c && c.getSection() == viaChunk;
            boolean stable = chunk.getSection(index) == viaChunk;   // same section on re-fetch
            sectionRoutedOk = backedByCube && stable && viaChunk.getBlockState(1, 2, 3) == stone;

            LevelChunkSection[] sections = chunk.getSections();
            sectionsArrayOk = sections.length == overworld.getSectionsCount()
                    && sections[index] == viaChunk;
        } finally {
            cubicChunk.cubicchunks$clearCubic();
        }

        LOGGER.info("Live section access check: sectionRoutedOk={}, sectionsArrayOk={}",
                sectionRoutedOk, sectionsArrayOk);
    }

    /**
     * Mirrors a loaded chunk's non-empty vanilla sections into a new cube-backed {@link Column}. Must
     * be called while the chunk is NOT in cubic mode, so {@code getSections()} returns the real
     * sections. Each section's block container is copied (cheap, via {@code PalettedContainer.copy()})
     * into a cube at the matching {@code sectionY}.
     *
     * @param level the server level (for the section-index range)
     * @param chunk the chunk to mirror
     * @return a column holding copies of the chunk's non-empty sections as cubes
     */
    public static Column mirrorChunkIntoColumn(ServerLevel level, LevelChunk chunk) {
        int columnX = chunk.getPos().x();
        int columnZ = chunk.getPos().z();
        Column column = new Column(columnX, columnZ);

        LevelChunkSection[] sections = chunk.getSections();
        int minSectionY = level.getMinSectionY();
        for (int i = 0; i < sections.length; i++) {
            LevelChunkSection s = sections[i];
            if (s == null || s.hasOnlyAir()) {
                continue;
            }
            Cube cube = new Cube(new CubePos(columnX, minSectionY + i, columnZ));
            cube.setBlockStates(s.getStates().copy());
            column.addCube(cube);
        }
        return column;
    }

    /**
     * Chunk-load hook: when population is enabled, put the chunk into cubic mode. The column is loaded
     * from cubic storage if it was saved before (so edits persist); otherwise it is mirrored from the
     * chunk's vanilla sections (first visit). Registered against Fabric's
     * {@code ServerChunkEvents.CHUNK_LOAD}, whose callback is {@code (ServerLevel, LevelChunk, boolean
     * newChunk)}; the new-chunk flag is unused (chunks are handled regardless of how they arrived).
     *
     * @param level the server level
     * @param chunk the loaded chunk
     * @param newChunk whether the chunk was newly generated (unused)
     */
    public static void onChunkLoadPopulate(ServerLevel level, LevelChunk chunk, boolean newChunk) {
        if (!populationEnabled) {
            return;
        }
        if (!(chunk instanceof ICubicLevelChunk cubicChunk) || cubicChunk.cubicchunks$isCubic()) {
            return;
        }

        Column column = null;
        ICubicStorage storage = storageFor(level);
        if (storage != null) {
            try {
                column = loadColumn(storage, chunk.getPos().x(), chunk.getPos().z());
            } catch (IOException e) {
                LOGGER.error("Failed to load cubic column [{}, {}]", chunk.getPos().x(), chunk.getPos().z(), e);
            }
        }
        if (column == null) {
            column = mirrorChunkIntoColumn(level, chunk); // first visit: seed from vanilla terrain
            cubicChunk.cubicchunks$setCubic(column);
            return;
        }

        // Loaded an edited column from storage: relight the positions that differ from the vanilla
        // terrain the chunk loaded with, so light matches the restored blocks (must read vanilla
        // sections before switching to cubic mode).
        relightLoadedEdits(level, chunk, column);
        cubicChunk.cubicchunks$setCubic(column);
    }

    /**
     * Schedules light updates for every block in {@code column} that differs from the chunk's currently
     * loaded (vanilla) sections. Call before switching the chunk to cubic mode, so {@code getSection}
     * still returns the real sections. Bounded to the cubes actually stored, so it costs work only for
     * chunks that were edited.
     */
    private static void relightLoadedEdits(ServerLevel level, LevelChunk chunk, Column column) {
        ThreadedLevelLightEngine lightEngine = level.getChunkSource().getLightEngine();
        int minSectionY = level.getMinSectionY();
        int sectionsCount = level.getSectionsCount();
        int baseX = chunk.getPos().getMinBlockX();
        int baseZ = chunk.getPos().getMinBlockZ();

        for (ICube ic : column.getLoadedCubes()) {
            Cube cube = (Cube) ic;
            int sectionY = cube.getY();
            int index = sectionY - minSectionY;
            if (index < 0 || index >= sectionsCount) {
                continue; // outside the vanilla window (no vanilla section to compare against)
            }
            LevelChunkSection vanilla = chunk.getSection(index); // cubic still off -> real section
            for (int y = 0; y < ICube.SIZE; y++) {
                for (int z = 0; z < ICube.SIZE; z++) {
                    for (int x = 0; x < ICube.SIZE; x++) {
                        if (cube.getBlockState(x, y, z) != vanilla.getBlockState(x, y, z)) {
                            lightEngine.checkBlock(new BlockPos(baseX + x, sectionY * ICube.SIZE + y, baseZ + z));
                        }
                    }
                }
            }
        }
    }

    /**
     * Chunk-unload hook: when population is enabled, persist the chunk's cubic column so its edits
     * survive. Registered against Fabric's {@code ServerChunkEvents.CHUNK_UNLOAD}.
     *
     * @param level the server level
     * @param chunk the unloading chunk
     */
    public static void onChunkUnloadPersist(ServerLevel level, LevelChunk chunk) {
        if (!populationEnabled) {
            return;
        }
        if (!(chunk instanceof ICubicLevelChunk cubicChunk) || !cubicChunk.cubicchunks$isCubic()) {
            return;
        }
        if (!(cubicChunk.cubicchunks$getColumn() instanceof Column column)) {
            return;
        }
        ICubicStorage storage = storageFor(level);
        if (storage == null) {
            return;
        }
        try {
            saveColumn(storage, column);
        } catch (IOException e) {
            LOGGER.error("Failed to save cubic column [{}, {}]", column.getX(), column.getZ(), e);
        }
    }

    /** Closes and forgets all open cubic storages. Called on server stop. */
    public static void closeStorages() {
        synchronized (CUBIC_STORAGES) {
            for (ICubicStorage storage : CUBIC_STORAGES.values()) {
                try {
                    storage.close();
                } catch (IOException e) {
                    LOGGER.error("Failed to close cubic storage", e);
                }
            }
            CUBIC_STORAGES.clear();
        }
    }

    /**
     * The cubic storage for {@code level}, under {@code <world>/cubicchunks/<dimension>/}, created on
     * first use. Returns {@code null} if the store could not be opened (logged).
     */
    @javax.annotation.Nullable
    private static ICubicStorage storageFor(ServerLevel level) {
        synchronized (CUBIC_STORAGES) {
            ICubicStorage existing = CUBIC_STORAGES.get(level);
            if (existing != null) {
                return existing;
            }
            try {
                String dim = level.dimension().identifier().toString().replace(':', '_').replace('/', '_');
                Path dir = level.getServer().getWorldPath(LevelResource.ROOT).resolve("cubicchunks").resolve(dim);
                ICubicStorage storage = new NbtFileCubicStorage(dir);
                CUBIC_STORAGES.put(level, storage);
                return storage;
            } catch (IOException e) {
                LOGGER.error("Failed to open cubic storage for {}", level.dimension().identifier(), e);
                return null;
            }
        }
    }

    /**
     * Saves a column (with its cubes bundled) as a single column entry in {@code storage}.
     *
     * @param storage the cubic storage
     * @param column the column to save
     * @throws IOException on IO error
     */
    public static void saveColumn(ICubicStorage storage, Column column) throws IOException {
        storage.writeColumn(new ChunkPos(column.getX(), column.getZ()), column.writeToNbt(new CompoundTag(), true));
    }

    /**
     * Loads a bundled column (with its cubes) from {@code storage}, or {@code null} if none is stored
     * at that position.
     *
     * @param storage the cubic storage
     * @param x the column x
     * @param z the column z
     * @return the loaded column, or {@code null}
     * @throws IOException on IO error
     */
    @javax.annotation.Nullable
    public static Column loadColumn(ICubicStorage storage, int x, int z) throws IOException {
        CompoundTag tag = storage.readColumn(new ChunkPos(x, z));
        if (tag == null) {
            return null;
        }
        Column column = new Column(x, z);
        column.readFromNbt(tag);
        return column;
    }

    /**
     * Smoke-checks population: a real overworld chunk's blocks are captured, the chunk is mirrored into
     * cubes and switched to cubic mode, and every captured position is verified to read back the same
     * block state through the cube-backed path (i.e. the game is now reading the real chunk's blocks
     * out of cubes). Reverted in a {@code finally}.
     */
    public static void verifyPopulation(MinecraftServer server) {
        ServerLevel overworld = server.overworld();
        LevelChunk chunk = overworld.getChunk(0, 0);
        if (!(chunk instanceof ICubicLevelChunk cubicChunk)) {
            LOGGER.warn("Population NOT applied: LevelChunk is not ICubicLevelChunk");
            return;
        }

        int baseX = chunk.getPos().getMinBlockX();
        int baseZ = chunk.getPos().getMinBlockZ();
        int[] locals = {0, 7, 15};
        Map<BlockPos, BlockState> expected = new HashMap<>();
        for (int lx : locals) {
            for (int lz : locals) {
                for (int y = overworld.getMinY(); y <= overworld.getMaxY(); y++) {
                    BlockPos pos = new BlockPos(baseX + lx, y, baseZ + lz);
                    expected.put(pos, chunk.getBlockState(pos)); // cubic off -> vanilla
                }
            }
        }

        boolean allMatch = true;
        int checked = 0;
        try {
            Column column = mirrorChunkIntoColumn(overworld, chunk);
            cubicChunk.cubicchunks$setCubic(column);
            for (Map.Entry<BlockPos, BlockState> e : expected.entrySet()) {
                checked++;
                if (chunk.getBlockState(e.getKey()) != e.getValue()) {
                    allMatch = false;
                }
            }
        } finally {
            cubicChunk.cubicchunks$clearCubic();
        }

        LOGGER.info("Population check: positionsChecked={}, allMatch={}", checked, allMatch);
    }

    /**
     * Smoke-checks extended height: logs the overworld's reported build-height range (extended when
     * population is enabled, otherwise vanilla, confirming it is off by default). When population is on,
     * it also places a block above vanilla's old ceiling and reads it back through the world, then
     * clears it, proving the extended range is buildable end to end.
     */
    public static void verifyExtendedHeight(MinecraftServer server) {
        ServerLevel overworld = server.overworld();
        LOGGER.info("Extended height check: populationEnabled={}, minY={}, maxY={}, sections={}",
                populationEnabled, overworld.getMinY(), overworld.getMaxY(), overworld.getSectionsCount());

        if (populationEnabled) {
            BlockPos high = new BlockPos(0, 400, 0); // above vanilla's max of 320, within the extended window
            boolean insideNow = !overworld.isOutsideBuildHeight(high);
            overworld.setBlockAndUpdate(high, Blocks.STONE.defaultBlockState());
            boolean placed = overworld.getBlockState(high) == Blocks.STONE.defaultBlockState();
            overworld.setBlockAndUpdate(high, Blocks.AIR.defaultBlockState());
            LOGGER.info("Extended height build test: y400InsideBuildHeight={}, placedAndRead={}", insideNow, placed);
        }
    }

    /**
     * Smoke-checks persistence: a column with an edited cube is saved (bundled) to a temporary cubic
     * storage and loaded back into a fresh column, verifying the edit survived the disk round trip, and
     * that a never-saved position loads as {@code null}. This exercises the same save/load path the live
     * chunk unload/load hooks use.
     */
    public static void verifyPersistence() {
        BlockState stone = Blocks.STONE.defaultBlockState();
        Path dir = null;
        try {
            dir = Files.createTempDirectory("cubicchunks-persistence-test");
            try (ICubicStorage storage = new NbtFileCubicStorage(dir)) {
                Column column = new Column(5, 6);
                Cube cube = new Cube(new CubePos(5, 1, 6));
                cube.setBlockState(2, 3, 4, stone);
                column.addCube(cube);

                boolean noneBefore = !storage.columnExists(new ChunkPos(5, 6));
                saveColumn(storage, column);
                boolean existsAfter = storage.columnExists(new ChunkPos(5, 6));

                Column loaded = loadColumn(storage, 5, 6);
                ICube loadedCube = loaded == null ? null : loaded.getLoadedCube(1);
                BlockPos worldPos = new BlockPos(5 * ICube.SIZE + 2, 1 * ICube.SIZE + 3, 6 * ICube.SIZE + 4);
                boolean restored = loadedCube != null && loadedCube.getBlockState(worldPos) == stone;
                boolean missingNull = loadColumn(storage, 42, 42) == null;

                LOGGER.info("Persistence check: noneBefore={}, existsAfter={}, restored={}, missingNull={}",
                        noneBefore, existsAfter, restored, missingNull);
            }
        } catch (IOException e) {
            LOGGER.error("Persistence check FAILED with IO error", e);
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
