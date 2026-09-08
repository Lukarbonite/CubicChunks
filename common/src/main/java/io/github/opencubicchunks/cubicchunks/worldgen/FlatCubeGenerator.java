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
package io.github.opencubicchunks.cubicchunks.worldgen;

import io.github.opencubicchunks.cubicchunks.api.util.CubePos;
import io.github.opencubicchunks.cubicchunks.api.worldgen.CubePrimer;
import io.github.opencubicchunks.cubicchunks.api.worldgen.ICubeGenerator;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The first concrete {@link ICubeGenerator}: a flat base-terrain profile that is solid below a fixed
 * surface and air above it, filling the whole extended vertical range with stone so the cubic depth is
 * immediately visible. It is deliberately trivial; a vanilla-noise-backed generator (sampling
 * {@code NoiseBasedChunkGenerator} into cube slices) replaces it in a later slice.
 *
 * <p>The profile, from the top down: air at and above {@link #SURFACE_Y}, one grass block at
 * {@code SURFACE_Y - 1}, three dirt blocks below that, and stone all the way down through the extended
 * negative range. It ignores the seed on purpose (it captures it only so the wiring and determinism
 * story are already in place for the noise generator), which makes it a pure function of {@link CubePos}
 * and therefore safe under C2ME's off-main-thread worldgen: see the purity contract on
 * {@link ICubeGenerator}.
 */
public final class FlatCubeGenerator implements ICubeGenerator {

    /** First air block: the top solid block of the world is at {@code SURFACE_Y - 1}. */
    private static final int SURFACE_Y = 64;

    private static final BlockState STONE = Blocks.STONE.defaultBlockState();
    private static final BlockState DIRT = Blocks.DIRT.defaultBlockState();
    private static final BlockState GRASS = Blocks.GRASS_BLOCK.defaultBlockState();

    /** Captured for determinism and for the future noise generator; the flat profile does not use it. */
    private final long seed;

    public FlatCubeGenerator(long seed) {
        this.seed = seed;
    }

    /** The world seed this generator was built for. */
    public long seed() {
        return seed;
    }

    @Override
    public void generate(CubePos pos, CubePrimer primer) {
        int baseY = pos.getY() * 16; // cube Y coordinate to the world Y of its bottom layer
        for (int localY = 0; localY < 16; localY++) {
            BlockState state = profileAt(baseY + localY);
            if (state == null) {
                continue; // air: leave the primer at its default
            }
            for (int localX = 0; localX < 16; localX++) {
                for (int localZ = 0; localZ < 16; localZ++) {
                    primer.setBlockState(localX, localY, localZ, state);
                }
            }
        }
    }

    /** The block state for a given world Y in the flat profile, or {@code null} for air. */
    private static BlockState profileAt(int worldY) {
        if (worldY >= SURFACE_Y) {
            return null;
        }
        if (worldY == SURFACE_Y - 1) {
            return GRASS;
        }
        if (worldY >= SURFACE_Y - 4) {
            return DIRT; // the three blocks below the surface
        }
        return STONE;
    }
}
