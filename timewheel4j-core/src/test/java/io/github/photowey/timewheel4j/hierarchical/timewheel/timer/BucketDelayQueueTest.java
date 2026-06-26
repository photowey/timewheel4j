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

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * BucketDelayQueueTest.
 *
 * @author photowey
 * @version 1.0.0
 * @since 2026/06/26
 */
class BucketDelayQueueTest {

    @Test
    void givenEmptyQueueWhenPollIsCalledThenOptionalIsEmpty() throws InterruptedException {
        // Given
        var queue = new BucketDelayQueue();

        // When
        var bucket = queue.poll(1, TimeUnit.MILLISECONDS);

        // Then
        assertFalse(bucket.isPresent());
    }

    @Test
    void givenExpiredBucketWhenPollIsCalledThenOptionalContainsBucket() throws InterruptedException {
        // Given
        var clock = new ManualClock(1_000L);
        var queue = new BucketDelayQueue();
        var bucket = new TimerTaskList(clock);
        bucket.setExpiration(1_000L);

        // When
        queue.offer(bucket);
        var polled = queue.poll(1, TimeUnit.MILLISECONDS);

        // Then
        assertTrue(polled.isPresent());
        assertSame(bucket, polled.get());
        assertEquals(0, queue.size());
    }

    @Test
    void givenEmptyQueueWhenPeekIsCalledThenOptionalIsEmpty() {
        // Given
        var queue = new BucketDelayQueue();

        // When
        var bucket = queue.peek();

        // Then
        assertFalse(bucket.isPresent());
    }
}
