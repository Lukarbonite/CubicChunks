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
package io.github.opencubicchunks.cubicchunks.api.event;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * A minimal, loader-neutral event channel.
 *
 * <p>Replaces the 1.12.2 Forge event bus (on which the cube load/unload/watch/data events were
 * fired). Listeners register a {@link Consumer}; the cube management code invokes the channel when
 * the event occurs. Registration is thread-safe so listeners may be added during setup on any thread.
 *
 * @param <T> the event payload type
 */
public final class CubicEvent<T> {

    private final List<Consumer<T>> listeners = new CopyOnWriteArrayList<>();

    /** Registers a listener to be notified when this event is invoked. */
    public void register(Consumer<T> listener) {
        listeners.add(listener);
    }

    /** Notifies all registered listeners, in registration order. */
    public void invoke(T event) {
        for (Consumer<T> listener : listeners) {
            listener.accept(event);
        }
    }

    /** True if at least one listener is registered (lets callers skip building an event object). */
    public boolean hasListeners() {
        return !listeners.isEmpty();
    }
}
