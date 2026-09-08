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
package io.github.opencubicchunks.cubicchunks.api.worldgen;

import io.github.opencubicchunks.cubicchunks.api.util.CubePos;
import io.github.opencubicchunks.cubicchunks.api.world.ICube;
import net.minecraft.core.Holder;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.chunk.PalettedContainer;

import javax.annotation.Nullable;

/**
 * Generates the base terrain of a cubic world, one 16x16x16 cube at a time.
 *
 * <p><b>Partial port.</b> On 1.12.2 this declared the full cube/column generation, population and
 * biome API. This first 26.2 slice carries only base-terrain generation ({@link #generate}); biome
 * placement, population (features/structures) and per-cube lighting are added as those subsystems are
 * ported, mirroring the {@code GENERATE} -> {@code POPULATE} -> {@code LIGHT} requirement ladder in
 * {@link io.github.opencubicchunks.cubicchunks.api.world.ICubeProviderServer.Requirement}.
 *
 * <p><b>Purity contract (why it is written this way).</b> Generation must behave as a pure function of
 * {@code (world seed, cube position)} and nothing else. An implementation:
 * <ul>
 *   <li>must not read from or write to the live world, neighbouring cubes, or any shared mutable state;
 *       everything it needs (seed, noise routers, biome source) is captured once at construction;</li>
 *   <li>must be safe to call concurrently from many threads for distinct cube positions, and must
 *       produce identical output for a given {@link CubePos} no matter which thread runs it or how many
 *       times it runs;</li>
 *   <li>must confine all of its output to the supplied {@link CubePrimer}.</li>
 * </ul>
 * This is the same contract vanilla's {@code ChunkGenerator} obeys, and it is exactly what lets us stay
 * compatible with C2ME (RelativityMC/C2ME-fabric), whose {@code c2me-opts-worldgen-*} and
 * {@code c2me-threading-lighting} modules run generation and lighting off the server main thread. C2ME
 * documents that custom generators break only when they violate these threading assumptions, so keeping
 * generation a side-effect-free function of position is what makes C2ME support automatic rather than a
 * later retrofit. The cube-load/unload bridge deliberately stays off {@code ChunkMap}/{@code
 * ServerChunkCache} internals for the same reason (C2ME rewrites those).
 */
public interface ICubeGenerator {

    /**
     * Fills {@code primer} with the base terrain block states for the cube at {@code pos}. Positions the
     * generator leaves untouched stay air ({@link CubePrimer}'s default). Must satisfy the purity
     * contract described on this interface: output depends only on {@code pos} and state captured at
     * construction, with no live-world access.
     *
     * @param pos the position of the cube being generated
     * @param primer the scratch buffer to fill with this cube's block states
     */
    void generate(CubePos pos, CubePrimer primer);

    /**
     * Produces the biome cells for the cube at {@code pos} as a 4x4x4-cell biome container (the same
     * resolution and container type a vanilla {@code LevelChunkSection} carries), or {@code null} if this
     * generator does not place biomes (the cube then falls back to an empty, default-biome container).
     *
     * <p>Kept separate from {@link #generate} because biomes need the world's biome registry to build a
     * container, which the world-free cube storage cannot do; an implementation captures that registry at
     * construction. Must satisfy the same purity contract as {@link #generate}: output depends only on
     * {@code pos} and construction-time state, with no live-world access, so it is safe under C2ME's
     * off-main-thread worldgen.
     *
     * @param pos the position of the cube whose biomes are wanted
     * @return the cube's biome container, or {@code null} for no biomes
     */
    @Nullable
    default PalettedContainer<Holder<Biome>> generateBiomes(CubePos pos) {
        return null;
    }

    /**
     * Decorates an already-generated cube in place: the {@code POPULATE} stage, vanilla's "features"
     * equivalent (ores, plants, trees, small structures). Called once per cube, after its base terrain
     * exists, guarded by a populated flag so it never runs twice. The default is a no-op (a generator
     * that places no decoration).
     *
     * <p><b>Scope of the current port.</b> A faithful features port needs a cube-backed
     * {@code WorldGenLevel} and neighbour-aware placement (vanilla features write into a 3x3 chunk
     * neighbourhood, so a cube's decoration depends on its neighbours being generated first). Neither is
     * ported yet, so this slice only carries a self-contained, single-cube placeholder decorator; real
     * vanilla feature/structure placement lands in a later slice. Because it may run off the main thread
     * later, an implementation must still confine its writes to {@code cube} and derive any randomness
     * deterministically from {@code pos} plus construction-time state (see the purity contract above).
     *
     * @param pos the position of the cube being decorated
     * @param cube the already-generated cube to decorate in place
     */
    default void populate(CubePos pos, ICube cube) {
    }

    /**
     * The population radius, in cubes: how many cubes out (in every axis) around a cube must already be
     * generated before that cube may be populated. This is the 3D analogue of vanilla's 2x2 chunk
     * population offset: because a feature originating in one cube can write into neighbouring cubes (and
     * vice versa), a cube's final decorated content depends on its neighbourhood, so population waits for
     * a {@code (2r+1)^3} box of generated cubes around it. The default {@code 1} (a 3x3x3 neighbourhood)
     * is a safe bound for vanilla-scale features; a generator with larger features can raise it.
     *
     * @return the required generated-neighbour radius in cubes (>= 0)
     */
    default int getPopulationRadius() {
        return 1;
    }
}
