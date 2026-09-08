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
package io.github.opencubicchunks.cubicchunks.worldgen;

import io.github.opencubicchunks.cubicchunks.api.util.CubePos;
import io.github.opencubicchunks.cubicchunks.api.world.ICube;
import io.github.opencubicchunks.cubicchunks.api.world.ICubeProviderServer;
import io.github.opencubicchunks.cubicchunks.api.world.ICubicWorldServer;
import io.github.opencubicchunks.cubicchunks.api.worldgen.CubePrimer;
import io.github.opencubicchunks.cubicchunks.api.worldgen.ICubeGenerator;
import net.minecraft.core.Holder;
import net.minecraft.core.RegistryAccess;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.NoiseColumn;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.chunk.PalettedContainer;
import net.minecraft.world.level.chunk.PalettedContainerFactory;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.RandomState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A base-terrain {@link ICubeGenerator} that carries the world's real vanilla terrain into cubes by
 * sampling the world's {@link ChunkGenerator} one vertical column at a time. This gives cube generation
 * vanilla parity (the same surface, stone, deepslate, air a normal chunk would have) for the part of a
 * cube that lies within the generator's own height window.
 *
 * <p><b>How it stays on the purity contract.</b> Everything the generator needs is captured once at
 * construction: the {@link ChunkGenerator}, the world's {@link RandomState} (seed-derived noise, itself
 * immutable), and the {@link ServerLevel} for the sampling window. {@link #generate} then calls
 * {@link ChunkGenerator#getBaseColumn} which is a pure noise sample: it reads no live blocks and mutates
 * no shared state, so it is safe to run for distinct cubes on many threads. This is the exact primitive
 * vanilla itself uses off the main thread (structure placement, {@code /locate}), and C2ME parallelizes,
 * so obeying the contract here is what keeps C2ME support automatic. See {@link ICubeGenerator}.
 *
 * <p><b>Base terrain</b> is a pure noise sample. <b>Biomes</b> are sampled per cube. <b>Population</b>
 * (features/structures) runs vanilla's {@code applyBiomeDecoration} against a {@link CubeWorldGenRegion}
 * over the cube's neighbourhood, with writes clamped to the target cube (see {@link #populate}).
 */
public final class NoiseCubeGenerator implements ICubeGenerator {

    private static final Logger LOGGER = LoggerFactory.getLogger("CubicChunks/NoiseCubeGenerator");

    private final ChunkGenerator generator;
    private final RandomState randomState;
    private final ServerLevel serverLevel;
    private final BiomeSource biomeSource;
    private final Climate.Sampler climateSampler;
    private final PalettedContainerFactory containerFactory;
    private final long worldSeed;

    public NoiseCubeGenerator(ChunkGenerator generator, RandomState randomState, ServerLevel serverLevel,
                              RegistryAccess registryAccess, long worldSeed) {
        this.generator = generator;
        this.randomState = randomState;
        this.serverLevel = serverLevel;
        // Biome inputs captured once: the source and the seed-derived climate sampler are both immutable.
        this.biomeSource = generator.getBiomeSource();
        this.climateSampler = randomState.sampler();
        this.containerFactory = PalettedContainerFactory.create(registryAccess);
        this.worldSeed = worldSeed;
    }

    @Override
    public void generate(CubePos pos, CubePrimer primer) {
        int baseX = pos.getX() * 16;
        int baseY = pos.getY() * 16;
        int baseZ = pos.getZ() * 16;

        int minY = serverLevel.getMinY();
        int maxYExclusive = minY + serverLevel.getHeight();
        // Whole cube below or above the generator's window: nothing to sample, leave it air.
        if (baseY + 15 < minY || baseY >= maxYExclusive) {
            return;
        }

        for (int localX = 0; localX < 16; localX++) {
            for (int localZ = 0; localZ < 16; localZ++) {
                // One noise sample per (x, z) yields the whole vertical column; read the 16 rows we need.
                NoiseColumn column = generator.getBaseColumn(baseX + localX, baseZ + localZ, serverLevel, randomState);
                for (int localY = 0; localY < 16; localY++) {
                    int worldY = baseY + localY;
                    if (worldY < minY || worldY >= maxYExclusive) {
                        continue; // outside the sampled window; NoiseColumn would be out of bounds
                    }
                    BlockState state = column.getBlock(worldY);
                    if (!state.isAir()) {
                        primer.setBlockState(localX, localY, localZ, state);
                    }
                }
            }
        }
    }

    /**
     * Samples the world's biome source into a fresh 4x4x4 biome container for this cube, at the same
     * quart resolution vanilla stores biomes. A cube spans 4 biome cells per axis; cell {@code (qx,qy,qz)}
     * maps to the global quart position {@code (cubeX*4 + qx, cubeY*4 + qy, cubeZ*4 + qz)}. Pure noise
     * sample, safe off the main thread.
     */
    @Override
    public PalettedContainer<Holder<Biome>> generateBiomes(CubePos pos) {
        PalettedContainer<Holder<Biome>> container = containerFactory.createForBiomes();
        int quartBaseX = pos.getX() * 4;
        int quartBaseY = pos.getY() * 4;
        int quartBaseZ = pos.getZ() * 4;
        for (int qx = 0; qx < 4; qx++) {
            for (int qy = 0; qy < 4; qy++) {
                for (int qz = 0; qz < 4; qz++) {
                    Holder<Biome> biome = biomeSource.getNoiseBiome(
                            quartBaseX + qx, quartBaseY + qy, quartBaseZ + qz, climateSampler);
                    container.getAndSet(qx, qy, qz, biome);
                }
            }
        }
        return container;
    }

    /**
     * Runs vanilla feature/structure decoration for one cube. Builds a {@link CubeWorldGenRegion} over the
     * cube's neighbourhood (already generated by the {@code CubePopulator}) and calls the world's
     * {@link ChunkGenerator#applyBiomeDecoration} against it. Vanilla decorates a whole column at once, but
     * the region clamps writes to the target cube, so this cube keeps only the features overlapping it;
     * each cube keeps its own slice as it is populated (the 3D population offset). The decoration seed is
     * per-column, so re-running for each cube is deterministic and consistent.
     *
     * <p>Guarded: a feature that misbehaves against the cube view logs and is skipped rather than aborting
     * generation.
     */
    @Override
    public void populate(CubePos pos, ICube cube) {
        ICubeProviderServer provider = ((ICubicWorldServer) serverLevel).getCubeCache();
        int radius = getPopulationRadius();
        CubeWorldGenRegion region = new CubeWorldGenRegion(serverLevel, provider, pos, radius,
                RandomSource.create(worldSeed), biomeSource, climateSampler, containerFactory);
        // The column view for the target cube's column; applyBiomeDecoration also pulls neighbour columns
        // through region.getChunk to determine which biomes' features to place.
        ChunkAccess columnView = region.getChunk(pos.getX(), pos.getZ(), ChunkStatus.FULL, true);
        try {
            generator.applyBiomeDecoration(region, columnView, serverLevel.structureManager());
        } catch (Exception e) {
            LOGGER.error("Feature placement failed for cube {}", pos, e);
        }
    }
}
