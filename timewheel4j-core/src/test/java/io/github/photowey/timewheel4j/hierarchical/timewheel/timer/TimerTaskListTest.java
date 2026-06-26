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

import java.util.ArrayList;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * TimerTaskListTest.
 *
 * @author photowey
 * @version 1.0.0
 * @since 2026/06/26
 */
class TimerTaskListTest {

    @Test
    void givenExpirationWhenSetTwiceThenOnlyChangeIsReported() {
        // Given
        var list = new TimerTaskList();

        // When
        var first = list.setExpiration(System.currentTimeMillis() + 1_000);
        var second = list.setExpiration(list.expiration());

        // Then
        assertTrue(first);
        assertEquals(false, second);
        assertTrue(list.getDelay(TimeUnit.MILLISECONDS) <= 1_000);
    }

    @Test
    void givenManualClockWhenDelayIsReadThenDelayUsesInjectedTime() {
        // Given
        var clock = new ManualClock(1_000L);
        var list = new TimerTaskList(clock);
        list.setExpiration(1_250L);

        // When
        var initialDelay = list.getDelay(TimeUnit.MILLISECONDS);
        clock.advanceMs(200L);
        var advancedDelay = list.getDelay(TimeUnit.MILLISECONDS);

        // Then
        assertEquals(250L, initialDelay);
        assertEquals(50L, advancedDelay);
    }

    @Test
    void givenEntryInOneListWhenAddedToAnotherThenEntryMovesLists() {
        // Given
        var first = new TimerTaskList();
        var second = new TimerTaskList();
        var entry = new TimerTaskEntry(() -> {
        }, 100L);
        first.add(entry);

        // When
        second.add(entry);

        // Then
        assertEquals(second, entry.list);
    }

    @Test
    void givenEntriesWhenListIsFlushedThenEntriesAreConsumedAndExpirationResets() {
        // Given
        var list = new TimerTaskList();
        var first = new TimerTaskEntry(() -> {
        }, 100L);
        var second = new TimerTaskEntry(() -> {
        }, 200L);
        var flushed = new ArrayList<TimerTaskEntry>();
        list.setExpiration(100L);
        list.add(first);
        list.add(second);

        // When
        list.flush(flushed::add);
        list.flush(flushed::add);

        // Then
        assertEquals(2, flushed.size());
        assertEquals(-1L, list.expiration());
    }

    @Test
    void givenTwoListsWhenComparedThenEarlierExpirationSortsFirst() {
        // Given
        var earlier = new TimerTaskList();
        var later = new TimerTaskList();
        earlier.setExpiration(100L);
        later.setExpiration(200L);

        // When
        var comparison = earlier.compareTo(later);

        // Then
        assertTrue(comparison < 0);
    }
}
