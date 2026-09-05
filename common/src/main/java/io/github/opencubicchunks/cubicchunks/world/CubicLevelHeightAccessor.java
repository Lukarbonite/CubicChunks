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
package io.github.opencubicchunks.cubicchunks.world;

import io.github.opencubicchunks.cubicchunks.api.world.IMinMaxHeight;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.LevelHeightAccessor;

/**
 * A {@link LevelHeightAccessor} over an extended, section-aligned build-height window for a cubic
 * world. It supplies only {@link #getMinY()} and {@link #getHeight()}; all section-index math is
 * inherited from the {@code LevelHeightAccessor} defaults, so it is guaranteed to agree with vanilla
 * for any given range.
 *
 * <p><b>Why a bounded window, not the whole world.</b> CubicChunks worlds are effectively unbounded
 * vertically ({@code CubicChunksCommon.MIN/MAX_SUPPORTED_BLOCK_Y} span nearly the full {@code int}
 * range), but a {@code LevelHeightAccessor}'s height is an {@code int}, so it cannot represent that
 * whole span (the block count overflows {@code int}). This accessor therefore describes a large but
 * {@code int}-safe window that vanilla systems which query world height (heightmaps, some lighting and
 * serialization paths) can use. The truly unbounded part of the world is served by cubes and the
 * {@code LevelChunk} block-access bridging, not by reporting an enormous height here.
 *
 * <p><b>Not yet applied to live levels.</b> A real {@code LevelChunk} allocates a fixed
 * {@code LevelChunkSection[]} sized to {@code getSectionsCount()}, so a level cannot simply start
 * reporting an extended height without the section array being made sparse first. This type is the
 * coordinate model that later steps will wire in behind that guard.
 */
public final class CubicLevelHeightAccessor implements LevelHeightAccessor {

    private final int minY;
    private final int height;

    /**
     * @param minY the lowest block Y of the window; must be a multiple of {@link SectionPos} size (16)
     * @param height the window height in blocks; must be a positive multiple of 16
     */
    public CubicLevelHeightAccessor(int minY, int height) {
        if (height <= 0 || (height & 15) != 0) {
            throw new IllegalArgumentException("height must be a positive multiple of 16, got " + height);
        }
        if ((minY & 15) != 0) {
            throw new IllegalArgumentException("minY must be a multiple of 16, got " + minY);
        }
        this.minY = minY;
        this.height = height;
    }

    /**
     * Builds an accessor from a cubic height seam, snapping the range outward to section (16-block)
     * boundaries so it satisfies the section-aligned contract.
     *
     * @param height the cubic min/max height source
     * @return an accessor spanning the snapped range
     */
    public static CubicLevelHeightAccessor fromCubicWorld(IMinMaxHeight height) {
        int min = Math.floorDiv(height.getMinHeight(), 16) * 16;
        int maxExclusive = -Math.floorDiv(-height.getMaxHeight(), 16) * 16; // ceil to section
        return new CubicLevelHeightAccessor(min, maxExclusive - min);
    }

    @Override
    public int getHeight() {
        return height;
    }

    @Override
    public int getMinY() {
        return minY;
    }
}
