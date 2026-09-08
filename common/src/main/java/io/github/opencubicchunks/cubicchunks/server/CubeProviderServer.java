/*
 *  This file is part of Cubic Chunks Mod, licensed under the MIT License (MIT).
 *
 *  Copyright (c) 2015-2021 OpenCubicChunks
 *  Copyright (c) 2015-2021 contributors
 *
 *  Permission is hereby granted, free of charge, to any person obtaining a copy
 *  of this software and associated documentation files (the "Software"), to deal
 *  in the Software without restriction, including without limitation the rights
 *  to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 *  copies of the Software, and to permit persons to whom the Software is
 *  furnished to do so, subject to the following conditions:
 *
 *  The above copyright notice and this permission notice shall be included in
 *  all copies or substantial portions of the Software.
 *
 *  THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 *  IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 *  FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 *  AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 *  LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 *  OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 *  THE SOFTWARE.
 */
package io.github.opencubicchunks.cubicchunks.server;

import io.github.opencubicchunks.cubicchunks.api.util.CubePos;
import io.github.opencubicchunks.cubicchunks.api.world.ICube;
import io.github.opencubicchunks.cubicchunks.api.world.IColumn;
import io.github.opencubicchunks.cubicchunks.api.world.ICubeProviderServer;
import io.github.opencubicchunks.cubicchunks.api.world.storage.ICubicStorage;
import io.github.opencubicchunks.cubicchunks.api.worldgen.CubePrimer;
import io.github.opencubicchunks.cubicchunks.api.worldgen.ICubeGenerator;
import io.github.opencubicchunks.cubicchunks.world.column.Column;
import io.github.opencubicchunks.cubicchunks.world.cube.Cube;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.state.BlockState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import javax.annotation.Nullable;
import javax.annotation.ParametersAreNonnullByDefault;

/**
 * Server-side cube provider: the in-memory index of loaded {@link Column}s (and, through them,
 * {@link ICube}s) for a single world.
 *
 * <p><b>Partial port.</b> The 1.12.2 {@code CubeProviderServer} extended vanilla's
 * {@code ChunkProviderServer}: columns were vanilla {@code Chunk}s, and the provider drove disk IO,
 * the generation/population pipeline, {@code ForgeChunkManager} tickets and async loading. Modern
 * Minecraft owns the {@code LevelChunk} lifecycle in {@code ServerChunkCache}/{@code ChunkMap} with
 * ticket levels, so those responsibilities move to a chunk-pipeline mixin later, and the disk/gen
 * paths wait on the storage and generation subsystems.
 *
 * <p>What is ported here is the loader-neutral core: a {@code (x, z) -> Column} map keyed by
 * {@link ChunkPos#pack(int, int)}, with cube lookup routed through each column's {@code CubeMap}.
 * With no disk or generator yet, every {@link Requirement} above {@code GET_CACHED} is satisfied by
 * creating the column/cube in memory on demand, so {@link #isCubeGenerated} is equivalent to "loaded".
 */
@ParametersAreNonnullByDefault
public class CubeProviderServer implements ICubeProviderServer {

    private static final Logger LOGGER = LoggerFactory.getLogger("CubicChunks/CubeProviderServer");

    private final Map<Long, Column> columns = new HashMap<>();

    /** Backing disk storage, or {@code null} for a pure in-memory provider. */
    @Nullable private final ICubicStorage storage;

    /**
     * Base-terrain generator, or {@code null} for none. When {@code null}, {@link Requirement#GENERATE}
     * yields an empty (all-air) cube exactly as before, which keeps ordinary worlds vanilla until a world
     * opts in. When set, a cache/disk miss at {@code GENERATE} or above fills the new cube from it.
     */
    @Nullable private ICubeGenerator generator;

    /** The neighbour-gated populate driver, created with the generator (rebuilt when it changes). */
    @Nullable private CubePopulator populator;

    /** Creates a pure in-memory provider (no disk persistence). */
    public CubeProviderServer() {
        this(null);
    }

    /**
     * Creates a provider backed by {@code storage}: {@link Requirement#LOAD} reads columns and cubes
     * from disk on a cache miss, and {@link #saveColumn}/{@link #saveAll} persist them.
     *
     * @param storage the disk backend, or {@code null} for in-memory only
     */
    public CubeProviderServer(@Nullable ICubicStorage storage) {
        this.storage = storage;
    }

    /**
     * Sets (or clears, with {@code null}) the base-terrain generator used by {@link Requirement#GENERATE}
     * and above.
     */
    public void setGenerator(@Nullable ICubeGenerator generator) {
        this.generator = generator;
        this.populator = generator == null ? null : new CubePopulator(this, generator);
    }

