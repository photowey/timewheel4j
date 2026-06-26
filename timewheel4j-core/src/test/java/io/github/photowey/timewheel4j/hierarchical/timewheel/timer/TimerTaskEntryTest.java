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

import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * TimerTaskEntryTest.
 *
 * @author photowey
 * @version 1.0.0
 * @since 2026/06/26
 */
class TimerTaskEntryTest {

    @Test
    void givenActiveEntryWhenCancelledThenCompletionRunsOnce() {
        // Given
        AtomicInteger completions = new AtomicInteger();
        TimerTaskEntry entry = new TimerTaskEntry(() -> {
        }, 100L, completions::incrementAndGet);

        // When
        boolean cancelled = entry.cancel();
        boolean cancelledAgain = entry.cancel();
        boolean expiredAfterCancel = entry.expire();

        // Then
        assertTrue(cancelled);
        assertFalse(cancelledAgain);
        assertFalse(expiredAfterCancel);
        assertTrue(entry.isCancelled());
        assertFalse(entry.isExpired());
        assertFalse(entry.isActive());
        assertEquals(1, completions.get());
    }

    @Test
    void givenActiveEntryWhenExpiredThenCompletionRunsOnceAndTaskCanRun() {
        // Given
        AtomicInteger executions = new AtomicInteger();
        AtomicInteger completions = new AtomicInteger();
        TimerTaskEntry entry = new TimerTaskEntry(executions::incrementAndGet, 100L, completions::incrementAndGet);

        // When
        boolean expired = entry.expire();
        boolean expiredAgain = entry.expire();
        boolean cancelledAfterExpire = entry.cancel();
        entry.run();

        // Then
        assertTrue(expired);
        assertFalse(expiredAgain);
        assertFalse(cancelledAfterExpire);
        assertTrue(entry.isExpired());
        assertFalse(entry.isCancelled());
        assertFalse(entry.isActive());
        assertEquals(1, executions.get());
        assertEquals(1, completions.get());
    }

    @Test
    void givenEntryInListWhenRemovedThenItLeavesTheList() {
        // Given
        TimerTaskList list = new TimerTaskList();
        TimerTaskEntry entry = new TimerTaskEntry(() -> {
        }, 100L);
        list.add(entry);

        // When
        entry.remove();
        entry.remove();

        // Then
        assertEquals(null, entry.list);
        assertEquals(null, entry.prev);
        assertEquals(null, entry.next);
    }
}
