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
package io.github.opencubicchunks.cubicchunks.api.world.storage;

import net.minecraft.resources.Identifier;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Base class for a CubicChunks storage-format provider: a factory producing an {@link ICubicStorage}
 * backend for a world.
 *
 * <p><b>Ported registry.</b> On 1.12.2 this was a Forge {@code IForgeRegistryEntry} kept in a Forge
 * {@code IForgeRegistry}. That is replaced here by a simple loader-neutral static registry
 * ({@link #register}/{@link #get}/{@link #getAll}). The Forge {@code MissingFactory}/dummy-format
 * mechanism (used only for Forge registry network sync) is dropped; storage formats are never used
 * on the client.
 */
public abstract class StorageFormatProviderBase {

    public static final Identifier DEFAULT = Identifier.fromNamespaceAndPath("cubicchunks", "anvil3d");

    private static final Map<Identifier, StorageFormatProviderBase> REGISTRY = new ConcurrentHashMap<>();

    /** Registers a storage-format provider under its {@link #getRegistryName() registry name}. */
    public static void register(StorageFormatProviderBase provider) {
        Identifier name = Objects.requireNonNull(provider.getRegistryName(), "provider registry name");
        REGISTRY.put(name, provider);
    }

    /** Returns the registered provider with the given name, or {@code null} if none is registered. */
    @Nullable
    public static StorageFormatProviderBase get(Identifier name) {
        return REGISTRY.get(name);
    }

    /** Returns all registered providers. */
    public static Collection<StorageFormatProviderBase> getAll() {
        return REGISTRY.values();
    }

    public static Identifier defaultStorageFormatProviderName(String fallback) {
        if (!fallback.isEmpty()) {
            return Identifier.parse(fallback);
        }
        Identifier[] providersThatCanBeDefault = REGISTRY.values().stream()
                .filter(StorageFormatProviderBase::canBeDefault)
                .map(StorageFormatProviderBase::getRegistryName)
                .toArray(Identifier[]::new);
        return providersThatCanBeDefault.length == 1 ? providersThatCanBeDefault[0] : DEFAULT;
    }

    public Identifier registryName;
    public String unlocalizedName;

    public Identifier getRegistryName() {
        return this.registryName;
    }

    public StorageFormatProviderBase setRegistryName(Identifier registryNameIn) {
        this.registryName = registryNameIn;
        return this;
    }

    public String getUnlocalizedName() {
        return this.unlocalizedName;
    }

    public StorageFormatProviderBase setUnlocalizedName(String nameIn) {
        this.unlocalizedName = nameIn;
        return this;
    }

    public abstract ICubicStorage provideStorage(Level world, Path path) throws IOException;

    public boolean canBeDefault() {
        return false;
    }
}
