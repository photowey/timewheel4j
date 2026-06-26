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

import java.util.function.LongConsumer;

/**
 * TimingWheel.
 *
 * @author photowey
 * @version 1.0.0
 * @since 2026/06/26
 */
final class TimingWheel {

    private final long tickMs;
    private final int wheelSize;
    private final long interval;
    private final TimerTaskList[] buckets;
    private final BucketDelayQueue delayQueue;
    private final Clock clock;
    private final LongConsumer bucketOfferListener;

    private volatile long currentTime;
    private volatile TimingWheel overflowWheel;

    TimingWheel(long tickMs, int wheelSize, long startMs, BucketDelayQueue delayQueue) {
        this(tickMs, wheelSize, startMs, delayQueue, Clock.SYSTEM);
    }

    TimingWheel(long tickMs, int wheelSize, long startMs, BucketDelayQueue delayQueue, Clock clock) {
        this(tickMs, wheelSize, startMs, delayQueue, clock, expiration -> {
        });
    }

    TimingWheel(
        long tickMs,
        int wheelSize,
        long startMs,
        BucketDelayQueue delayQueue,
        Clock clock,
        LongConsumer bucketOfferListener) {
        this.tickMs = tickMs;
        this.wheelSize = wheelSize;
        this.interval = Math.multiplyExact(tickMs, wheelSize);
        this.currentTime = truncate(startMs, tickMs);
        this.delayQueue = delayQueue;
        this.clock = clock;
        this.bucketOfferListener = bucketOfferListener;
        this.buckets = new TimerTaskList[wheelSize];
        for (var i = 0; i < wheelSize; i++) {
            this.buckets[i] = new TimerTaskList(clock);
        }
    }

    boolean add(TimerTaskEntry entry) {
        if (!entry.isActive()) {
            return true;
        }

        long deadline = entry.deadlineMs();
        if (deadline < this.currentTime + this.tickMs) {
            return false;
        }

        if (deadline < this.currentTime + this.interval) {
            var bucketExpiration = truncate(deadline, this.tickMs);
            var bucket = this.buckets[(int) ((bucketExpiration / this.tickMs) % this.wheelSize)];
            bucket.add(entry);
            if (bucket.setExpiration(bucketExpiration)) {
                this.delayQueue.offer(bucket);
                this.bucketOfferListener.accept(bucketExpiration);
            }
            return true;
        }

        return this.overflowWheel().add(entry);
    }

    void advanceClock(long timeMs) {
        if (timeMs >= this.currentTime + this.tickMs) {
            this.currentTime = truncate(timeMs, this.tickMs);
            var overflow = this.overflowWheel;
            if (overflow != null) {
                overflow.advanceClock(this.currentTime);
            }
        }
    }

    private TimingWheel overflowWheel() {
        var wheel = this.overflowWheel;
        if (wheel == null) {
            synchronized (this) {
                wheel = this.overflowWheel;
                if (wheel == null) {
                    wheel = new TimingWheel(
                        this.interval,
                        this.wheelSize,
                        this.currentTime,
                        this.delayQueue,
                        this.clock,
                        this.bucketOfferListener);
                    this.overflowWheel = wheel;
                }
            }
        }
        return wheel;
    }

    private static long truncate(long value, long unit) {
        return value - value % unit;
    }
}
