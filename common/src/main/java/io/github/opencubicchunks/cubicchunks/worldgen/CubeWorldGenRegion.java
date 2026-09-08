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

import io.github.opencubicchunks.cubicchunks.api.util.Coords;
import io.github.opencubicchunks.cubicchunks.api.util.CubePos;
import io.github.opencubicchunks.cubicchunks.api.world.ICube;
import io.github.opencubicchunks.cubicchunks.api.world.ICubeProviderServer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.attribute.EnvironmentAttributeReader;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.flag.FeatureFlagSet;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.border.WorldBorder;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkSource;
import net.minecraft.world.level.chunk.PalettedContainerFactory;
import net.minecraft.world.level.chunk.ProtoChunk;
import net.minecraft.world.level.chunk.UpgradeData;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.lighting.LevelLightEngine;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.storage.LevelData;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraft.world.ticks.LevelTickAccess;
import net.minecraft.world.ticks.ScheduledTick;
import net.minecraft.world.ticks.TickPriority;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;

/**
 * A {@link WorldGenLevel} view over an already-generated cube neighbourhood, so vanilla feature and
 * structure placement can run against cubes. This is the cube analogue of vanilla's
 * {@code WorldGenRegion}: block/biome/height/block-entity access routes to the cubes in a
 * {@code (2r+1)^3} box around a centre cube (via the provider), and writes are clamped to that box;
 * everything else (registries, dimension, ticks, world border, difficulty, ...) delegates to the real
 * {@link ServerLevel}.
 *
 * <p><b>Scope of this slice.</b> The routing + delegation surface is in place and exercised by a
 * read/write smoke test; it is not yet driven by {@code applyBiomeDecoration}. Two things are
 * deliberately provisional until real feature placement is wired next: {@link #getChunk} delegates to
 * the live level (rather than returning a cube-backed {@link ChunkAccess} view), and generation writes
 * are plain cube block sets (no heightmap/light bookkeeping). Sound/particle/game-event sinks are no-ops,
 * matching how {@code WorldGenRegion} ignores them during generation.
 */
public final class CubeWorldGenRegion implements WorldGenLevel {

    private static final BlockState AIR = Blocks.AIR.defaultBlockState();

    private final ServerLevel level;
    private final ICubeProviderServer provider;
    private final RandomSource random;
    private final BiomeSource biomeSource;
    private final Climate.Sampler climateSampler;
    private final PalettedContainerFactory containerFactory;

    /** Per-column {@link ChunkAccess} views (biome-only ProtoChunks) built on demand for getChunk. */
    private final Map<Long, ChunkAccess> chunkViews = new HashMap<>();

    // Read bounds: the whole neighbourhood, so features can read across cube boundaries.
    private final int minCubeX;
    private final int minCubeY;
    private final int minCubeZ;
    private final int maxCubeX;
    private final int maxCubeY;
    private final int maxCubeZ;

    // Write bounds: only the centre cube. Features that decorate a column write into every cube they
    // overlap, but each cube must keep only its own slice (each is populated separately with the same
    // per-column seed), so writes outside the centre cube are dropped to avoid double placement.
    private final int centreCubeX;
    private final int centreCubeY;
    private final int centreCubeZ;

    /**
     * @param level the backing server level (delegate for everything not routed to cubes)
     * @param provider the cube provider holding the (already generated) neighbourhood
     * @param centre the centre cube of the region
     * @param radius the neighbourhood radius in cubes (the region is a {@code (2*radius+1)^3} box)
     * @param random the random source feature placement should use
     * @param biomeSource the world biome source (for the biome-only chunk views getChunk returns)
     * @param climateSampler the climate sampler (for those biome views)
     * @param containerFactory the palette-container factory (for those biome views)
     */
    public CubeWorldGenRegion(ServerLevel level, ICubeProviderServer provider, CubePos centre, int radius,
                              RandomSource random, BiomeSource biomeSource, Climate.Sampler climateSampler,
                              PalettedContainerFactory containerFactory) {
        this.level = level;
        this.provider = provider;
        this.random = random;
        this.biomeSource = biomeSource;
        this.climateSampler = climateSampler;
        this.containerFactory = containerFactory;
        this.minCubeX = centre.getX() - radius;
        this.minCubeY = centre.getY() - radius;
        this.minCubeZ = centre.getZ() - radius;
        this.maxCubeX = centre.getX() + radius;
        this.maxCubeY = centre.getY() + radius;
        this.maxCubeZ = centre.getZ() + radius;
        this.centreCubeX = centre.getX();
        this.centreCubeY = centre.getY();
        this.centreCubeZ = centre.getZ();
    }

