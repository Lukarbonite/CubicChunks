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

import com.mojang.serialization.Codec;
import io.github.opencubicchunks.cubicchunks.api.util.Coords;
import io.github.opencubicchunks.cubicchunks.api.util.CubePos;
import io.github.opencubicchunks.cubicchunks.api.world.ICube;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.PalettedContainer;
import net.minecraft.world.level.chunk.Strategy;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

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

    /** NBT key under which the block-state container is stored. */
    private static final String NBT_BLOCK_STATES = "block_states";

    /** Palette strategy shared by the block-state container and its codec. */
    private static final Strategy<BlockState> BLOCK_STRATEGY =
            Strategy.createForBlockStates(Block.BLOCK_STATE_REGISTRY);

    /** Read/write codec for the block-state container (matches how a {@code LevelChunkSection} serializes). */
    private static final Codec<PalettedContainer<BlockState>> BLOCKS_CODEC =
            PalettedContainer.codecRW(BlockState.CODEC, BLOCK_STRATEGY, AIR);

    private final CubePos coords;
    private PalettedContainer<BlockState> blocks;
    private int nonAirBlockCount;

    private final EntityContainer entities = new EntityContainer();
    private final Map<BlockPos, BlockEntity> blockEntities = new HashMap<>();

    public Cube(CubePos coords) {
        this.coords = coords;
        this.blocks = new PalettedContainer<>(AIR, BLOCK_STRATEGY);
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

    @Override
    public void addEntity(Entity entity) {
        entities.addEntity(entity);
    }

    @Override
    public boolean removeEntity(Entity entity) {
        return entities.remove(entity);
    }

    @Override
    public Collection<Entity> getEntities() {
        return entities.getEntities();
    }

    /** The backing entity container (in-memory only until the storage subsystem is ported). */
    public EntityContainer getEntityContainer() {
        return entities;
    }

    @Nullable
    @Override
    public BlockEntity getBlockEntity(BlockPos pos) {
        return blockEntities.get(pos);
    }

    @Override
    public void addBlockEntity(BlockEntity blockEntity) {
        blockEntities.put(blockEntity.getBlockPos().immutable(), blockEntity);
    }

    @Override
    public void removeBlockEntity(BlockPos pos) {
        blockEntities.remove(pos);
    }

    @Override
    public Map<BlockPos, BlockEntity> getBlockEntities() {
        return Collections.unmodifiableMap(blockEntities);
    }

    // ==========================================================================
    // Serialization (block storage only)
    // ==========================================================================

    /**
     * Writes this cube's block storage into {@code tag} under {@value #NBT_BLOCK_STATES}, palette
     * compressed via {@link #BLOCKS_CODEC} exactly as a vanilla {@code LevelChunkSection} would.
     *
     * <p><b>Partial port.</b> Entities and block entities are not serialized here: modern Minecraft
     * persists entities through a separate per-region entity store and block entities need a world to
     * reconstruct, so both wait on the storage/world subsystem. Coordinates are not written either;
     * the disk layer keys cubes by {@link CubePos}.
     *
     * @param tag the compound to write into
     * @return {@code tag}, for chaining
     */
    public CompoundTag writeToNbt(CompoundTag tag) {
        tag.put(NBT_BLOCK_STATES, BLOCKS_CODEC.encodeStart(NbtOps.INSTANCE, blocks).getOrThrow());
        return tag;
    }

    /**
     * Replaces this cube's block storage with the container decoded from {@code tag} (written by
     * {@link #writeToNbt}) and recomputes the non-air block count. If the tag has no block-state
     * entry the cube is left empty (all air).
     *
     * @param tag the compound to read from
     */
    public void readFromNbt(CompoundTag tag) {
        Tag blockStatesTag = tag.get(NBT_BLOCK_STATES);
        if (blockStatesTag == null) {
            this.blocks = new PalettedContainer<>(AIR, BLOCK_STRATEGY);
            this.nonAirBlockCount = 0;
            return;
        }
        this.blocks = BLOCKS_CODEC.parse(NbtOps.INSTANCE, blockStatesTag).getOrThrow();
        recountNonAirBlocks();
    }

    /** Recomputes {@link #nonAirBlockCount} by scanning the container (used after deserialization). */
    private void recountNonAirBlocks() {
        int count = 0;
        for (int y = 0; y < ICube.SIZE; y++) {
            for (int z = 0; z < ICube.SIZE; z++) {
                for (int x = 0; x < ICube.SIZE; x++) {
                    if (!blocks.get(x, y, z).isAir()) {
                        count++;
                    }
                }
            }
        }
        this.nonAirBlockCount = count;
    }
}
