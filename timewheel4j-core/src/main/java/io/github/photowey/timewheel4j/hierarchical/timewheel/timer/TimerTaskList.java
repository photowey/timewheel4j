/*
 * Copyright © 2023-present The Timewheel4j Authors. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.github.photowey.timewheel4j.hierarchical.timewheel.timer;

import java.util.concurrent.Delayed;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

/**
 * TimerTaskList.
 *
 * @author photowey
 * @version 1.0.0
 * @since 2026/06/26
 */
final class TimerTaskList implements Delayed {

    private static final long NOT_SET = -1L;

    private final TimerTaskEntry root = new TimerTaskEntry(() -> {
    }, -1L);
    private final AtomicLong expiration = new AtomicLong(NOT_SET);
    private final Clock clock;

    TimerTaskList() {
        this(Clock.SYSTEM);
    }

    TimerTaskList(Clock clock) {
        this.clock = clock;
        this.root.next = this.root;
        this.root.prev = this.root;
    }

    long expiration() {
        return this.expiration.get();
    }

    boolean setExpiration(long expirationMs) {
        return this.expiration.getAndSet(expirationMs) != expirationMs;
    }

    synchronized void add(TimerTaskEntry entry) {
        entry.remove();

        var tail = this.root.prev;
        entry.next = this.root;
        entry.prev = tail;
        entry.list = this;
        tail.next = entry;
        this.root.prev = entry;
    }

    synchronized void remove(TimerTaskEntry entry) {
        if (entry.list != this) {
            return;
        }

        entry.next.prev = entry.prev;
        entry.prev.next = entry.next;
        entry.next = null;
        entry.prev = null;
        entry.list = null;
    }

    void flush(Consumer<TimerTaskEntry> consumer) {
        while (true) {
            TimerTaskEntry entry;
            synchronized (this) {
                entry = this.root.next;
                if (entry == this.root) {
                    this.expiration.set(NOT_SET);
                    return;
                }
                this.remove(entry);
            }

            consumer.accept(entry);
        }
    }

    @Override
    public long getDelay(TimeUnit unit) {
        return unit.convert(this.expiration.get() - this.clock.nowMs(), TimeUnit.MILLISECONDS);
    }

    @Override
    public int compareTo(Delayed other) {
        var that = (TimerTaskList) other;
        return Long.compare(this.expiration.get(), that.expiration.get());
    }
}