    // ==========================================================================
    // Cube routing
    // ==========================================================================

    private boolean inBounds(int cubeX, int cubeY, int cubeZ) {
        return cubeX >= minCubeX && cubeX <= maxCubeX
                && cubeY >= minCubeY && cubeY <= maxCubeY
                && cubeZ >= minCubeZ && cubeZ <= maxCubeZ;
    }

    private ICube cubeAt(BlockPos pos) {
        int cubeX = Coords.blockToCube(pos.getX());
        int cubeY = Coords.blockToCube(pos.getY());
        int cubeZ = Coords.blockToCube(pos.getZ());
        if (!inBounds(cubeX, cubeY, cubeZ)) {
            return null;
        }
        return provider.getLoadedCube(cubeX, cubeY, cubeZ);
    }

    public BlockState getBlockState(BlockPos pos) {
        ICube cube = cubeAt(pos);
        return cube == null ? AIR : cube.getBlockState(pos);
    }

    public FluidState getFluidState(BlockPos pos) {
        return getBlockState(pos).getFluidState();
    }

    public BlockEntity getBlockEntity(BlockPos pos) {
        ICube cube = cubeAt(pos);
        return cube == null ? null : cube.getBlockEntity(pos);
    }

    public boolean setBlock(BlockPos pos, BlockState state, int flags, int recursionLeft) {
        // Writes are clamped to the centre cube only (see the write-bounds note above).
        if (Coords.blockToCube(pos.getX()) != centreCubeX
                || Coords.blockToCube(pos.getY()) != centreCubeY
                || Coords.blockToCube(pos.getZ()) != centreCubeZ) {
            return false;
        }
        ICube cube = provider.getLoadedCube(centreCubeX, centreCubeY, centreCubeZ);
        if (cube == null) {
            return false;
        }
        cube.setBlockState(pos, state);
        return true;
    }

    public boolean removeBlock(BlockPos pos, boolean isMoving) {
        return setBlock(pos, AIR, 3, 512);
    }

    public boolean destroyBlock(BlockPos pos, boolean dropBlock, Entity entity, int recursionLeft) {
        return setBlock(pos, AIR, 3, recursionLeft);
    }

