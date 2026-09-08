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
package io.github.opencubicchunks.cubicchunks.world.column;

import io.github.opencubicchunks.cubicchunks.api.util.Coords;
import io.github.opencubicchunks.cubicchunks.api.util.CubePos;
import io.github.opencubicchunks.cubicchunks.api.world.IColumn;
import io.github.opencubicchunks.cubicchunks.api.world.ICube;
import io.github.opencubicchunks.cubicchunks.api.world.IHeightMap;
import io.github.opencubicchunks.cubicchunks.world.ServerHeightMap;
import io.github.opencubicchunks.cubicchunks.world.cube.Cube;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.world.level.chunk.PalettedContainerFactory;

import javax.annotation.Nullable;
import java.util.Collection;

/**
 * A column: the (x, z) stack of {@link Cube}s, backed by a {@link CubeMap} and an
 * {@link IHeightMap} (opacity index).
 *
 * <p><b>First core slice.</b> This is a standalone column that owns its cubes in the {@link CubeMap}
 * directly. On 1.12.2 the column was a vanilla {@code Chunk} whose {@code getLoadedCube}/{@code getCube}
 * routed through the world's cube provider; that provider is not ported yet, so here those consult the
 * local {@code CubeMap} instead (and {@link #getCube} creates an empty cube on demand). {@link #shouldTick()}
 * approximates the not-yet-ported ticket system. When the world/provider land, this bridges to a
 * vanilla {@code LevelChunk}.
 */
public class Column implements IColumn {

    private final int x;
    private final int z;
    private final CubeMap cubeMap = new CubeMap();
    private final IHeightMap opacityIndex;

    public Column(int x, int z) {
        this.x = x;
        this.z = z;
        this.opacityIndex = new ServerHeightMap(new int[ICube.SIZE * ICube.SIZE]);
    }

    @Override
    public int getX() {
        return x;
    }

    @Override
    public int getZ() {
        return z;
    }

    @Nullable
    @Override
    public ICube getLoadedCube(int cubeY) {
        return cubeMap.get(cubeY);
    }

    @Override
    public ICube getCube(int cubeY) {
        Cube cube = cubeMap.get(cubeY);
        if (cube == null) {
            // No provider/generator yet: create an empty cube on demand so the non-null contract holds.
            cube = new Cube(new CubePos(x, cubeY, z));
            cubeMap.put(cube);
        }
        return cube;
    }

    @Override
    public void addCube(ICube cube) {
        cubeMap.put((Cube) cube);
    }

    @Nullable
    @Override
    public ICube removeCube(int cubeY) {
        return cubeMap.remove(cubeY);
    }

    @Override
    public boolean hasLoadedCubes() {
        return !cubeMap.isEmpty();
    }

    @Override
    public Collection<? extends ICube> getLoadedCubes() {
        return cubeMap.all();
    }

    @Override
    public Iterable<? extends ICube> getLoadedCubes(int startY, int endY) {
        return cubeMap.cubes(startY, endY);
    }

    @Override
    public void preCacheCube(ICube cube) {
        // Single-cube lookup cache; a no-op until the provider path exists.
    }

    @Override
    public IHeightMap getOpacityIndex() {
        return opacityIndex;
    }

    @Override
    public int getHeight(BlockPos pos) {
        return getHeightValue(Coords.blockToLocal(pos.getX()), pos.getY(), Coords.blockToLocal(pos.getZ()));
    }

    @Deprecated
    @Override
    public int getHeightValue(int localX, int localZ) {
        return opacityIndex.getTopBlockY(localX, localZ) + 1;
    }

    @Override
    public int getHeightValue(int localX, int blockY, int localZ) {
        return opacityIndex.getTopBlockY(localX, localZ) + 1;
    }

    @Override
    public boolean shouldTick() {
        // Ticket system not ported yet: tick while any cubes are loaded.
        return !cubeMap.isEmpty();
    }

    // ==========================================================================
    // Serialization
    // ==========================================================================

    private static final String NBT_X = "x";
    private static final String NBT_Z = "z";
    private static final String NBT_OPACITY_INDEX = "opacity_index";
    private static final String NBT_CUBES = "cubes";
    private static final String NBT_CUBE_Y = "y";

