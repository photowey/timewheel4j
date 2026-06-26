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

import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Builder for {@link SystemTimer} instances.
 *
 * @author photowey
 * @version 1.0.0
 * @since 2026/06/26
 */
public final class TimerBuilder {

    private long tickMs = 1L;
    private int wheelSize = 512;
    private int workerThreads = Runtime.getRuntime().availableProcessors();
    private int workerQueueCapacity = 100_000;
    private ExecutorService bossExecutor;
    private ExecutorService executorService;
    private String workerName = "timewheel-worker";
    private String bossName = "timewheel-boss";

    private TimerBuilder() {
    }

    /**
     * Create a timer builder with production-oriented defaults.
     *
     * @return a new builder
     */
    public static TimerBuilder builder() {
        return new TimerBuilder();
    }

    /**
     * Configure the base tick duration.
     *
     * @param tick tick value
     * @param unit tick unit
     * @return this builder
     */
    public TimerBuilder tick(long tick, TimeUnit unit) {
        if (unit == null) {
            throw new NullPointerException("unit");
        }
        var resolvedTickMs = unit.toMillis(tick);
        if (resolvedTickMs <= 0) {
            throw new IllegalArgumentException("tick must be at least 1 millisecond");
        }
        this.tickMs = resolvedTickMs;
        return this;
    }

    /**
     * Configure the number of buckets per wheel level.
     *
     * @param wheelSize bucket count
     * @return this builder
     */
    public TimerBuilder wheelSize(int wheelSize) {
        if (wheelSize <= 0) {
            throw new IllegalArgumentException("wheelSize must be positive");
        }
        this.wheelSize = wheelSize;
        return this;
    }

    /**
     * Configure worker threads used by the owned executor.
     *
     * @param workerThreads worker thread count
     * @return this builder
     */
    public TimerBuilder workerThreads(int workerThreads) {
        if (workerThreads <= 0) {
            throw new IllegalArgumentException("workerThreads must be positive");
        }
        this.workerThreads = workerThreads;
        return this;
    }

    /**
     * Configure the owned worker queue capacity.
     *
     * @param workerQueueCapacity maximum queued expired tasks
     * @return this builder
     */
    public TimerBuilder workerQueueCapacity(int workerQueueCapacity) {
        if (workerQueueCapacity <= 0) {
            throw new IllegalArgumentException("workerQueueCapacity must be positive");
        }
        this.workerQueueCapacity = workerQueueCapacity;
        return this;
    }

    /**
     * Use a caller-owned executor for the single boss scheduling loop.
     *
     * @param bossExecutor scheduler executor
     * @return this builder
     */
    public TimerBuilder bossExecutor(ExecutorService bossExecutor) {
        if (bossExecutor == null) {
            throw new NullPointerException("bossExecutor");
        }
        this.bossExecutor = bossExecutor;
        return this;
    }

    /**
     * Use a caller-owned executor for running expired tasks.
     *
     * @param executorService task executor
     * @return this builder
     */
    public TimerBuilder executorService(ExecutorService executorService) {
        if (executorService == null) {
            throw new NullPointerException("executorService");
        }
        this.executorService = executorService;
        return this;
    }

    /**
     * Use a caller-owned executor for running expired tasks.
     *
     * @param workerExecutor task executor
     * @return this builder
     */
    public TimerBuilder workerExecutor(ExecutorService workerExecutor) {
        return this.executorService(workerExecutor);
    }

    /**
     * Configure the owned worker thread name prefix.
     *
     * @param workerName worker thread prefix
     * @return this builder
     */
    public TimerBuilder workerName(String workerName) {
        if (workerName == null || workerName.isBlank()) {
            throw new IllegalArgumentException("workerName must not be blank");
        }
        this.workerName = workerName;
        return this;
    }

    /**
     * Configure the owned boss thread name prefix.
     *
     * @param bossName boss thread prefix
     * @return this builder
     */
    public TimerBuilder bossName(String bossName) {
        if (bossName == null || bossName.isBlank()) {
            throw new IllegalArgumentException("bossName must not be blank");
        }
        this.bossName = bossName;
        return this;
    }

    /**
     * Configure the scheduler thread name prefix.
     *
     * @param schedulerName scheduler thread prefix
     * @return this builder
     */
    public TimerBuilder schedulerName(String schedulerName) {
        if (schedulerName == null || schedulerName.isBlank()) {
            throw new IllegalArgumentException("schedulerName must not be blank");
        }
        this.bossName = schedulerName;
        return this;
    }

    /**
     * Build and start a timer.
     *
     * @return a running timer
     */
    public Timer build() {
        return new SystemTimer(
            this.tickMs,
            this.wheelSize,
            this.bossExecutor,
            this.workerThreads,
            this.workerQueueCapacity,
            this.executorService,
            this.bossName,
            this.workerName);
    }
}
