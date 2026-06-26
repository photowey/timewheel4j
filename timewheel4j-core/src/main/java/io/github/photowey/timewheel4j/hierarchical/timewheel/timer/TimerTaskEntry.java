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

import java.util.concurrent.atomic.AtomicInteger;

/**
 * TimerTaskEntry.
 *
 * @author photowey
 * @version 1.0.0
 * @since 2026/06/26
 */
final class TimerTaskEntry implements Timeout {

    private static final int INIT = 0;
    private static final int CANCELLED = 1;
    private static final int EXPIRED = 2;

    private final Runnable task;
    private final long deadlineMs;
    private final Runnable expiration;
    private final Runnable cancellation;
    private final AtomicInteger state = new AtomicInteger(INIT);

    TimerTaskList list;
    TimerTaskEntry prev;
    TimerTaskEntry next;

    TimerTaskEntry(Runnable task, long deadlineMs) {
        this(task, deadlineMs, () -> {
        });
    }

    TimerTaskEntry(Runnable task, long deadlineMs, Runnable completion) {
        this(task, deadlineMs, completion, completion);
    }

    TimerTaskEntry(Runnable task, long deadlineMs, Runnable expiration, Runnable cancellation) {
        this.task = task;
        this.deadlineMs = deadlineMs;
        this.expiration = expiration;
        this.cancellation = cancellation;
    }

    long deadlineMs() {
        return this.deadlineMs;
    }

    boolean expire() {
        if (this.state.compareAndSet(INIT, EXPIRED)) {
            this.expiration.run();
            return true;
        }
        return false;
    }

    void run() {
        this.task.run();
    }

    @Override
    public boolean cancel() {
        var cancelled = this.state.compareAndSet(INIT, CANCELLED);
        if (cancelled) {
            this.remove();
            this.cancellation.run();
        }
        return cancelled;
    }

    @Override
    public boolean isCancelled() {
        return this.state.get() == CANCELLED;
    }

    @Override
    public boolean isExpired() {
        return this.state.get() == EXPIRED;
    }

    boolean isActive() {
        return this.state.get() == INIT;
    }

    void remove() {
        var current = this.list;
        while (current != null) {
            current.remove(this);
            current = this.list;
        }
    }
}
