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

import io.github.opencubicchunks.cubicchunks.api.world.IColumn;
import io.github.opencubicchunks.cubicchunks.world.cube.Cube;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.PalettedContainerFactory;

import javax.annotation.Nullable;

/**
 * A sparse, {@code sectionY}-keyed view of a {@link IColumn}'s cubes as {@link LevelChunkSection}s.
 *
 * <p>This is the structure that replaces a vanilla {@code LevelChunk}'s fixed
 * {@code LevelChunkSection[]} for cubic worlds. A cube at {@code cubeY} is exactly the section at
 * {@code sectionY == cubeY} (both are 16-block units), and each cube lazily owns its section
 * ({@link Cube#getOrCreateSection}).
 *
 * <p><b>Why sparse-by-key, not a list or an array.</b> A cubic column is unbounded and sparse in Y:
 * cubes exist only where the world has content, at arbitrary (including large-negative) section-Ys. A
 * contiguous {@code list}/{@code array} would have to be offset-indexed and pre-sized to the whole
 * window, wasting memory across gaps and capping the range at an {@code int} height. Keying by
 * {@code sectionY} (backed by {@link CubeMap}, already sorted-sparse by Y) stores only what exists and
 * imposes no vertical bound.
 */
public final class CubeSectionStorage {

    private final IColumn column;
    private final PalettedContainerFactory factory;

    public CubeSectionStorage(IColumn column, PalettedContainerFactory factory) {
        this.column = column;
        this.factory = factory;
    }

    /**
     * Returns the section at {@code sectionY}, creating the cube and its section on demand.
     *
     * @param sectionY the section (== cube) Y coordinate
     * @return the section, never {@code null}
     */
    public LevelChunkSection getOrCreateSection(int sectionY) {
        return ((Cube) column.getCube(sectionY)).getOrCreateSection(factory);
    }

    /**
     * Returns the section at {@code sectionY} only if its cube is loaded and its section already exists.
     *
     * @param sectionY the section (== cube) Y coordinate
     * @return the section, or {@code null} if not present
     */
    @Nullable
    public LevelChunkSection getLoadedSection(int sectionY) {
        var cube = column.getLoadedCube(sectionY);
        return cube == null ? null : ((Cube) cube).getSection();
    }

    /**
     * Whether a cube exists at {@code sectionY} (regardless of whether its section has been created).
     *
     * @param sectionY the section (== cube) Y coordinate
     * @return whether the cube is loaded
     */
    public boolean hasSection(int sectionY) {
        return column.getLoadedCube(sectionY) != null;
    }
}
