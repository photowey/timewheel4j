/*
 * Copyright © 2023 the original author or authors.
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
package com.photowey.hierarchical.timewheel.core.task;

import java.util.concurrent.Delayed;
import java.util.concurrent.TimeUnit;

import static com.photowey.hierarchical.timewheel.core.fx.Functions.checkNotNull;

/**
 * {@code ScheduledTask}
 *
 * @author photowey
 * @version 1.0.0
 * @since 2023/04/05
 */
public class ScheduledTask implements Runnable, Delayed {

    private final Runnable task;
    private final long expireTime;
    private final long delayMillis;

    public ScheduledTask(Runnable task, long delay, TimeUnit unit) {
        checkNotNull(task, "task");
        checkNotNull(unit, "unit");
        if (delay < 0) {
            throw new IllegalArgumentException("delay : " + delay + " (expected: >= 0)");
        }
        this.task = task;
        this.delayMillis = unit.toMillis(delay);
        this.expireTime = System.currentTimeMillis() + this.delayMillis;
    }

    @Override
    public long getDelay(TimeUnit unit) {
        return unit.convert(expireTime - System.currentTimeMillis(), TimeUnit.MILLISECONDS);
    }

    @Override
    public int compareTo(Delayed o) {
        return Long.compare(expireTime, ((ScheduledTask) o).expireTime);
    }

    @Override
    public void run() {
        this.task.run();
    }

    public long delayTicks(long tickMillis) {
        if (tickMillis <= 0) {
            throw new IllegalArgumentException("tickMillis : " + tickMillis + " (expected: > 0)");
        }
        return ticks(this.delayMillis, tickMillis);
    }

    private static long ticks(long delayMillis, long tickMillis) {
        return Math.max(1L, (delayMillis + tickMillis - 1L) / tickMillis);
    }
}
