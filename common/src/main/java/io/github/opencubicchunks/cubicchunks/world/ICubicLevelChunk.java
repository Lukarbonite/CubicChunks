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

import io.github.opencubicchunks.cubicchunks.api.world.IColumn;
import io.github.opencubicchunks.cubicchunks.world.column.CubeSectionStorage;

import javax.annotation.Nullable;

/**
 * Implemented on {@code LevelChunk} by {@code LevelChunkMixin}. When cubic mode is enabled, the
 * chunk's block get/set is routed to the backing {@link IColumn}'s cubes instead of vanilla's
 * fixed-height sections.
 *
 * <p>Cubic mode is opt-in and off by default, so an ordinary (non-cubic) world's chunks behave
 * exactly like vanilla. It becomes always-on for cubic worlds once the world-type / chunk-pipeline
 * work lands and every cubic chunk is given its column at load time.
 */
public interface ICubicLevelChunk {

    /**
     * Enables cubic block routing on this chunk, backed by {@code column}.
     *
     * @param column the column whose cubes hold this chunk's blocks
     */
    void cubicchunks$setCubic(IColumn column);

    /** Disables cubic routing, returning the chunk to vanilla block access. */
    void cubicchunks$clearCubic();

    /** Whether cubic block routing is currently enabled on this chunk. */
    boolean cubicchunks$isCubic();

    /** The backing column, or {@code null} if cubic routing is not enabled. */
    @Nullable IColumn cubicchunks$getColumn();

    /**
     * The sparse {@code sectionY -> LevelChunkSection} view over the backing column's cubes, built
     * lazily on first use. Only valid while cubic mode is enabled.
     *
     * @return the section storage
     */
    CubeSectionStorage cubicchunks$getSectionStorage();
}
