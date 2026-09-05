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
import net.minecraft.server.level.ServerPlayer;

import javax.annotation.Nullable;

/**
 * Payload fired when a player starts watching a cube, dispatched through
 * {@link io.github.opencubicchunks.cubicchunks.api.event.CubicEvents#CUBE_WATCH}.
 *
 * <p>On 1.12.2 this extended Forge's {@code Event}; it is now a loader-neutral payload.
 */
public class CubeWatchEvent {

    @Nullable private final ICube cube;
    private final CubePos cubePos;
    private final ICubeWatcher cubeWatcher;
    private final ServerPlayer player;

    public CubeWatchEvent(@Nullable ICube cubeIn, CubePos cubePosIn, ICubeWatcher cubeWatcherIn, ServerPlayer playerIn) {
        cube = cubeIn;
        cubePos = cubePosIn;
        cubeWatcher = cubeWatcherIn;
        player = playerIn;
    }

    @Nullable
    public ICube getCube() {
        return cube;
    }

    public CubePos getCubePos() {
        return cubePos;
    }

    public ICubeWatcher getCubeWatcher() {
        return cubeWatcher;
    }

    public ICubicWorld getWorld() {
        return (ICubicWorld) player.level();
    }

    public ServerPlayer getPlayer() {
        return player;
    }
}
