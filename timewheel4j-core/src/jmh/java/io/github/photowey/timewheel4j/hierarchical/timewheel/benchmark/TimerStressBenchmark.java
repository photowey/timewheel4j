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
import java.util.concurrent.TimeUnit;

/**
 * TimerStressBenchmark.
 *
 * @author photowey
 * @version 1.0.0
 * @since 2026/06/26
 */
@BenchmarkMode(Mode.SingleShotTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Warmup(iterations = 1, batchSize = 1)
@Measurement(iterations = 3, batchSize = 1)
@Fork(1)
public class TimerStressBenchmark {

    private static final Runnable NOOP = () -> {
    };

    @Benchmark
    public long scheduleMillionReadyWorkload(StressState state) {
        return state.scheduleBatch();
    }

    /**
     * StressState.
     *
     * @author photowey
     * @version 1.0.0
     * @since 2026/06/26
     */
    @State(Scope.Benchmark)
    public static class StressState {

        @Param({"1000000"})
        int taskCount;

        @Param({"1000", "60000", "3600000"})
        long maxDelayMillis;

        @Param({"0", "50", "90"})
        int cancelPercent;

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
            List<Timeout> timeouts = new ArrayList<>(this.taskCount);
            for (int i = 0; i < this.taskCount; i++) {
                Timeout timeout = this.timer.schedule(NOOP, delayFor(i), TimeUnit.MILLISECONDS);
                if (shouldCancel(i)) {
                    timeout.cancel();
                }
                timeouts.add(timeout);
            }

            return timeouts.size();
        }

        private long delayFor(int index) {
            long delay = index % this.maxDelayMillis;
            return Math.max(1L, delay);
        }

        private boolean shouldCancel(int index) {
            return this.cancelPercent > 0 && index % 100 < this.cancelPercent;
        }
    }
}
