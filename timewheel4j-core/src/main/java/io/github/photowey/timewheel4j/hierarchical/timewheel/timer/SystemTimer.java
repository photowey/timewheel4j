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

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Kafka-style hierarchical timing wheel backed by a bucket-level delay queue.
 *
 * @author photowey
 * @version 1.0.0
 * @since 2026/06/26
 */
public final class SystemTimer implements Timer {

    private final BucketDelayQueue delayQueue = new BucketDelayQueue();
    private final TimingWheel timingWheel;
    private final ExecutorService boss;
    private final ExecutorService workers;
    private final Future<?> bossLoop;
    private final AtomicBoolean running = new AtomicBoolean(true);
    private final AtomicLong size = new AtomicLong();
    private final AtomicLong scheduledTimeouts = new AtomicLong();
    private final AtomicLong expiredTimeouts = new AtomicLong();
    private final AtomicLong cancelledTimeouts = new AtomicLong();
    private final AtomicLong rejectedTimeouts = new AtomicLong();
    private final AtomicLong bucketOffers = new AtomicLong();
    private final AtomicLong bucketExpirations = new AtomicLong();
    private final AtomicLong maxBucketDelayMs = new AtomicLong();
    private final Clock clock;
    private final boolean ownsBoss;
    private final boolean ownsWorkers;

    SystemTimer(
        long tickMs,
        int wheelSize,
        ExecutorService bossExecutor,
        int workerThreads,
        int workerQueueCapacity,
        ExecutorService executorService,
        String bossName,
        String workerName) {
        this(
            tickMs,
            wheelSize,
            bossExecutor,
            workerThreads,
            workerQueueCapacity,
            executorService,
            bossName,
            workerName,
            Clock.SYSTEM);
    }

    SystemTimer(
        long tickMs,
        int wheelSize,
        ExecutorService bossExecutor,
        int workerThreads,
        int workerQueueCapacity,
        ExecutorService executorService,
        String bossName,
        String workerName,
        Clock clock) {
        this.clock = clock;
        var startMs = clock.nowMs();
        this.timingWheel = new TimingWheel(tickMs, wheelSize, startMs, this.delayQueue, clock, this::recordBucketOffer);
        if (bossExecutor == null) {
            this.boss = Executors.newSingleThreadExecutor(new TimerThreadFactory(bossName));
            this.ownsBoss = true;
        } else {
            this.boss = bossExecutor;
            this.ownsBoss = false;
        }
        if (executorService == null) {
            this.workers = new ThreadPoolExecutor(
                workerThreads,
                workerThreads,
                0L,
                TimeUnit.MILLISECONDS,
                workerQueue(workerQueueCapacity),
                new TimerThreadFactory(workerName),
                new ThreadPoolExecutor.AbortPolicy());
            this.ownsWorkers = true;
        } else {
            this.workers = executorService;
            this.ownsWorkers = false;
        }
        this.bossLoop = this.boss.submit(this::advance);
    }

    /**
     * Schedule a task for delayed execution.
     *
     * @param task  the task to execute
     * @param delay the delay value
     * @param unit  the delay unit
     * @return a timeout handle
     */
    @Override
    public Timeout schedule(Runnable task, long delay, TimeUnit unit) {
        if (task == null) {
            throw new NullPointerException("task");
        }
        if (unit == null) {
            throw new NullPointerException("unit");
        }
        if (delay < 0) {
            throw new IllegalArgumentException("delay must not be negative");
        }
        if (!this.running.get()) {
            throw new IllegalStateException("timer has been shut down");
        }

        var deadlineMs = this.clock.nowMs() + unit.toMillis(delay);
        this.size.incrementAndGet();
        this.scheduledTimeouts.incrementAndGet();
        var entry = new TimerTaskEntry(task, deadlineMs, this::recordExpired, this::recordCancelled);
        this.addOrRun(entry);
        return entry;
    }

    /**
     * Returns the number of active, not-yet-expired timeouts.
     *
     * @return pending timeout count
     */
    @Override
    public long size() {
        return this.size.get();
    }

    /**
     * Returns a point-in-time metrics snapshot.
     *
     * @return immutable timer metrics
     */
    @Override
    public TimerMetrics metrics() {
        return new TimerMetrics(
            this.scheduledTimeouts.get(),
            this.expiredTimeouts.get(),
            this.cancelledTimeouts.get(),
            this.rejectedTimeouts.get(),
            this.size.get(),
            this.bucketOffers.get(),
            this.bucketExpirations.get(),
            this.maxBucketDelayMs.get());
    }

    /**
     * Stops the timer and shuts down the owned worker executor.
     */
    @Override
    public void shutdown() {
        if (this.running.compareAndSet(true, false)) {
            this.bossLoop.cancel(true);
            if (this.ownsBoss) {
                this.boss.shutdownNow();
            }
            if (this.ownsWorkers) {
                this.workers.shutdown();
            }
        }
    }

    private void advance() {
        while (this.running.get()) {
            try {
                this.delayQueue.poll(100, TimeUnit.MILLISECONDS).ifPresent(this::expire);
            } catch (InterruptedException e) {
                if (!this.running.get()) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        }
    }

    private void expire(TimerTaskList bucket) {
        this.timingWheel.advanceClock(bucket.expiration());
        this.bucketExpirations.incrementAndGet();
        bucket.flush((entry) -> {
            try {
                this.addOrRun(entry);
            } catch (RejectedExecutionException ignored) {
                // Rejected worker submissions must not stop the single owner boss loop.
            }
        });
    }

    private void addOrRun(TimerTaskEntry entry) {
        if (!entry.isActive()) {
            return;
        }

        if (!this.timingWheel.add(entry)) {
            this.submit(entry);
        }
    }

    private void submit(TimerTaskEntry entry) {
        if (!entry.expire()) {
            return;
        }
        if (!this.running.get()) {
            this.rejectedTimeouts.incrementAndGet();
            return;
        }

        try {
            this.workers.execute(entry::run);
        } catch (RejectedExecutionException e) {
            this.rejectedTimeouts.incrementAndGet();
            throw e;
        }
    }

    private void recordExpired() {
        this.size.decrementAndGet();
        this.expiredTimeouts.incrementAndGet();
    }

    private void recordCancelled() {
        this.size.decrementAndGet();
        this.cancelledTimeouts.incrementAndGet();
    }

    private void recordBucketOffer(long bucketExpirationMs) {
        this.bucketOffers.incrementAndGet();
        this.maxBucketDelayMs.accumulateAndGet(
            Math.max(0L, bucketExpirationMs - this.clock.nowMs()),
            Math::max);
    }

    private static BlockingQueue<Runnable> workerQueue(int capacity) {
        if (capacity == Integer.MAX_VALUE) {
            return new LinkedBlockingQueue<>();
        }
        return new LinkedBlockingQueue<>(capacity);
    }
}
