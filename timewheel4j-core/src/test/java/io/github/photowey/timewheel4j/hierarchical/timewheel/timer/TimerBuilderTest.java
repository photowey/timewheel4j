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

import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * TimerBuilderTest.
 *
 * @author photowey
 * @version 1.0.0
 * @since 2026/06/26
 */
class TimerBuilderTest {

    @Test
    void givenDefaultBuilderWhenBuildIsCalledThenTimerIsCreated() {
        // Given
        var builder = TimerBuilder.builder();

        // When
        var timer = builder.build();

        // Then
        try {
            assertNotNull(timer);
        } finally {
            timer.shutdown();
        }
    }

    @Test
    void givenInvalidTickWhenConfiguredThenValidationFails() {
        // Given
        var builder = TimerBuilder.builder();

        // When / Then
        assertThrows(NullPointerException.class, () -> builder.tick(1, null));
        for (var invalidTick : List.of(0L, 999L)) {
            assertThrows(IllegalArgumentException.class, () -> builder.tick(invalidTick, TimeUnit.MICROSECONDS));
        }
    }

    @Test
    void givenInvalidWheelSizeWhenConfiguredThenValidationFails() {
        // Given
        var builder = TimerBuilder.builder();

        // When / Then
        assertThrows(IllegalArgumentException.class, () -> builder.wheelSize(0));
    }

    @Test
    void givenInvalidWorkerThreadsWhenConfiguredThenValidationFails() {
        // Given
        var builder = TimerBuilder.builder();

        // When / Then
        assertThrows(IllegalArgumentException.class, () -> builder.workerThreads(0));
    }

    @Test
    void givenInvalidWorkerQueueCapacityWhenConfiguredThenValidationFails() {
        // Given
        var builder = TimerBuilder.builder();

        // When / Then
        assertThrows(IllegalArgumentException.class, () -> builder.workerQueueCapacity(0));
    }

    @Test
    void givenInvalidExecutorWhenConfiguredThenValidationFails() {
        // Given
        var builder = TimerBuilder.builder();

        // When / Then
        assertThrows(NullPointerException.class, () -> builder.bossExecutor(null));
        assertThrows(NullPointerException.class, () -> builder.executorService(null));
        assertThrows(NullPointerException.class, () -> builder.workerExecutor(null));
    }

    @Test
    void givenInvalidThreadNamesWhenConfiguredThenValidationFails() {
        // Given
        var builder = TimerBuilder.builder();

        // When / Then
        for (var blankName : List.of("  ", "\t")) {
            assertThrows(IllegalArgumentException.class, () -> builder.bossName(blankName));
            assertThrows(IllegalArgumentException.class, () -> builder.workerName(blankName));
            assertThrows(IllegalArgumentException.class, () -> builder.schedulerName(blankName));
        }
        assertThrows(IllegalArgumentException.class, () -> builder.bossName(null));
        assertThrows(IllegalArgumentException.class, () -> builder.workerName(null));
        assertThrows(IllegalArgumentException.class, () -> builder.schedulerName(null));
    }

    @Test
    void givenExternalExecutorWhenBuildIsCalledThenTimerIsCreated() {
        // Given
        var executor = Executors.newSingleThreadExecutor();

        // When
        var timer = TimerBuilder.builder()
            .tick(1, TimeUnit.MILLISECONDS)
            .wheelSize(8)
            .executorService(executor)
            .build();

        // Then
        try {
            assertNotNull(timer);
        } finally {
            timer.shutdown();
            executor.shutdownNow();
        }
    }

    @Test
    void givenExternalWorkerExecutorAliasWhenBuildIsCalledThenTimerIsCreated() {
        // Given
        var executor = Executors.newSingleThreadExecutor();

        // When
        var timer = TimerBuilder.builder()
            .tick(1, TimeUnit.MILLISECONDS)
            .wheelSize(8)
            .workerExecutor(executor)
            .build();

        // Then
        try {
            assertNotNull(timer);
        } finally {
            timer.shutdown();
            executor.shutdownNow();
        }
    }
}
