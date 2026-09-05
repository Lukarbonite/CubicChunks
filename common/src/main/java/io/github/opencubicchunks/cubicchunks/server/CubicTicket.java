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
package io.github.opencubicchunks.cubicchunks.server;

import io.github.opencubicchunks.cubicchunks.api.util.CubePos;
import io.github.opencubicchunks.cubicchunks.api.world.ICubicTicket;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import it.unimi.dsi.fastutil.ints.IntSets;
import net.minecraft.world.level.ChunkPos;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import javax.annotation.ParametersAreNonnullByDefault;

/**
 * A concrete, loader-neutral {@link ICubicTicket}: the source of truth for the set of cubes a single
 * ticket force-loads, grouped by column position (the shape {@link #getAllForcedChunkCubes()} exposes).
 *
 * <p>The 1.12.2 mod implemented this on Forge's {@code ForgeChunkManager.Ticket}. Modern loaders drive
 * vanilla {@code LevelChunk} loading through {@code DistanceManager} ticket levels, so a platform ticket
 * type will back this later. Until then this standalone ticket lets the {@link CubicTicketManager} on the
 * server world track force-loads without a platform binding, and gives tests a ticket to hold.
 *
 * <p>Not thread-safe: force/unforce are expected on the server thread, as with vanilla chunk tickets.
 */
@ParametersAreNonnullByDefault
public class CubicTicket implements ICubicTicket {

    /** Column position -> set of forced cube Y positions in that column. */
    private final Map<ChunkPos, IntSet> forcedByColumn = new HashMap<>();

    /**
     * Records the cube at {@code pos} as forced by this ticket.
     *
     * @return {@code true} if it was not already forced (a new entry), {@code false} if it was
     */
    public boolean addForced(CubePos pos) {
        IntSet ys = forcedByColumn.computeIfAbsent(pos.chunkPos(), key -> new IntOpenHashSet());
        return ys.add(pos.getY());
    }

    /**
     * Removes the cube at {@code pos} from this ticket's forced set, dropping the column entry when it
     * empties.
     *
     * @return {@code true} if the cube had been forced (an entry was removed), {@code false} otherwise
     */
    public boolean removeForced(CubePos pos) {
        ChunkPos column = pos.chunkPos();
        IntSet ys = forcedByColumn.get(column);
        if (ys == null) {
            return false;
        }
        boolean removed = ys.remove(pos.getY());
        if (ys.isEmpty()) {
            forcedByColumn.remove(column);
        }
        return removed;
    }

    /** Whether this ticket currently forces the cube at {@code pos}. */
    public boolean isForced(CubePos pos) {
        IntSet ys = forcedByColumn.get(pos.chunkPos());
        return ys != null && ys.contains(pos.getY());
    }

    /** The number of cubes this ticket forces, across all columns. */
    public int forcedCount() {
        int total = 0;
        for (IntSet ys : forcedByColumn.values()) {
            total += ys.size();
        }
        return total;
    }

    @Override
    public Map<ChunkPos, IntSet> getAllForcedChunkCubes() {
        // Unmodifiable snapshot: the interface permits a copy, and callers must not mutate our state.
        Map<ChunkPos, IntSet> copy = new HashMap<>(forcedByColumn.size());
        for (Map.Entry<ChunkPos, IntSet> entry : forcedByColumn.entrySet()) {
            copy.put(entry.getKey(), IntSets.unmodifiable(new IntOpenHashSet(entry.getValue())));
        }
        return Collections.unmodifiableMap(copy);
    }
}
