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

import java.util.concurrent.TimeUnit;

/**
 * Schedules delayed tasks on a hierarchical timing wheel.
 *
 * @author photowey
 * @version 1.0.0
 * @since 2026/06/26
 */
public interface Timer extends AutoCloseable {

    /**
     * Schedule a task for delayed execution.
     *
     * @param task  the task to execute
     * @param delay the delay value
     * @param unit  the delay unit
     * @return a timeout handle that can cancel or inspect the task state
     */
    Timeout schedule(Runnable task, long delay, TimeUnit unit);

    /**
     * Returns the number of active, not-yet-expired timeouts.
     *
     * @return pending timeout count
     */
    long size();

    /**
     * Returns a point-in-time metrics snapshot.
     *
     * @return immutable timer metrics
     */
    TimerMetrics metrics();

    /**
     * Stops the timer and releases owned worker resources.
     */
    void shutdown();

    /**
     * Stops the timer.
     */
    @Override
    default void close() {
        this.shutdown();
    }
}
