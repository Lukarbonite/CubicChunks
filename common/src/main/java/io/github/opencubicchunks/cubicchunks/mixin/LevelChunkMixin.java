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
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.PalettedContainerFactory;
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
 * <p><b>Scope of this slice.</b> Only the two primary block accessors are routed. Block entities,
 * lighting updates, neighbor notifications and heightmap updates that vanilla normally performs inside
 * {@code setBlockState} are intentionally not reproduced yet; they belong to later bridging steps.
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
        cir.setReturnValue(old);
    }
}
