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
import io.github.opencubicchunks.cubicchunks.api.util.XYZAddressable;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Collection;
import java.util.Map;

import javax.annotation.Nullable;

/**
 * A cube: a {@value #SIZE}x{@value #SIZE}x{@value #SIZE} section of the world.
 *
 * <p><b>Partial port.</b> On 1.12.2 this interface also exposed entity, tile-entity, lighting and
 * capability access (backed by Forge/vanilla types). So far the size constants, coordinate accessors
 * and the block-storage contract are ported; the rest will be added as the world subsystem lands.
 */
public interface ICube extends XYZAddressable {

    /** Side length of a cube. */
    int SIZE = 16;

    /** Side length of a cube, as a double. */
    double SIZE_D = 16.0D;

    /** The position of this cube in cube coordinates. */
    CubePos getCoords();

    /** True if this cube contains only air. */
    boolean isEmpty();

    /**
     * Returns the block state at the given world position (which must lie within this cube).
     *
     * @param pos world block position
     * @return the block state
     */
    BlockState getBlockState(BlockPos pos);

    /**
     * Sets the block state at the given world position (which must lie within this cube).
     *
     * @param pos world block position
     * @param newstate the new block state
     * @return the previous block state, or {@code null} if there was no change
     */
    @Nullable BlockState setBlockState(BlockPos pos, BlockState newstate);

    /**
     * Adds an entity to this cube's in-memory entity set.
     *
     * @param entity the entity to add
     */
    void addEntity(Entity entity);

    /**
     * Removes an entity from this cube's entity set.
     *
     * @param entity the entity to remove
     * @return {@code true} if the entity was present and removed
     */
    boolean removeEntity(Entity entity);

    /**
     * Returns an unmodifiable view of the entities currently held by this cube.
     *
     * @return the entities in this cube
     */
    Collection<Entity> getEntities();

    /**
     * Returns the block entity at the given world position within this cube, or {@code null} if none.
     *
     * @param pos world block position
     * @return the block entity, or {@code null}
     */
    @Nullable BlockEntity getBlockEntity(BlockPos pos);

    /**
     * Adds (or replaces) a block entity at its own position within this cube.
     *
     * @param blockEntity the block entity to store
     */
    void addBlockEntity(BlockEntity blockEntity);

    /**
     * Removes the block entity at the given world position within this cube, if present.
     *
     * @param pos world block position
     */
    void removeBlockEntity(BlockPos pos);

    /**
     * Returns an unmodifiable view of this cube's block entities, keyed by world position.
     *
     * @return the block entities in this cube
     */
    Map<BlockPos, BlockEntity> getBlockEntities();
}