    public int getHeight(Heightmap.Types type, int x, int z) {
        int top = maxCubeY * 16 + 15;
        int bottom = minCubeY * 16;
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int y = top; y >= bottom; y--) {
            if (!getBlockState(cursor.set(x, y, z)).isAir()) {
                return y + 1; // first empty block above the surface, matching the heightmap convention
            }
        }
        return level.getMinY();
    }

    public BlockPos getHeightmapPos(Heightmap.Types type, BlockPos pos) {
        return new BlockPos(pos.getX(), getHeight(type, pos.getX(), pos.getZ()), pos.getZ());
    }

    public boolean isStateAtPosition(BlockPos pos, Predicate<BlockState> predicate) {
        return predicate.test(getBlockState(pos));
    }

    public boolean isFluidAtPosition(BlockPos pos, Predicate<FluidState> predicate) {
        return predicate.test(getFluidState(pos));
    }

    @SuppressWarnings("unchecked")
    public <T extends BlockEntity> Optional<T> getBlockEntity(BlockPos pos, BlockEntityType<T> type) {
        BlockEntity be = getBlockEntity(pos);
        return be != null && be.getType() == type ? Optional.of((T) be) : Optional.empty();
    }

    public boolean hasChunk(int chunkX, int chunkZ) {
        return chunkX >= minCubeX && chunkX <= maxCubeX && chunkZ >= minCubeZ && chunkZ <= maxCubeZ;
    }

    // ==========================================================================
    // Delegated to the backing level
    // ==========================================================================

    public ServerLevel getLevel() {
        return level;
    }

    public long getSeed() {
        return level.getSeed();
    }

    public RandomSource getRandom() {
        return random;
    }

    public RegistryAccess registryAccess() {
        return level.registryAccess();
    }

    public FeatureFlagSet enabledFeatures() {
        return level.enabledFeatures();
    }

    public DimensionType dimensionType() {
        return level.dimensionType();
    }

    public int getMinY() {
        return level.getMinY();
    }

    public int getHeight() {
        return level.getHeight();
    }

    public int getSeaLevel() {
        return level.getSeaLevel();
    }

    public int getSkyDarken() {
        return level.getSkyDarken();
    }

    public BiomeManager getBiomeManager() {
        return level.getBiomeManager();
    }

    public Holder<Biome> getUncachedNoiseBiome(int quartX, int quartY, int quartZ) {
        return level.getUncachedNoiseBiome(quartX, quartY, quartZ);
    }

    public DifficultyInstance getCurrentDifficultyAt(BlockPos pos) {
        return level.getCurrentDifficultyAt(pos);
    }

    public MinecraftServer getServer() {
        return level.getServer();
    }

    public ChunkSource getChunkSource() {
        return level.getChunkSource();
    }

    public LevelData getLevelData() {
        return level.getLevelData();
    }

    public WorldBorder getWorldBorder() {
        return level.getWorldBorder();
    }

    public LevelLightEngine getLightEngine() {
        return level.getLightEngine();
    }

    public EnvironmentAttributeReader environmentAttributes() {
        return level.environmentAttributes();
    }

    public boolean isClientSide() {
        return false;
    }

    public long nextSubTickCount() {
        return level.nextSubTickCount();
    }

    public LevelTickAccess<net.minecraft.world.level.block.Block> getBlockTicks() {
        return level.getBlockTicks();
    }

    public LevelTickAccess<Fluid> getFluidTicks() {
        return level.getFluidTicks();
    }

    public <T> ScheduledTick<T> createTick(BlockPos pos, T type, int delay, TickPriority priority) {
        return level.createTick(pos, type, delay, priority);
    }

    public <T> ScheduledTick<T> createTick(BlockPos pos, T type, int delay) {
        return level.createTick(pos, type, delay);
    }

    public BlockGetter getChunkForCollisions(int chunkX, int chunkZ) {
        return level.getChunkForCollisions(chunkX, chunkZ);
    }

    public List<VoxelShape> getEntityCollisions(Entity entity, AABB box) {
        return level.getEntityCollisions(entity, box);
    }

    /**
     * Returns a lightweight biome-only {@link ChunkAccess} view for a column, built on demand and cached.
     * Feature placement uses {@code getChunk} only to read the biomes present around the target column
     * (to decide which features to place) and the column's {@code getPos()}; block reads/writes go through
     * this region ({@link #getBlockState}/{@link #setBlock}) instead. So the view is a {@link ProtoChunk}
     * whose section biomes are filled from the world biome source and whose blocks stay empty. Crucially
     * this does not load or generate a real vanilla chunk, which is what keeps cube generation independent
     * of the live chunk pipeline.
     */
    public ChunkAccess getChunk(int chunkX, int chunkZ, ChunkStatus status, boolean requireChunk) {
        return chunkViews.computeIfAbsent(ChunkPos.pack(chunkX, chunkZ), key -> {
            ProtoChunk view = new ProtoChunk(new ChunkPos(chunkX, chunkZ), UpgradeData.EMPTY, level,
                    containerFactory, null);
            view.fillBiomesFromNoise(biomeSource, climateSampler);
            return view;
        });
    }

    // ==========================================================================
    // Ignored during generation (no-op sinks, matching WorldGenRegion)
    // ==========================================================================

    public boolean addFreshEntity(Entity entity) {
        return false;
    }

    public void playSound(Entity entity, BlockPos pos, SoundEvent sound, SoundSource source, float volume,
                          float pitch) {
    }

    public void addParticle(ParticleOptions particle, double x, double y, double z, double dx, double dy, double dz) {
    }

    public void levelEvent(Entity entity, int type, BlockPos pos, int data) {
    }

    public void gameEvent(Holder<GameEvent> event, Vec3 pos, GameEvent.Context context) {
    }

    public List<Entity> getEntities(Entity entity, AABB box, Predicate<? super Entity> predicate) {
        return List.of();
    }

    public <T extends Entity> List<T> getEntities(EntityTypeTest<Entity, T> test, AABB box,
                                                  Predicate<? super T> predicate) {
        return List.of();
    }

    public List<? extends Player> players() {
        return List.of();
    }
}
