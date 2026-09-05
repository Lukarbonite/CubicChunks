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

import io.github.opencubicchunks.cubicchunks.world.ICubicLevelChunk;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.chunk.LevelChunkSection;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Redirects a chunk's section accessors to the cube-backed {@code CubeSectionStorage} when the chunk
 * is a cubic {@link ICubicLevelChunk} with cubic mode enabled. {@code getSection}/{@code getSections}
 * live on {@code ChunkAccess} (not overridden by {@code LevelChunk}), so the injection targets here.
 *
 * <p><b>Scope.</b> This wires the sparse section store into a live chunk's section accessors, so
 * within the chunk's current (still vanilla) height window a section maps to the corresponding cube's
 * section instead of the fixed array slot. It is guarded by {@code instanceof ICubicLevelChunk} (so
 * {@code ProtoChunk} and other {@code ChunkAccess}es are untouched) and by the per-chunk cubic flag
 * (off by default), so ordinary worlds stay vanilla. Extending the reported height beyond the vanilla
 * window, and the lighting/serialization loops that consume these, come in later steps.
 */
@Mixin(net.minecraft.world.level.chunk.ChunkAccess.class)
public abstract class ChunkAccessMixin {

    @Shadow
    protected LevelHeightAccessor levelHeightAccessor;

    @Inject(
            method = "getSection(I)Lnet/minecraft/world/level/chunk/LevelChunkSection;",
            at = @At("HEAD"),
            cancellable = true)
    private void cubicchunks$getSectionFromCube(int index, CallbackInfoReturnable<LevelChunkSection> cir) {
        if (this instanceof ICubicLevelChunk cubic && cubic.cubicchunks$isCubic()) {
            int sectionY = levelHeightAccessor.getSectionYFromSectionIndex(index);
            cir.setReturnValue(cubic.cubicchunks$getSectionStorage().getOrCreateSection(sectionY));
        }
    }

    @Inject(
            method = "getSections()[Lnet/minecraft/world/level/chunk/LevelChunkSection;",
            at = @At("HEAD"),
            cancellable = true)
    private void cubicchunks$getSectionsFromCubes(CallbackInfoReturnable<LevelChunkSection[]> cir) {
        if (this instanceof ICubicLevelChunk cubic && cubic.cubicchunks$isCubic()) {
            int count = levelHeightAccessor.getSectionsCount();
            int minSectionY = levelHeightAccessor.getMinSectionY();
            LevelChunkSection[] out = new LevelChunkSection[count];
            for (int i = 0; i < count; i++) {
                out[i] = cubic.cubicchunks$getSectionStorage().getOrCreateSection(minSectionY + i);
            }
            cir.setReturnValue(out);
        }
    }
}
