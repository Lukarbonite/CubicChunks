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
import io.github.opencubicchunks.cubicchunks.world.ICubicWorldHeightData;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelHeightAccessor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

/**
 * Gives every {@link Level} a mutable CubicChunks build-height range that overrides vanilla's when
 * set. This is the write-side of the height seam: {@link #getMinHeight()}/{@link #getMaxHeight()}
 * return the custom range once {@link #cubicchunks$setHeightData} has been called (e.g. from
 * {@code PacketCubicWorldData} on the client), otherwise they fall through to vanilla height.
 *
 * <p>{@code LevelHeightAccessorMixin} still supplies the read-only vanilla passthrough for other
 * {@link LevelHeightAccessor}s (chunks, world-gen regions); this concrete {@code Level} override
 * takes precedence for levels.
 */
@Mixin(Level.class)
public abstract class LevelMixin implements IMinMaxHeight, ICubicWorldHeightData {

    @Unique private boolean cubicchunks$hasCustomHeight = false;
    @Unique private int cubicchunks$minHeight;
    @Unique private int cubicchunks$maxHeight;

    @Override
    public int getMinHeight() {
        if (cubicchunks$hasCustomHeight) {
            return cubicchunks$minHeight;
        }
        return ((LevelHeightAccessor) this).getMinY();
    }

    @Override
    public int getMaxHeight() {
        if (cubicchunks$hasCustomHeight) {
            return cubicchunks$maxHeight;
        }
        LevelHeightAccessor self = (LevelHeightAccessor) this;
        return self.getMinY() + self.getHeight();
    }

    @Override
    public void cubicchunks$setHeightData(int minHeight, int maxHeight) {
        this.cubicchunks$minHeight = minHeight;
        this.cubicchunks$maxHeight = maxHeight;
        this.cubicchunks$hasCustomHeight = true;
    }

    @Override
    public boolean cubicchunks$hasCustomHeightData() {
        return this.cubicchunks$hasCustomHeight;
    }
}
