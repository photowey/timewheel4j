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
package io.github.photowey.timewheel4j.hierarchical.timewheel.benchmark;

import io.github.photowey.timewheel4j.hierarchical.timewheel.timer.Timeout;
import io.github.photowey.timewheel4j.hierarchical.timewheel.timer.Timer;
import io.github.photowey.timewheel4j.hierarchical.timewheel.timer.TimerBuilder;
import io.netty.util.HashedWheelTimer;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.annotations.Warmup;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Delayed;
import java.util.concurrent.DelayQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * TimerBenchmark.
 *
 * @author photowey
 * @version 1.0.0
 * @since 2026/06/26
 */
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
@Warmup(iterations = 2, time = 1)
@Measurement(iterations = 3, time = 1)
@Fork(1)
public class TimerBenchmark {

    private static final Runnable NOOP = () -> {
    };

    @Benchmark
    public long scheduleOnTimewheel(TimerState state) {
        return state.scheduleBatch();
    }

    @Benchmark
    public long scheduleOnScheduledExecutor(ExecutorState state) {
        return state.scheduleBatch();
    }

    @Benchmark
    public long scheduleOnNettyHashedWheelTimer(NettyState state) {
        return state.scheduleBatch();
    }

    @Benchmark
    public long scheduleOnDelayQueue(DelayQueueState state) {
        return state.scheduleBatch();
    }

    /**
     * TimerState.
     *
     * @author photowey
     * @version 1.0.0
     * @since 2026/06/26
     */
    @State(Scope.Benchmark)
    public static class TimerState extends AbstractBatchState {

        private Timer timer;

        @Setup(Level.Iteration)
        public void setup() {
            this.timer = TimerBuilder.builder()
                .tick(1, TimeUnit.MILLISECONDS)
                .wheelSize(512)
                .workerThreads(1)
                .build();
        }

        @TearDown(Level.Iteration)
        public void tearDown() {
            this.timer.shutdown();
        }

        long scheduleBatch() {
            List<Timeout> timeouts = new ArrayList<>(this.operationsPerInvocation());
            for (int i = 0; i < this.taskCount; i++) {
                Timeout timeout = this.timer.schedule(NOOP, delayFor(i), TimeUnit.MILLISECONDS);
                if (shouldCancel(i)) {
                    timeout.cancel();
                }
                timeouts.add(timeout);
            }

            return timeouts.size();
        }
    }

    /**
     * NettyState.
     *
     * @author photowey
     * @version 1.0.0
     * @since 2026/06/26
     */
    @State(Scope.Benchmark)
    public static class NettyState extends AbstractBatchState {

        private HashedWheelTimer timer;

        @Setup(Level.Iteration)
        public void setup() {
            this.timer = new HashedWheelTimer(1, TimeUnit.MILLISECONDS, 512);
        }

        @TearDown(Level.Iteration)
        public void tearDown() {
            this.timer.stop();
        }

        long scheduleBatch() {
            for (int i = 0; i < this.taskCount; i++) {
                io.netty.util.Timeout timeout = this.timer.newTimeout(
                    ignored -> NOOP.run(),
                    delayFor(i),
                    TimeUnit.MILLISECONDS);
                if (shouldCancel(i)) {
                    timeout.cancel();
                }
            }

            return this.taskCount;
        }
    }

    /**
     * ExecutorState.
     *
     * @author photowey
     * @version 1.0.0
     * @since 2026/06/26
     */
    @State(Scope.Benchmark)
    public static class ExecutorState extends AbstractBatchState {

        private ScheduledExecutorService executor;

        @Setup(Level.Iteration)
        public void setup() {
            this.executor = Executors.newSingleThreadScheduledExecutor();
        }

        @TearDown(Level.Iteration)
        public void tearDown() {
            this.executor.shutdownNow();
        }

        long scheduleBatch() {
            for (int i = 0; i < this.taskCount; i++) {
                var future = this.executor.schedule(NOOP, delayFor(i), TimeUnit.MILLISECONDS);
                if (shouldCancel(i)) {
                    future.cancel(false);
                }
            }

            return this.taskCount;
        }
    }

    /**
     * DelayQueueState.
     *
     * @author photowey
     * @version 1.0.0
     * @since 2026/06/26
     */
    @State(Scope.Benchmark)
    public static class DelayQueueState extends AbstractBatchState {

        private DelayQueue<DelayedTask> queue;
        private AtomicLong sequence;

        @Setup(Level.Iteration)
        public void setup() {
            this.queue = new DelayQueue<>();
            this.sequence = new AtomicLong();
        }

        @TearDown(Level.Iteration)
        public void tearDown() {
            this.queue.clear();
        }

        long scheduleBatch() {
            for (int i = 0; i < this.taskCount; i++) {
                DelayedTask task = new DelayedTask(System.nanoTime()
                    + TimeUnit.MILLISECONDS.toNanos(delayFor(i)), this.sequence.incrementAndGet());
                if (!shouldCancel(i)) {
                    this.queue.offer(task);
                }
            }

            return this.queue.size();
        }
    }

    /**
     * AbstractBatchState.
     *
     * @author photowey
     * @version 1.0.0
     * @since 2026/06/26
     */
    @State(Scope.Benchmark)
    public abstract static class AbstractBatchState {

        @Param({"1000", "10000"})
        int taskCount;

        @Param({"1", "100", "60000"})
        long maxDelayMillis;

        @Param({"0", "50"})
        int cancelPercent;

        long delayFor(int index) {
            long delay = index % this.maxDelayMillis;
            return Math.max(1, delay);
        }

        boolean shouldCancel(int index) {
            return this.cancelPercent > 0 && index % 100 < this.cancelPercent;
        }

        int operationsPerInvocation() {
            return this.taskCount;
        }
    }

    /**
     * DelayedTask.
     *
     * @author photowey
     * @version 1.0.0
     * @since 2026/06/26
     */
    private static final class DelayedTask implements Delayed {

        private final long deadlineNanos;
        private final long sequence;

        private DelayedTask(long deadlineNanos, long sequence) {
            this.deadlineNanos = deadlineNanos;
            this.sequence = sequence;
        }

        @Override
        public long getDelay(TimeUnit unit) {
            return unit.convert(this.deadlineNanos - System.nanoTime(), TimeUnit.NANOSECONDS);
        }

        @Override
        public int compareTo(Delayed other) {
            DelayedTask that = (DelayedTask) other;
            int deadline = Long.compare(this.deadlineNanos, that.deadlineNanos);
            if (deadline != 0) {
                return deadline;
            }

            return Long.compare(this.sequence, that.sequence);
        }
    }
}
