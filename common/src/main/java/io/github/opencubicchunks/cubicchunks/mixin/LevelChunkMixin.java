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
package io.github.opencubicchunks.cubicchunks.mixin;

import io.github.opencubicchunks.cubicchunks.api.util.Coords;
import io.github.opencubicchunks.cubicchunks.api.world.ICube;
import io.github.opencubicchunks.cubicchunks.api.world.IColumn;
import io.github.opencubicchunks.cubicchunks.world.ICubicLevelChunk;
import io.github.opencubicchunks.cubicchunks.world.column.CubeSectionStorage;
import io.github.opencubicchunks.cubicchunks.world.cube.Cube;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.PalettedContainerFactory;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.lighting.ChunkSkyLightSources;
import net.minecraft.world.level.lighting.LightEngine;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import javax.annotation.Nullable;

/**
 * Routes a {@link LevelChunk}'s block get/set to CubicChunks cubes when cubic mode is enabled on it.
 * This is the keystone of block-access bridging: on 1.12.2 the column <i>was</i> the chunk and its
 * {@code getBlockState}/{@code setBlockState} delegated to the cube; here the same is achieved by
 * head-injecting into {@link LevelChunk} and cancelling the vanilla section path.
 *
 * <p><b>Opt-in and off by default.</b> Until the world-type and chunk-pipeline work land, no chunk
 * enables cubic mode, so ordinary worlds are byte-for-byte vanilla. When a chunk is cubic, reads of an
 * unloaded cube return air and writes create the cube on demand through the column.
 *
 * <p><b>Scope of this slice.</b> The two primary block accessors are routed, and the cube-routed write
 * reproduces the side effects vanilla runs inside {@code setBlockState} (see
 * {@link #cubicchunks$afterSetBlock}): heightmap updates, per-write lighting (sky-light sources +
 * {@code checkBlock}), block-entity removal/creation, the old block's removal effects, and the new
 * block's {@code onPlace} (which schedules fluid spread and falling-block ticks, so water flows and sand
 * falls in cubic mode). The neighbor and shape notifications that {@code Level.setBlock} performs
 * <i>after</i> {@code setBlockState} still run normally (that call is not cancelled). The section
 * air/non-air emptiness toggle (relevant only when a whole 16&sup3; section flips between all-air and
 * occupied) is still not reproduced per write; section status is established on load instead.
 */
@Mixin(LevelChunk.class)
public abstract class LevelChunkMixin implements ICubicLevelChunk {

    @Unique private boolean cubicchunks$cubic = false;
    @Unique @Nullable private IColumn cubicchunks$column;
    @Unique @Nullable private CubeSectionStorage cubicchunks$sectionStorage;

    @Override
    public void cubicchunks$setCubic(IColumn column) {
        this.cubicchunks$column = column;
        this.cubicchunks$cubic = true;
        this.cubicchunks$sectionStorage = null; // rebuilt lazily against the new column
    }

    @Override
    public void cubicchunks$clearCubic() {
        this.cubicchunks$cubic = false;
        this.cubicchunks$column = null;
        this.cubicchunks$sectionStorage = null;
    }

    @Override
    public CubeSectionStorage cubicchunks$getSectionStorage() {
        if (cubicchunks$sectionStorage == null) {
            PalettedContainerFactory factory = PalettedContainerFactory.create(
                    ((LevelChunk) (Object) this).getLevel().registryAccess());
            cubicchunks$sectionStorage = new CubeSectionStorage(cubicchunks$column, factory);
        }
        return cubicchunks$sectionStorage;
    }

    @Override
    public boolean cubicchunks$isCubic() {
        return this.cubicchunks$cubic;
    }

    @Nullable
    @Override
    public IColumn cubicchunks$getColumn() {
        return this.cubicchunks$column;
    }

    @Inject(
            method = "getBlockState(Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/level/block/state/BlockState;",
            at = @At("HEAD"),
            cancellable = true)
    private void cubicchunks$getBlockStateFromCube(BlockPos pos, CallbackInfoReturnable<BlockState> cir) {
        if (!cubicchunks$cubic || cubicchunks$column == null) {
            return;
        }
        ICube cube = cubicchunks$column.getLoadedCube(Coords.blockToCube(pos.getY()));
        cir.setReturnValue(cube == null ? Blocks.AIR.defaultBlockState() : cube.getBlockState(pos));
    }

    @Inject(
            method = "setBlockState(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;I)Lnet/minecraft/world/level/block/state/BlockState;",
            at = @At("HEAD"),
            cancellable = true)
    private void cubicchunks$setBlockStateToCube(BlockPos pos, BlockState state, int flags,
                                                 CallbackInfoReturnable<BlockState> cir) {
        if (!cubicchunks$cubic || cubicchunks$column == null) {
            return;
        }
        Cube cube = (Cube) cubicchunks$column.getCube(Coords.blockToCube(pos.getY()));
        BlockState old = cube.setBlockState(pos, state);
        // Vanilla returns null when the state is unchanged, otherwise the previous state.
        if (old != null) {
            cubicchunks$afterSetBlock((LevelChunk) (Object) this, pos, old, state);
        }
        cir.setReturnValue(old);
    }

