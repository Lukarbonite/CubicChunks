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

import io.github.opencubicchunks.cubicchunks.api.world.IMinMaxHeight;
import net.minecraft.world.level.LevelHeightAccessor;
import org.spongepowered.asm.mixin.Mixin;

/**
 * Injects the CubicChunks {@link IMinMaxHeight} seam onto every vanilla
 * {@link LevelHeightAccessor} (levels, chunks, world-gen regions), so any of them can be cast
 * to {@code IMinMaxHeight} to query the world's build-height range.
 *
 * <p>By default this delegates to vanilla's own height, mirroring the 1.12.2 behaviour where the
 * seam reported the (then fixed 0..256) world height until CubicChunks overrode it. Extending the
 * range beyond vanilla limits is the job of later, cube-storage-aware overrides.
 *
 * <p>Note on semantics: 1.12.2 {@code getMaxHeight()} is the exclusive top (the Y above the top
 * block), so it maps to {@code getMinY() + getHeight()}, not vanilla's inclusive {@code getMaxY()}.
 */
@Mixin(LevelHeightAccessor.class)
public interface LevelHeightAccessorMixin extends IMinMaxHeight {

    @Override
    default int getMinHeight() {
        return ((LevelHeightAccessor) this).getMinY();
    }

    @Override
    default int getMaxHeight() {
        LevelHeightAccessor self = (LevelHeightAccessor) this;
        return self.getMinY() + self.getHeight();
    }
}
