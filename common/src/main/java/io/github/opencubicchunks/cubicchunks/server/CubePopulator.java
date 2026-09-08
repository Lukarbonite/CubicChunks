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
import io.github.opencubicchunks.cubicchunks.api.world.ICubeProviderServer;
import io.github.opencubicchunks.cubicchunks.api.world.ICubeProviderServer.Requirement;
import io.github.opencubicchunks.cubicchunks.api.worldgen.ICubeGenerator;
import io.github.opencubicchunks.cubicchunks.world.cube.Cube;

import javax.annotation.Nullable;

/**
 * The 3D neighbour population-offset scheme: decides when a cube may be decorated (the generator's
 * {@code populate} stage) and drives it, so a cube is populated only once the cubes around it are
 * generated.
 *
 * <p><b>Why an offset is needed.</b> Vanilla places features at the chunk {@code FEATURES} status and a
 * feature originating in one chunk can write into its neighbours; to keep results deterministic and
 * seamless, a chunk is decorated only after the neighbours it can reach exist. The 2D game uses a 2x2
 * offset for this. Cubes are 3D, so the same idea becomes a {@code (2r+1)^3} box (radius
 * {@link ICubeGenerator#getPopulationRadius()}): a cube is <i>population-ready</i> only when every cube
 * in that box is generated.
 *
 * <p>Two entry points implement the two directions of the dependency:
 * <ul>
 *   <li>{@link #ensurePopulated} (pull): to decorate a specific cube now, generate its whole
 *       neighbourhood, then decorate the cube once. Used by the provider's {@code POPULATE} requests.</li>
 *   <li>{@link #onCubeGenerated} (push): when a cube finishes generating, decorate any already-generated
 *       neighbour it just completed the neighbourhood of. This never forces generation (it terminates),
 *       and is what a streaming chunk pipeline calls as cubes stream in.</li>
 * </ul>
 * Decoration runs at most once per cube, guarded by {@link Cube#isPopulated()}.
 */
public final class CubePopulator {

    private final ICubeProviderServer provider;
    private final ICubeGenerator generator;

    public CubePopulator(ICubeProviderServer provider, ICubeGenerator generator) {
        this.provider = provider;
        this.generator = generator;
    }

    /** The required generated-neighbour radius, from the generator (clamped to >= 0). */
    private int radius() {
        return Math.max(0, generator.getPopulationRadius());
    }

    /**
     * Whether every cube in {@code centre}'s required neighbourhood is already generated, so {@code centre}
     * may be decorated. Does not generate anything.
     */
    public boolean isPopulationReady(CubePos centre) {
        int r = radius();
        for (int dx = -r; dx <= r; dx++) {
            for (int dy = -r; dy <= r; dy++) {
                for (int dz = -r; dz <= r; dz++) {
                    if (!provider.isCubeGenerated(centre.getX() + dx, centre.getY() + dy, centre.getZ() + dz)) {
                        return false;
                    }
                }
            }
        }
        return true;
    }

    /**
     * Generates {@code centre}'s whole neighbourhood (so cross-cube features have terrain to read and
     * write), then decorates {@code centre} exactly once. Only {@code centre} is decorated here; each
     * neighbour is decorated by its own {@code ensurePopulated}. Idempotent.
     *
     * @param centre the cube to populate
     * @return the populated centre cube
     */
    public ICube ensurePopulated(CubePos centre) {
        int r = radius();
        for (int dx = -r; dx <= r; dx++) {
            for (int dy = -r; dy <= r; dy++) {
                for (int dz = -r; dz <= r; dz++) {
                    provider.getCube(centre.getX() + dx, centre.getY() + dy, centre.getZ() + dz, Requirement.GENERATE);
                }
            }
        }
        ICube cube = provider.getCube(centre.getX(), centre.getY(), centre.getZ(), Requirement.GENERATE);
        decorateOnce(centre, cube);
        return cube;
    }

    /**
     * Push trigger: after the cube at {@code generated} finished generating, decorate any cube in its
     * neighbourhood that is now population-ready (i.e. this was the last missing neighbour). Never forces
     * generation, so it always terminates.
     *
     * @param generated the cube that was just generated
     */
    public void onCubeGenerated(CubePos generated) {
        int r = radius();
        for (int dx = -r; dx <= r; dx++) {
            for (int dy = -r; dy <= r; dy++) {
                for (int dz = -r; dz <= r; dz++) {
                    int cx = generated.getX() + dx;
                    int cy = generated.getY() + dy;
                    int cz = generated.getZ() + dz;
                    ICube cube = provider.getLoadedCube(cx, cy, cz);
                    if (cube instanceof Cube concrete && !concrete.isPopulated()) {
                        CubePos pos = new CubePos(cx, cy, cz);
                        if (isPopulationReady(pos)) {
                            decorate(pos, concrete);
                        }
                    }
                }
            }
        }
    }

    /** Decorates the cube once if it has not been populated yet. */
    private void decorateOnce(CubePos pos, @Nullable ICube cube) {
        if (cube instanceof Cube concrete && !concrete.isPopulated()) {
            decorate(pos, concrete);
        }
    }

    private void decorate(CubePos pos, Cube cube) {
        generator.populate(pos, cube);
        cube.setPopulated(true);
    }
}