    /**
     * Writes this column's coordinates, opacity index (height map) and every loaded cube into
     * {@code tag}. Each cube is stored in the {@value #NBT_CUBES} list as its Y plus the block-state
     * data from {@link Cube#writeToNbt}.
     *
     * <p><b>Note.</b> This bundles the cubes into the column tag, which is a convenient self-contained
     * unit for now. The 1.12.2 region format stored columns and cubes in separate entries; the disk
     * layer, when ported, may split them again (cubes keyed by {@link CubePos}). Biomes and entities
     * are not written yet (deferred with the storage/world subsystem).
     *
     * @param tag the compound to write into
     * @return {@code tag}, for chaining
     */
    public CompoundTag writeToNbt(CompoundTag tag) {
        return writeToNbt(tag, true, null);
    }

    /**
     * Writes this column into {@code tag}, optionally bundling its cubes.
     *
     * <p>With {@code includeCubes = true} the result is a self-contained column-plus-cubes unit. With
     * {@code includeCubes = false} only column-level data (coordinates and opacity index) is written;
     * this is what the disk layer uses, since {@code ICubicStorage} stores cubes in their own entries
     * keyed by {@link CubePos}. {@link #readFromNbt} reads either form (a missing cube list restores no
     * cubes).
     *
     * @param tag the compound to write into
     * @param includeCubes whether to bundle the column's cubes into {@code tag}
     * @return {@code tag}, for chaining
     */
    public CompoundTag writeToNbt(CompoundTag tag, boolean includeCubes) {
        return writeToNbt(tag, includeCubes, null);
    }

    /**
     * As {@link #writeToNbt(CompoundTag, boolean)}, but a non-null {@code factory} is passed to each
     * bundled cube so its biomes are serialized too (biomes need the world's biome registry, which the
     * factory carries). With {@code factory == null} the cubes write block states only.
     *
     * @param tag the compound to write into
     * @param includeCubes whether to bundle the column's cubes into {@code tag}
     * @param factory the factory for biome serialization, or {@code null} for block states only
     * @return {@code tag}, for chaining
     */
    public CompoundTag writeToNbt(CompoundTag tag, boolean includeCubes, @Nullable PalettedContainerFactory factory) {
        tag.putInt(NBT_X, x);
        tag.putInt(NBT_Z, z);
        tag.putByteArray(NBT_OPACITY_INDEX, ((ServerHeightMap) opacityIndex).getData());

        if (includeCubes) {
            ListTag cubesTag = new ListTag();
            for (Cube cube : cubeMap) {
                CompoundTag cubeTag = new CompoundTag();
                cubeTag.putInt(NBT_CUBE_Y, cube.getY());
                cube.writeToNbt(cubeTag, factory);
                cubesTag.add(cubesTag.size(), cubeTag);
            }
            tag.put(NBT_CUBES, cubesTag);
        }
        return tag;
    }

    /**
     * Restores this column's opacity index and cubes from {@code tag} (written by
     * {@link #writeToNbt}), replacing any currently loaded cubes. The column's own x/z are not changed;
     * they are fixed at construction and the stored coordinates are for the disk layer's use.
     *
     * @param tag the compound to read from
     */
    public void readFromNbt(CompoundTag tag) {
        readFromNbt(tag, null);
    }

    /**
     * As {@link #readFromNbt(CompoundTag)}, but a non-null {@code factory} is passed to each cube so its
     * stored biomes are restored too (must be the same kind of factory used to write them).
     *
     * @param tag the compound to read from
     * @param factory the factory for biome deserialization, or {@code null} for block states only
     */
    public void readFromNbt(CompoundTag tag, @Nullable PalettedContainerFactory factory) {
        tag.getByteArray(NBT_OPACITY_INDEX)
                .ifPresent(data -> ((ServerHeightMap) opacityIndex).readData(data));

        cubeMap.clear();
        ListTag cubesTag = tag.getListOrEmpty(NBT_CUBES);
        for (int i = 0; i < cubesTag.size(); i++) {
            CompoundTag cubeTag = cubesTag.getCompoundOrEmpty(i);
            int cubeY = cubeTag.getIntOr(NBT_CUBE_Y, 0);
            Cube cube = new Cube(new CubePos(x, cubeY, z));
            cube.readFromNbt(cubeTag, factory);
            cubeMap.put(cube);
        }
    }
}
