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

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * TimingWheelTest.
 *
 * @author photowey
 * @version 1.0.0
 * @since 2026/06/26
 */
class TimingWheelTest {

    @Test
    void givenDueEntryWhenAddedThenWheelRequestsImmediateExecution() {
        // Given
        var delayQueue = new BucketDelayQueue();
        var wheel = new TimingWheel(10L, 8, 100L, delayQueue);
        var entry = new TimerTaskEntry(() -> {
        }, 109L);

        // When
        var added = wheel.add(entry);

        // Then
        assertFalse(added);
        assertEquals(0, delayQueue.size());
    }

    @Test
    void givenEntryInsideCurrentIntervalWhenAddedThenBucketIsOfferedOnce() {
        // Given
        var delayQueue = new BucketDelayQueue();
        var wheel = new TimingWheel(10L, 8, 100L, delayQueue);
        var first = new TimerTaskEntry(() -> {
        }, 130L);
        var second = new TimerTaskEntry(() -> {
        }, 139L);

        // When
        var firstAdded = wheel.add(first);
        var secondAdded = wheel.add(second);

        // Then
        assertTrue(firstAdded);
        assertTrue(secondAdded);
        assertEquals(1, delayQueue.size());
    }

    @Test
    void givenEntryOutsideCurrentIntervalWhenAddedThenOverflowWheelReceivesIt() {
        // Given
        var delayQueue = new BucketDelayQueue();
        var wheel = new TimingWheel(10L, 8, 100L, delayQueue);
        var first = new TimerTaskEntry(() -> {
        }, 260L);
        var second = new TimerTaskEntry(() -> {
        }, 270L);

        // When
        var firstAdded = wheel.add(first);
        var secondAdded = wheel.add(second);

        // Then
        assertTrue(firstAdded);
        assertTrue(secondAdded);
        assertEquals(1, delayQueue.size());
    }

    @Test
    void givenCancelledEntryWhenAddedThenWheelIgnoresItAsHandled() {
        // Given
        var delayQueue = new BucketDelayQueue();
        var wheel = new TimingWheel(10L, 8, 100L, delayQueue);
        var entry = new TimerTaskEntry(() -> {
        }, 130L);
        entry.cancel();

        // When
        var added = wheel.add(entry);

        // Then
        assertTrue(added);
        assertEquals(0, delayQueue.size());
    }

    @Test
    void givenClockBeforeNextTickWhenAdvancedThenNoBucketIsMoved() {
        // Given
        var delayQueue = new BucketDelayQueue();
        var wheel = new TimingWheel(10L, 8, 100L, delayQueue);

        // When
        wheel.advanceClock(109L);

        // Then
        assertEquals(0, delayQueue.size());
    }

    @Test
    void givenOverflowWheelWhenClockAdvancesThenOverflowClockAdvancesToo() {
        // Given
        var delayQueue = new BucketDelayQueue();
        var wheel = new TimingWheel(10L, 8, 100L, delayQueue);
        var entry = new TimerTaskEntry(() -> {
        }, 260L);
        wheel.add(entry);
        var bucket = delayQueue.peek().orElseThrow(AssertionError::new);

        // When
        wheel.advanceClock(bucket.expiration());
        bucket.flush(wheel::add);

        // Then
        assertTrue(delayQueue.size() >= 1);
    }

    @Test
    void givenManualClockWhenOverflowBucketIsFlushedThenEntryCascadesDeterministically() {
        // Given
        var clock = new ManualClock(100L);
        var delayQueue = new BucketDelayQueue();
        var wheel = new TimingWheel(10L, 8, clock.nowMs(), delayQueue, clock);
        var entry = new TimerTaskEntry(() -> {
        }, 260L);
        wheel.add(entry);

        // When
        clock.advanceMs(140L);
        var overflowBucket = delayQueue.poll().orElseThrow(AssertionError::new);
        wheel.advanceClock(overflowBucket.expiration());
        overflowBucket.flush(wheel::add);

        // Then
        assertEquals(1, delayQueue.size());
        assertTrue(entry.isActive());
    }
}
