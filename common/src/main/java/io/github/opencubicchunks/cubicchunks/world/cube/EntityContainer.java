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
package io.github.opencubicchunks.cubicchunks.world.cube;

import net.minecraft.util.ClassInstanceMultiMap;
import net.minecraft.world.entity.Entity;

import java.util.Collection;
import java.util.Collections;

import javax.annotation.Nonnull;
import javax.annotation.ParametersAreNonnullByDefault;

/**
 * The in-memory set of entities held by a single cube.
 *
 * <p><b>Partial port.</b> The 1.12.2 version also serialized entities to and from NBT
 * ({@code writeToNbt}/{@code readFromNbt} via {@code EntityList.createEntityFromNBT}) and tracked a
 * save dirty-flag. Serialization is world-coupled (an entity is created against a {@code Level} and
 * modern Minecraft reconstructs entities through {@code EntityType}, not the old {@code EntityList}),
 * so it is deferred until the world/storage subsystem is ported. What remains here is the
 * loader-neutral, world-independent container: add, remove, iterate, count.
 *
 * <p>Backed by a {@link ClassInstanceMultiMap} (modern rename of {@code ClassInheritanceMultiMap}),
 * so callers can later query by entity subclass via {@link ClassInstanceMultiMap#find(Class)} once
 * the entity-tick path needs it.
 */
@ParametersAreNonnullByDefault
public class EntityContainer {

    @Nonnull private final ClassInstanceMultiMap<Entity> entities;
    private boolean hasActiveEntities;

    public EntityContainer() {
        this.entities = new ClassInstanceMultiMap<>(Entity.class);
        this.hasActiveEntities = false;
    }

    public void addEntity(Entity entity) {
        this.entities.add(entity);
        this.hasActiveEntities = true;
    }

    public boolean remove(Entity entity) {
        return this.entities.remove(entity);
    }

    public ClassInstanceMultiMap<Entity> getEntitySet() {
        return this.entities;
    }

    public void clear() {
        this.entities.clear();
    }

    public Collection<Entity> getEntities() {
        return Collections.unmodifiableCollection(this.entities);
    }

    public int size() {
        return this.entities.size();
    }

    /** True once any entity has been added since construction (mirrors the 1.12.2 dirty hint). */
    public boolean hasActiveEntities() {
        return this.hasActiveEntities;
    }
}