    /** The neighbour-gated populate driver for this provider, or {@code null} if no generator is set. */
    @Nullable
    public CubePopulator getPopulator() {
        return populator;
    }

    /** The base-terrain generator, or {@code null} if none is set. */
    @Nullable
    public ICubeGenerator getGenerator() {
        return generator;
    }

    // ==========================================================================
    // Columns
    // ==========================================================================

    @Nullable
    @Override
    public IColumn getLoadedColumn(int x, int z) {
        return columns.get(ChunkPos.pack(x, z));
    }

    @Override
    public IColumn provideColumn(int x, int z) {
        return columns.computeIfAbsent(ChunkPos.pack(x, z), key -> new Column(x, z));
    }

    @Nullable
    @Override
    public IColumn getColumn(int columnX, int columnZ, Requirement req) {
        IColumn cached = getLoadedColumn(columnX, columnZ);
        if (cached != null || req == Requirement.GET_CACHED) {
            return cached;
        }
        IColumn loaded = loadColumnFromStorage(columnX, columnZ);
        if (loaded != null) {
            return loaded;
        }
        if (req == Requirement.LOAD) {
            // LOAD must not generate: the column is neither cached nor on disk.
            return null;
        }
        // GENERATE and above: with no generator yet, "generate" is an empty in-memory column.
        return provideColumn(columnX, columnZ);
    }

    /**
     * Loads a column from disk into the cache if the backend has it. Returns {@code null} when there is
     * no storage or no stored column (or on IO error, logged).
     */
    @Nullable
    private Column loadColumnFromStorage(int x, int z) {
        if (storage == null) {
            return null;
        }
        try {
            CompoundTag tag = storage.readColumn(new ChunkPos(x, z));
            if (tag == null) {
                return null;
            }
            Column column = new Column(x, z);
            column.readFromNbt(tag);
            columns.put(ChunkPos.pack(x, z), column);
            return column;
        } catch (IOException e) {
            LOGGER.error("Failed to load column [{}, {}] from storage", x, z, e);
            return null;
        }
    }

    // ==========================================================================
    // Cubes
    // ==========================================================================

    @Nullable
    @Override
    public ICube getLoadedCube(int cubeX, int cubeY, int cubeZ) {
        IColumn column = getLoadedColumn(cubeX, cubeZ);
        return column == null ? null : column.getLoadedCube(cubeY);
    }

    @Nullable
    @Override
    public ICube getLoadedCube(CubePos coords) {
        return getLoadedCube(coords.getX(), coords.getY(), coords.getZ());
    }

    @Override
    public ICube getCube(int cubeX, int cubeY, int cubeZ) {
        // provideColumn is non-null; getCube on the column creates the cube on demand.
        return provideColumn(cubeX, cubeZ).getCube(cubeY);
    }

    @Override
    public ICube getCube(CubePos coords) {
        return getCube(coords.getX(), coords.getY(), coords.getZ());
    }

    @Nullable
    @Override
    public ICube getCube(int cubeX, int cubeY, int cubeZ, Requirement req) {
        if (req == Requirement.GET_CACHED) {
            return getLoadedCube(cubeX, cubeY, cubeZ);
        }

        // Resolve the owning column at the same requirement (loads/creates it as allowed).
        IColumn column = getColumn(cubeX, cubeZ, req);
        if (column == null) {
            return null;
        }

        // Resolve the cube: cached, then from disk, then (for GENERATE and above) freshly generated.
        ICube cube = column.getLoadedCube(cubeY);
        if (cube == null) {
            cube = loadCubeFromStorage(column, cubeX, cubeY, cubeZ);
        }
        if (cube == null) {
            if (req == Requirement.LOAD) {
                // LOAD must not generate: the cube is neither cached nor on disk.
                return null;
            }
            // GENERATE and above: create the cube, then fill it from the generator when one is set. With no
            // generator it stays an empty in-memory cube, preserving the vanilla-off default.
            ICube generated = column.getCube(cubeY);
            if (generator != null && generated instanceof Cube fresh && fresh.isEmpty()) {
                generateInto(fresh);
            }
            cube = generated;
        }
        // POPULATE (and above, since LIGHT implies populate): decorate through the neighbour-gated
        // populator, which first generates the required neighbourhood so cross-cube features are seamless,
        // then decorates this cube once. This applies whether the cube was cached, loaded, or just
        // generated (an already-generated cube still needs populating on a POPULATE request), and is
        // skipped once the cube is populated. LIGHT-stage lighting itself is not ported.
        if (populator != null && req.ordinal() >= Requirement.POPULATE.ordinal()
                && cube instanceof Cube target && !target.isPopulated()) {
            populator.ensurePopulated(new CubePos(cubeX, cubeY, cubeZ));
        }
        return cube;
    }

