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

import com.google.common.collect.Lists;
import io.github.opencubicchunks.cubicchunks.world.cube.Cube;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.annotation.ParametersAreNonnullByDefault;

/**
 * Stores the cubes of a single column, kept sorted by cube Y for binary-search access.
 *
 * <p><b>Partial port.</b> The 1.12.2 version also drove per-tick block ticking
 * ({@code getStoragesToTick}) and light re-checks ({@code enqueueRelightChecks}). Those depend on
 * the cube's block section and ticket system, and on the pre-1.13 light engine (removed in modern
 * Minecraft), so they are deferred until those subsystems are ported.
 */
@ParametersAreNonnullByDefault
public class CubeMap implements Iterable<Cube> {

    @Nonnull private final List<Cube> cubes = new ArrayList<>();

    /**
     * Removes the cube at {@code cubeY}.
     *
     * @param cubeY cube y position
     * @return the removed cube if it existed, otherwise {@code null}
     */
    @Nullable public Cube remove(int cubeY) {
        int index = binarySearch(cubeY);
        return index < cubes.size() && cubes.get(index).getY() == cubeY ? cubes.remove(index) : null;
    }

    /**
     * Returns the cube at {@code cubeY}, or {@code null} if none is stored there.
     *
     * @param cubeY cube y position
     * @return the cube, or {@code null}
     */
    @Nullable public Cube get(int cubeY) {
        int index = binarySearch(cubeY);
        return index < cubes.size() && cubes.get(index).getY() == cubeY ? cubes.get(index) : null;
    }

    /**
     * Adds a cube.
     *
     * @param cube the cube to add
     * @throws IllegalArgumentException if a cube already exists at that Y
     */
    public void put(Cube cube) {
        int searchIndex = binarySearch(cube.getY());
        if (this.contains(cube.getY(), searchIndex)) {
            throw new IllegalArgumentException("Cube at " + cube.getY() + " already exists!");
        }
        cubes.add(searchIndex, cube);
    }

    /**
     * Iterate over all cubes between {@code startY} and {@code endY} in this storage in order. If
     * {@code startY < endY}, order is bottom to top, otherwise order is top to bottom.
     *
     * @param startY initial cube y position
     * @param endY last cube y position
     * @return an iterator over the cubes
     */
    public Iterable<Cube> cubes(int startY, int endY) {
        boolean reverse = false;
        if (startY > endY) {
            int i = startY;
            startY = endY;
            endY = i;
            reverse = true;
        }

        int bottom = binarySearch(startY);
        int top = binarySearch(endY + 1); // subList()'s second arg is exclusive so we need to add 1

        if (bottom < cubes.size() && top <= cubes.size()) {
            return reverse ? Lists.reverse(cubes.subList(bottom, top)) : cubes.subList(bottom, top);
        } else {
            return Collections.emptyList();
        }
    }

    /**
     * Check if the target cube is stored here.
     *
     * @param cubeY the y coordinate of the cube
     * @param searchIndex the index to search at (from {@link #binarySearch(int)})
     * @return {@code true} if the cube is contained here, {@code false} otherwise
     */
    private boolean contains(int cubeY, int searchIndex) {
        return searchIndex < cubes.size() && cubes.get(searchIndex).getY() == cubeY;
    }

    /**
     * Iterate over all cubes in this storage.
     *
     * @return the iterator
     */
    @Override public Iterator<Cube> iterator() {
        return cubes.iterator();
    }

    /**
     * Retrieve a collection of all cubes within this storage.
     *
     * @return the collection
     */
    public Collection<Cube> all() {
        return cubes;
    }

    /**
     * Check if this storage is empty.
     *
     * @return {@code true} if there are no cubes in this storage, {@code false} otherwise
     */
    public boolean isEmpty() {
        return cubes.isEmpty();
    }

    /**
     * Binary search for the index of the specified cube. If the cube is not present, returns the index at which it
     * should be inserted.
     *
     * @param cubeY cube y position
     * @return the target index
     */
    private int binarySearch(int cubeY) {
        int start = 0;
        int end = cubes.size() - 1;
        int mid;

        while (start <= end) {
            mid = start + end >>> 1;

            int at = cubes.get(mid).getY();
            if (at < cubeY) { // we are below the target;
                start = mid + 1;
            } else if (at > cubeY) {
                end = mid - 1; // we are above the target
            } else {
                return mid;// found target!
            }
        }

        return start; // not found :(
    }
}
