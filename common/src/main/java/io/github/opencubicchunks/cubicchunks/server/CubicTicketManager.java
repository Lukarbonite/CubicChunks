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
import io.github.opencubicchunks.cubicchunks.api.world.ICubeProviderServer;
import io.github.opencubicchunks.cubicchunks.api.world.ICubicTicket;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import javax.annotation.ParametersAreNonnullByDefault;

/**
 * Per-world registry of force-loaded cubes, backing {@code ServerLevel.forceChunk}/{@code reorderChunk}/
 * {@code unforceChunk}. It tracks which cubes each {@link ICubicTicket} forces and keeps an aggregate
 * reference count so a cube stays force-loaded until the last ticket releases it (many tickets may force
 * the same cube).
 *
 * <p>Forcing a cube materializes it in the {@link ICubeProviderServer} with {@code Requirement.GENERATE}
 * (an empty in-memory cube while the generator is deferred) and, since the provider never drops cubes on
 * its own yet, that is enough to keep it "loaded". The reference count is the seam a real unloader will
 * consult: {@link #isForced} answers "may this cube be unloaded?" for the future {@code unloadOldCubes}
 * bridge, which is why unforce decrements rather than deletes eagerly.
 *
 * <p>Accounting is authoritative here (per-ticket sets + aggregate counts); for our own {@link CubicTicket}
 * the manager also mirrors the change into the ticket so {@link ICubicTicket#getAllForcedChunkCubes()}
 * reflects reality. Foreign ticket implementations are still tracked by the manager's own maps.
 *
 * <p>Not thread-safe: expected to be driven from the server thread, as with vanilla chunk tickets.
 */
@ParametersAreNonnullByDefault
public class CubicTicketManager {

    /** What each ticket currently forces (authoritative per-ticket accounting). */
    private final Map<ICubicTicket, Set<CubePos>> forcedByTicket = new HashMap<>();

    /** How many (ticket, cube) references force each cube; a cube is force-loaded while its count > 0. */
    private final Map<CubePos, Integer> refCounts = new HashMap<>();

    private final ICubeProviderServer provider;

    public CubicTicketManager(ICubeProviderServer provider) {
        this.provider = provider;
    }

    /**
     * Force-loads {@code pos} on behalf of {@code ticket}. Idempotent per (ticket, pos): forcing the same
     * cube twice with one ticket counts once. When the cube's aggregate count rises from zero it is
     * loaded/created in the provider.
     */
    public void forceChunk(ICubicTicket ticket, CubePos pos) {
        Set<CubePos> forced = forcedByTicket.computeIfAbsent(ticket, key -> new HashSet<>());
        if (!forced.add(pos)) {
            return; // already forced by this ticket
        }
        if (ticket instanceof CubicTicket cubicTicket) {
            cubicTicket.addForced(pos);
        }
        int count = refCounts.merge(pos, 1, Integer::sum);
        if (count == 1) {
            // First reference: materialize the cube so it is resident (empty cube; generator deferred).
            provider.getCube(pos.getX(), pos.getY(), pos.getZ(), ICubeProviderServer.Requirement.GENERATE);
        }
    }

    /**
     * Re-affirms a force-load for {@code ticket} at {@code pos}. In the 1.12.2 mod this bumped the cube's
     * priority in the load queue; there is no priority queue here yet, so this only ensures the cube is
     * resident (forcing it if the ticket had not already).
     */
    public void reorderChunk(ICubicTicket ticket, CubePos pos) {
        forceChunk(ticket, pos);
    }

    /**
     * Releases {@code ticket}'s force-load of {@code pos}. When the last ticket releases the cube its
     * aggregate count reaches zero and it becomes eligible for unloading again (the manager does not drop
     * it from the provider itself; the unload bridge will).
     */
    public void unforceChunk(ICubicTicket ticket, CubePos pos) {
        Set<CubePos> forced = forcedByTicket.get(ticket);
        if (forced == null || !forced.remove(pos)) {
            return; // not forced by this ticket
        }
        if (forced.isEmpty()) {
            forcedByTicket.remove(ticket);
        }
        if (ticket instanceof CubicTicket cubicTicket) {
            cubicTicket.removeForced(pos);
        }
        Integer count = refCounts.get(pos);
        if (count == null) {
            return;
        }
        if (count <= 1) {
            refCounts.remove(pos);
        } else {
            refCounts.put(pos, count - 1);
        }
    }

    /** Whether any ticket currently force-loads the cube at {@code pos}. */
    public boolean isForced(CubePos pos) {
        return refCounts.containsKey(pos);
    }

    /** An unmodifiable view of every cube currently force-loaded by at least one ticket. */
    public Set<CubePos> getForcedCubes() {
        return Collections.unmodifiableSet(refCounts.keySet());
    }

    /** The number of distinct cubes currently force-loaded. */
    public int forcedCubeCount() {
        return refCounts.size();
    }
}
