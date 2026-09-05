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
}
