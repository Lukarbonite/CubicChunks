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
package io.github.opencubicchunks.cubicchunks.api.world;

import io.github.opencubicchunks.cubicchunks.api.util.CubePos;
import io.github.opencubicchunks.cubicchunks.api.util.NotCubicChunksWorldException;
import net.minecraft.core.BlockPos;

import java.util.function.Predicate;

import javax.annotation.ParametersAreNonnullByDefault;

/**
 * Cube-aware view of a world.
 *
 * <p><b>Partial port.</b> The 1.12.2 version also carried surface-detection default helpers
 * ({@code getSurfaceForCube}, {@code findTopBlock}, {@code canBeTopBlock} and the {@code SurfaceType}
 * enum). Those depend on APIs removed or changed in modern Minecraft (the {@code Material} system,
 * Forge leaf/foliage checks, {@code getLightOpacity}) and are only used by world generation, so they
 * are deferred to the worldgen port rather than reimplemented speculatively here.
 */
@ParametersAreNonnullByDefault
public interface ICubicWorld extends IMinMaxHeight {

    boolean isCubicWorld();

    /**
     * Returns the {@link ICubeProvider} for this world, or throws {@link NotCubicChunksWorldException}
     * if this is not a CubicChunks world.
     *
     * @return the cube provider
     */
    ICubeProvider getCubeCache();

    /**
     * Returns true iff the given Predicate evaluates to true for all cubes for block positions within blockRadius from
     * centerPos. Only cubes that exist are tested. If some cubes within that range aren't loaded - returns false.
     *
     * @param centerPos position to start at
     * @param blockRadius radius in block to test, starting from centerPos
     * @param test the test to apply
     * @return false if any invokation of the given predicate returns false, true otherwise
     */
    default boolean testForCubes(BlockPos centerPos, int blockRadius, Predicate<ICube> test) {
        return testForCubes(
                centerPos.getX() - blockRadius, centerPos.getY() - blockRadius, centerPos.getZ() - blockRadius,
                centerPos.getX() + blockRadius, centerPos.getY() + blockRadius, centerPos.getZ() + blockRadius,
                test
        );
    }

    /**
     * Returns true iff the given Predicate evaluates to true for all cubes for block positions between
     * BlockPos(minBlockX, minBlockY, minBlockZ) and BlockPos(maxBlockX, maxBlockY, maxBlockZ) (including the specified
     * positions). Only cubes that exist are tested. If some cubes within that range aren't loaded - returns false.
     *
     * @param minBlockX minimum block x coordinate
     * @param minBlockY minimum block y coordinate
     * @param minBlockZ minimum block z coordinate
     * @param maxBlockX maximum block x coordinate
     * @param maxBlockY maximum block y coordinate
     * @param maxBlockZ maximum block z coordinate
     * @param test the test to apply
     * @return false if any invokation of the given predicate returns false, true otherwise
     */
    default boolean testForCubes(int minBlockX, int minBlockY, int minBlockZ, int maxBlockX, int maxBlockY, int maxBlockZ, Predicate<ICube> test) {
        return testForCubes(
                CubePos.fromBlockCoords(minBlockX, minBlockY, minBlockZ),
                CubePos.fromBlockCoords(maxBlockX, maxBlockY, maxBlockZ),
                test
        );
    }

    /**
     * Returns true iff the given Predicate evaluates to true for given cube and neighbors.
     * Only cubes that exist are tested. If some cubes within that range aren't loaded - returns false.
     *
     * @param start start cube position
     * @param end end cube position
     * @param test the test to apply
     * @return false if any invokation of the given predicate returns false, true otherwise
     */
    boolean testForCubes(CubePos start, CubePos end, Predicate<? super ICube> test);

    /**
     * Return the actual world height for this world. Typically this is 256 for worlds with a sky, and 128 for worlds
     * without.
     *
     * @return The actual world height
     */
    int getActualHeight();

    ICube getCubeFromCubeCoords(int cubeX, int cubeY, int cubeZ);

    default ICube getCubeFromCubeCoords(CubePos pos) {
        return getCubeFromCubeCoords(pos.getX(), pos.getY(), pos.getZ());
    }


    ICube getCubeFromBlockCoords(BlockPos pos);

    int getEffectiveHeight(int blockX, int blockZ);

    boolean isBlockColumnLoaded(BlockPos pos);

    boolean isBlockColumnLoaded(BlockPos pos, boolean allowEmpty);

    int getMinGenerationHeight();

    int getMaxGenerationHeight();
}
