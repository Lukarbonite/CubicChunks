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
import io.github.opencubicchunks.cubicchunks.api.util.CubePos;
import io.github.opencubicchunks.cubicchunks.api.world.ICube;
import io.github.opencubicchunks.cubicchunks.api.world.IColumn;
import io.github.opencubicchunks.cubicchunks.api.world.ICubeProviderServer;
import io.github.opencubicchunks.cubicchunks.api.world.ICubicWorldServer;
import io.github.opencubicchunks.cubicchunks.api.worldgen.ICubeGenerator;
import io.github.opencubicchunks.cubicchunks.api.world.ICubicTicket;
import io.github.opencubicchunks.cubicchunks.server.CubeProviderServer;
import io.github.opencubicchunks.cubicchunks.server.CubicTicketManager;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

import java.util.function.Predicate;

/**
 * Makes a {@link ServerLevel} a cube-aware {@link ICubicWorldServer}: it lazily owns a
 * {@link CubeProviderServer} and routes cube/column access through it, so the in-memory cube index is
 * reachable from the live server world. Build-height ({@code getMinHeight}/{@code getMaxHeight}) comes
 * from {@code LevelMixin} on the shared {@link net.minecraft.world.level.Level} supertype.
 *
 * <p><b>Partial port.</b> With no disk IO or generator yet, {@code getCubeCache()} hands back the
 * in-memory provider; the generation subsystem ({@link #getCubeGenerator}) throws until it is ported.
 * The ticket subsystem ({@link #forceChunk}/{@link #reorderChunk}/{@link #unforceChunk}) is backed by a
 * loader-neutral {@link CubicTicketManager} that tracks force-loads and keeps forced cubes resident in
 * the provider; {@link #unloadOldCubes} is a documented no-op (vanilla drives unloading, and the manager
 * exposes which cubes are forced for the future unload bridge). {@link #isCubicWorld} returns
 * {@code true} unconditionally for now; gating it on a per-world flag arrives with the world-type /
 * world-preset port.
 */
@Mixin(ServerLevel.class)
public abstract class ServerLevelMixin implements ICubicWorldServer {

    @Unique private CubeProviderServer cubicchunks$cubeProvider;
    @Unique private CubicTicketManager cubicchunks$ticketManager;

    @Override
    public ICubeProviderServer getCubeCache() {
        if (cubicchunks$cubeProvider == null) {
            cubicchunks$cubeProvider = new CubeProviderServer();
        }
        return cubicchunks$cubeProvider;
    }

    @Override
    public boolean isCubicWorld() {
        return true;
    }

    @Override
    public int getActualHeight() {
        return getMaxHeight() - getMinHeight();
    }

    @Override
    public int getMinGenerationHeight() {
        return getMinHeight();
    }

    @Override
    public int getMaxGenerationHeight() {
        return getMaxHeight();
    }

    @Override
    public ICube getCubeFromCubeCoords(int cubeX, int cubeY, int cubeZ) {
        return getCubeCache().getCube(cubeX, cubeY, cubeZ);
    }

    @Override
    public ICube getCubeFromBlockCoords(BlockPos pos) {
        return getCubeFromCubeCoords(
                Coords.blockToCube(pos.getX()),
                Coords.blockToCube(pos.getY()),
                Coords.blockToCube(pos.getZ()));
    }

    @Override
    public int getEffectiveHeight(int blockX, int blockZ) {
        IColumn column = getCubeCache().getLoadedColumn(Coords.blockToCube(blockX), Coords.blockToCube(blockZ));
        if (column == null) {
            return getMinHeight();
        }
        return column.getHeightValue(Coords.blockToLocal(blockX), 0, Coords.blockToLocal(blockZ));
    }

    @Override
    public boolean isBlockColumnLoaded(BlockPos pos) {
        return isBlockColumnLoaded(pos, true);
    }

    @Override
    public boolean isBlockColumnLoaded(BlockPos pos, boolean allowEmpty) {
        IColumn column = getCubeCache().getLoadedColumn(Coords.blockToCube(pos.getX()), Coords.blockToCube(pos.getZ()));
        if (column == null) {
            return false;
        }
        // allowEmpty=false requires the column to actually hold cubes; the ticket-driven "empty column"
        // distinction from 1.12.2 does not exist yet, so this is approximated by loaded-cube presence.
        return allowEmpty || column.hasLoadedCubes();
    }

    @Override
    public boolean testForCubes(CubePos start, CubePos end, Predicate<? super ICube> test) {
        int minX = Math.min(start.getX(), end.getX());
        int minY = Math.min(start.getY(), end.getY());
        int minZ = Math.min(start.getZ(), end.getZ());
        int maxX = Math.max(start.getX(), end.getX());
        int maxY = Math.max(start.getY(), end.getY());
        int maxZ = Math.max(start.getZ(), end.getZ());
        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    ICube cube = getCubeCache().getLoadedCube(x, y, z);
                    // Only loaded cubes are tested; a missing cube in range fails the whole test.
                    if (cube == null || !test.test(cube)) {
                        return false;
                    }
                }
            }
        }
        return true;
    }

    @Override
    public ICubeGenerator getCubeGenerator() {
        throw new UnsupportedOperationException("Cube generator not ported yet (generation pipeline deferred)");
    }

    @Override
    public void unloadOldCubes() {
        // No-op: automatic cube unloading is driven by vanilla's chunk system, not yet bridged here.
        // When that bridge lands it must skip cubes the ticket manager still reports as forced.
    }

    /**
     * The per-world force-load registry, created lazily on the same provider {@link #getCubeCache}
     * returns so forced cubes and cached cubes share one index.
     */
    @Unique
    private CubicTicketManager cubicchunks$ticketManager() {
        if (cubicchunks$ticketManager == null) {
            cubicchunks$ticketManager = new CubicTicketManager(getCubeCache());
        }
        return cubicchunks$ticketManager;
    }

    @Override
    public void forceChunk(ICubicTicket ticket, CubePos chunk) {
        cubicchunks$ticketManager().forceChunk(ticket, chunk);
    }

    @Override
    public void reorderChunk(ICubicTicket ticket, CubePos chunk) {
        cubicchunks$ticketManager().reorderChunk(ticket, chunk);
    }

    @Override
    public void unforceChunk(ICubicTicket ticket, CubePos chunk) {
        cubicchunks$ticketManager().unforceChunk(ticket, chunk);
    }
}
