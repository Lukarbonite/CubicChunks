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
package io.github.opencubicchunks.cubicchunks.world.cube;

import io.github.opencubicchunks.cubicchunks.api.util.Coords;
import io.github.opencubicchunks.cubicchunks.api.util.CubePos;
import io.github.opencubicchunks.cubicchunks.api.world.ICube;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.PalettedContainer;
import net.minecraft.world.level.chunk.Strategy;

import javax.annotation.Nullable;

/**
 * The runtime storage of a single cube (16x16x16 blocks).
 *
 * <p>First core slice: backs block storage with a vanilla {@link PalettedContainer} of block states
 * (palette-compressed, using the static {@link Block#BLOCK_STATE_REGISTRY}, so a cube can exist
 * without a world). Biomes, block/sky light, tile entities and entities are added as the world
 * subsystem is ported; at that point the container will likely be wrapped in a
 * {@code LevelChunkSection} for serialization and rendering.
 */
public class Cube implements ICube {

    private static final BlockState AIR = Blocks.AIR.defaultBlockState();

    private final CubePos coords;
    private final PalettedContainer<BlockState> blocks;
    private int nonAirBlockCount;

    public Cube(CubePos coords) {
        this.coords = coords;
        this.blocks = new PalettedContainer<>(AIR, Strategy.createForBlockStates(Block.BLOCK_STATE_REGISTRY));
        this.nonAirBlockCount = 0;
    }

    @Override
    public int getX() {
        return coords.getX();
    }

    @Override
    public int getY() {
        return coords.getY();
    }

    @Override
    public int getZ() {
        return coords.getZ();
    }

    @Override
    public CubePos getCoords() {
        return coords;
    }

    @Override
    public boolean isEmpty() {
        return nonAirBlockCount == 0;
    }

    @Override
    public BlockState getBlockState(BlockPos pos) {
        return getBlockState(Coords.blockToLocal(pos.getX()), Coords.blockToLocal(pos.getY()), Coords.blockToLocal(pos.getZ()));
    }

    /**
     * Returns the block state at the given cube-local coordinates.
     *
     * @param localX cube-local x (0..15)
     * @param localY cube-local y (0..15)
     * @param localZ cube-local z (0..15)
     */
    public BlockState getBlockState(int localX, int localY, int localZ) {
        return blocks.get(localX, localY, localZ);
    }

    @Nullable
    @Override
    public BlockState setBlockState(BlockPos pos, BlockState newstate) {
        return setBlockState(Coords.blockToLocal(pos.getX()), Coords.blockToLocal(pos.getY()), Coords.blockToLocal(pos.getZ()), newstate);
    }

    /**
     * Sets the block state at the given cube-local coordinates.
     *
     * @param localX cube-local x (0..15)
     * @param localY cube-local y (0..15)
     * @param localZ cube-local z (0..15)
     * @param newstate the new block state
     * @return the previous block state, or {@code null} if unchanged
     */
    @Nullable
    public BlockState setBlockState(int localX, int localY, int localZ, BlockState newstate) {
        BlockState old = blocks.getAndSet(localX, localY, localZ, newstate);
        if (old == newstate) {
            return null;
        }
        if (old.isAir() && !newstate.isAir()) {
            nonAirBlockCount++;
        } else if (!old.isAir() && newstate.isAir()) {
            nonAirBlockCount--;
        }
        return old;
    }

    /**
     * The backing palette-compressed block storage. Block states only for now; biomes and light are
     * added with world integration.
     */
    public PalettedContainer<BlockState> getBlockStates() {
        return blocks;
    }
}