    /**
     * Reproduces the side effects vanilla runs inside {@code LevelChunk.setBlockState}, which our HEAD
     * cancel skips, following vanilla's order and guards:
     * <ol>
     *   <li>update the four kept heightmaps for the column;</li>
     *   <li>per-write lighting when the block's light properties changed: refresh the sky-light sources
     *       and {@code checkBlock} the position (so a placed opaque block casts shadow and a removed one
     *       lets light back in);</li>
     *   <li>drop the replaced block's block entity when the new state can't keep it
     *       ({@code preRemoveSideEffects} then {@code removeBlockEntity});</li>
     *   <li>run the old block's {@code affectNeighborsAfterRemoval} (its removal effect on neighbours);</li>
     *   <li>run the new block's {@code onPlace} &mdash; this is what schedules fluid spread ticks and
     *       falling-block ticks, so water flows and sand falls in cubic mode;</li>
     *   <li>create or refresh the new block's block entity.</li>
     * </ol>
     * Block entities live in the vanilla {@link LevelChunk} map (only block <i>states</i> route to
     * cubes), so without step&nbsp;3 a replaced block-entity block (e.g. a brushable block turned to
     * water) left a stale {@code BlockEntity} that vanilla saved and rejected on reload. The neighbour
     * and shape notifications that {@code Level.setBlock} performs after {@code setBlockState} are not
     * cancelled and are not repeated here. {@code movedByPiston} is {@code false}: piston movement is not
     * routed through this path. Steps&nbsp;4 and&nbsp;5 are server-only, matching vanilla.
     */
    @Unique
    private void cubicchunks$afterSetBlock(LevelChunk chunk, BlockPos pos, BlockState old, BlockState state) {
        Level level = chunk.getLevel();
        boolean serverSide = level instanceof ServerLevel; // ServerLevel is server-side by definition
        Block newBlock = state.getBlock();
        int localX = pos.getX() & 15;
        int localZ = pos.getZ() & 15;
        int y = pos.getY();

        // 1. Heightmaps (the four types vanilla keeps and updates per block change).
        cubicchunks$updateHeightmap(chunk, Heightmap.Types.MOTION_BLOCKING, localX, y, localZ, state);
        cubicchunks$updateHeightmap(chunk, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, localX, y, localZ, state);
        cubicchunks$updateHeightmap(chunk, Heightmap.Types.OCEAN_FLOOR, localX, y, localZ, state);
        cubicchunks$updateHeightmap(chunk, Heightmap.Types.WORLD_SURFACE, localX, y, localZ, state);

        // 2. Per-write lighting: only when the change alters light properties (opacity/emission).
        if (LightEngine.hasDifferentLightProperties(old, state)) {
            ChunkSkyLightSources sources = chunk.getSkyLightSources();
            if (sources != null) {
                sources.update(chunk, localX, y, localZ);
            }
            chunk.getLevel().getChunkSource().getLightEngine().checkBlock(pos);
        }

        // 3. Remove the old block entity unless the new state keeps it (mirrors vanilla's guard).
        if (old.hasBlockEntity() && !old.shouldChangedStateKeepBlockEntity(state)) {
            BlockEntity toRemove = chunk.getBlockEntity(pos);
            if (toRemove != null) {
                toRemove.preRemoveSideEffects(pos, old);
            }
            chunk.removeBlockEntity(pos);
        }

        // 4. Old block's removal effect on neighbours (only when the block actually changed).
        if (serverSide && old.getBlock() != newBlock) {
            old.affectNeighborsAfterRemoval((ServerLevel) level, pos, false);
        }

        // 5. New block's placement callback: schedules fluid/falling-block ticks, etc.
        if (serverSide) {
            state.onPlace(level, pos, old, false);
        }

        // 6. Create or refresh the new block's block entity.
        if (state.hasBlockEntity()) {
            BlockEntity existing = chunk.getBlockEntity(pos, LevelChunk.EntityCreationType.CHECK);
            if (existing == null || !existing.isValidBlockState(state)) {
                if (existing != null) {
                    chunk.removeBlockEntity(pos);
                }
                if (newBlock instanceof EntityBlock entityBlock) {
                    BlockEntity created = entityBlock.newBlockEntity(pos, state);
                    if (created != null) {
                        chunk.addAndRegisterBlockEntity(created);
                    }
                }
            } else {
                existing.setBlockState(state);
            }
        }
    }

    /** Updates one of the chunk's heightmaps for the column (the kept types are already primed). */
    @Unique
    private void cubicchunks$updateHeightmap(LevelChunk chunk, Heightmap.Types type, int localX, int y, int localZ,
                                             BlockState state) {
        chunk.getOrCreateHeightmapUnprimed(type).update(localX, y, localZ, state);
    }
}
