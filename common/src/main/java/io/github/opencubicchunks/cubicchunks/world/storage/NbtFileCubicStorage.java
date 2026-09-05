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
package io.github.opencubicchunks.cubicchunks.world.storage;

import io.github.opencubicchunks.cubicchunks.api.util.CubePos;
import io.github.opencubicchunks.cubicchunks.api.world.storage.ICubicStorage;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.world.level.ChunkPos;

import javax.annotation.Nullable;
import javax.annotation.ParametersAreNonnullByDefault;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Consumer;
import java.util.stream.Stream;

/**
 * A simple {@link ICubicStorage} that keeps one gzipped-NBT file per column and per cube under a base
 * directory ({@code columns/x.z.nbt}, {@code cubes/x.y.z.nbt}).
 *
 * <p><b>Partial port.</b> The 1.12.2 storage used a region-file format (many entries packed per file)
 * for disk efficiency. This flat, file-per-entry backend is deliberately simple: it is correct,
 * dependency-free (just {@link NbtIo}), and easy to verify, which suits the current bring-up. A
 * region-packed or {@code IOWorker}-backed implementation can replace it later behind the same
 * interface. Writes are synchronous and immediate, so {@link #flush()} is a no-op; per the interface
 * contract, no cross-file atomicity is guaranteed.
 */
@ParametersAreNonnullByDefault
public class NbtFileCubicStorage implements ICubicStorage {

    private static final String EXT = ".nbt";

    private final Path columnsDir;
    private final Path cubesDir;

    public NbtFileCubicStorage(Path baseDir) throws IOException {
        this.columnsDir = baseDir.resolve("columns");
        this.cubesDir = baseDir.resolve("cubes");
        Files.createDirectories(this.columnsDir);
        Files.createDirectories(this.cubesDir);
    }

    private Path columnFile(ChunkPos pos) {
        return columnsDir.resolve(pos.x() + "." + pos.z() + EXT);
    }

    private Path cubeFile(CubePos pos) {
        return cubesDir.resolve(pos.getX() + "." + pos.getY() + "." + pos.getZ() + EXT);
    }

    @Override
    public boolean columnExists(ChunkPos pos) {
        return Files.exists(columnFile(pos));
    }

    @Override
    public boolean cubeExists(CubePos pos) {
        return Files.exists(cubeFile(pos));
    }

    @Nullable
    @Override
    public CompoundTag readColumn(ChunkPos pos) throws IOException {
        return read(columnFile(pos));
    }

    @Nullable
    @Override
    public CompoundTag readCube(CubePos pos) throws IOException {
        return read(cubeFile(pos));
    }

    @Nullable
    private CompoundTag read(Path file) throws IOException {
        if (!Files.exists(file)) {
            return null;
        }
        return NbtIo.readCompressed(file, NbtAccounter.unlimitedHeap());
    }

    @Override
    public void writeColumn(ChunkPos pos, CompoundTag nbt) throws IOException {
        NbtIo.writeCompressed(nbt, columnFile(pos));
    }

    @Override
    public void writeCube(CubePos pos, CompoundTag nbt) throws IOException {
        NbtIo.writeCompressed(nbt, cubeFile(pos));
    }

    @Override
    public void forEachColumn(Consumer<ChunkPos> callback) throws IOException {
        forEachFile(columnsDir, name -> {
            int[] c = parseCoords(name, 2);
            if (c != null) {
                callback.accept(new ChunkPos(c[0], c[1]));
            }
        });
    }

    @Override
    public void forEachCube(Consumer<CubePos> callback) throws IOException {
        forEachFile(cubesDir, name -> {
            int[] c = parseCoords(name, 3);
            if (c != null) {
                callback.accept(new CubePos(c[0], c[1], c[2]));
            }
        });
    }

    private void forEachFile(Path dir, Consumer<String> nameCallback) throws IOException {
        try (Stream<Path> files = Files.list(dir)) {
            files.map(p -> p.getFileName().toString())
                    .filter(name -> name.endsWith(EXT))
                    .forEach(nameCallback);
        }
    }

    /**
     * Parses {@code count} dot-separated signed integers from a {@code x.z.nbt} / {@code x.y.z.nbt}
     * file name. Returns {@code null} for names that do not match (so stray files are skipped).
     */
    @Nullable
    private int[] parseCoords(String fileName, int count) {
        String base = fileName.substring(0, fileName.length() - EXT.length());
        String[] parts = base.split("\\.");
        if (parts.length != count) {
            return null;
        }
        int[] out = new int[count];
        try {
            for (int i = 0; i < count; i++) {
                out[i] = Integer.parseInt(parts[i]);
            }
        } catch (NumberFormatException e) {
            return null;
        }
        return out;
    }

    @Override
    public void flush() {
        // Writes are synchronous and immediate; nothing is buffered.
    }

    @Override
    public void close() {
        // No open resources to release for the flat-file backend.
    }
}