    /**
     * Fills a freshly created, empty cube from the generator: runs the generator into a {@link CubePrimer}
     * and copies its non-air block states into the cube. Air positions are left untouched (the cube is
     * already all air). Called only when {@link #generator} is set.
     */
    private void generateInto(Cube cube) {
        CubePrimer primer = new CubePrimer();
        generator.generate(cube.getCoords(), primer);
        for (int y = 0; y < ICube.SIZE; y++) {
            for (int z = 0; z < ICube.SIZE; z++) {
                for (int x = 0; x < ICube.SIZE; x++) {
                    BlockState state = primer.getBlockState(x, y, z);
                    if (!state.isAir()) {
                        cube.setBlockState(x, y, z, state);
                    }
                }
            }
        }
        // Biomes, when the generator places them: adopted before any section is built so it wraps them.
        var biomes = generator.generateBiomes(cube.getCoords());
        if (biomes != null) {
            cube.setBiomes(biomes);
        }
    }

    /**
     * Loads a cube from disk into its column if the backend has it. Returns {@code null} when there is
     * no storage or no stored cube (or on IO error, logged).
     */
    @Nullable
    private ICube loadCubeFromStorage(IColumn column, int x, int y, int z) {
        if (storage == null) {
            return null;
        }
        try {
            CompoundTag tag = storage.readCube(new CubePos(x, y, z));
            if (tag == null) {
                return null;
            }
            Cube cube = new Cube(new CubePos(x, y, z));
            cube.readFromNbt(tag);
            column.addCube(cube);
            return cube;
        } catch (IOException e) {
            LOGGER.error("Failed to load cube [{}, {}, {}] from storage", x, y, z, e);
            return null;
        }
    }

    @Nullable
    @Override
    public ICube getCubeNow(int cubeX, int cubeY, int cubeZ, Requirement req) {
        // No async loading in the in-memory skeleton: "now" is the same as the synchronous path.
        return getCube(cubeX, cubeY, cubeZ, req);
    }

    @Override
    public boolean isCubeGenerated(int cubeX, int cubeY, int cubeZ) {
        if (getLoadedCube(cubeX, cubeY, cubeZ) != null) {
            return true;
        }
        // Otherwise it counts as generated if it is present on disk.
        if (storage == null) {
            return false;
        }
        try {
            return storage.cubeExists(new CubePos(cubeX, cubeY, cubeZ));
        } catch (IOException e) {
            LOGGER.error("Failed to check cube [{}, {}, {}] on disk", cubeX, cubeY, cubeZ, e);
            return false;
        }
    }

    // ==========================================================================
    // Skeleton helpers (not part of the API; used by lifecycle wiring and tests)
    // ==========================================================================

    /** Drops a loaded column (and its cubes) from the in-memory index. */
    @Nullable
    public IColumn unloadColumn(int x, int z) {
        return columns.remove(ChunkPos.pack(x, z));
    }

    /** An unmodifiable view of all currently loaded columns. */
    public Collection<Column> getLoadedColumns() {
        return Collections.unmodifiableCollection(columns.values());
    }

    /** The number of columns currently held in memory. */
    public int getLoadedColumnCount() {
        return columns.size();
    }

    // ==========================================================================
    // Persistence
    // ==========================================================================

    /**
     * Persists a column and all of its loaded cubes to disk. The column is written column-level only
     * (coordinates + opacity index); each cube is written to its own {@link CubePos} entry. A no-op if
     * this provider has no storage.
     *
     * @param column the column to save
     */
    public void saveColumn(IColumn column) {
        if (storage == null) {
            return;
        }
        Column col = (Column) column;
        try {
            storage.writeColumn(new ChunkPos(col.getX(), col.getZ()), col.writeToNbt(new CompoundTag(), false));
            for (ICube cube : col.getLoadedCubes()) {
                storage.writeCube(cube.getCoords(), ((Cube) cube).writeToNbt(new CompoundTag()));
            }
        } catch (IOException e) {
            LOGGER.error("Failed to save column [{}, {}] to storage", col.getX(), col.getZ(), e);
        }
    }

    /**
     * Persists every loaded column and cube, then flushes the backend. A no-op if this provider has no
     * storage.
     */
    public void saveAll() {
        if (storage == null) {
            return;
        }
        for (Column column : columns.values()) {
            saveColumn(column);
        }
        try {
            storage.flush();
        } catch (IOException e) {
            LOGGER.error("Failed to flush storage", e);
        }
    }
}
