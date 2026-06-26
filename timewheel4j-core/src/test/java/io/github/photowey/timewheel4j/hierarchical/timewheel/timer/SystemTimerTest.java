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

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * SystemTimerTest.
 *
 * @author photowey
 * @version 1.0.0
 * @since 2026/06/26
 */
class SystemTimerTest {

    @Test
    void givenDelayedTaskWhenDeadlineExpiresThenTaskIsExecuted() throws InterruptedException {
        // Given
        var timer = newTimer(10, 16);
        var latch = new CountDownLatch(1);
        var startedAt = System.nanoTime();

        try {
            // When
            var timeout = timer.schedule(latch::countDown, 80, TimeUnit.MILLISECONDS);

            // Then
            assertTrue(latch.await(2, TimeUnit.SECONDS));
            assertTrue(timeout.isExpired());
            assertTrue(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt) >= 60);
        } finally {
            timer.shutdown();
        }
    }

    @Test
    void givenCancelledTaskWhenDeadlineExpiresThenTaskIsNotExecuted() throws InterruptedException {
        // Given
        var timer = newTimer(10, 16);
        var executions = new AtomicInteger();

        try {
            var timeout = timer.schedule(executions::incrementAndGet, 100, TimeUnit.MILLISECONDS);

            // When
            var cancelled = timeout.cancel();
            var cancelledAgain = timeout.cancel();
            Thread.sleep(250);

            // Then
            assertTrue(cancelled);
            assertFalse(cancelledAgain);
            assertTrue(timeout.isCancelled());
            assertFalse(timeout.isExpired());
            assertEquals(0, executions.get());
            assertEquals(0, timer.size());
        } finally {
            timer.shutdown();
        }
    }

    @Test
    void givenLongDelayTaskWhenOverflowBucketExpiresThenTaskCascadesAndExecutes() throws InterruptedException {
        // Given
        var timer = newTimer(10, 8);
        var latch = new CountDownLatch(1);

        try {
            // When
            timer.schedule(latch::countDown, 220, TimeUnit.MILLISECONDS);

            // Then
            assertTrue(latch.await(2, TimeUnit.SECONDS));
            assertEquals(0, timer.size());
        } finally {
            timer.shutdown();
        }
    }

    @Test
    void givenPendingTasksWhenOneIsCancelledAndOneExpiresThenSizeTracksActiveTimeouts() throws InterruptedException {
        // Given
        var timer = newTimer(10, 16);
        var latch = new CountDownLatch(1);

        try {
            var first = timer.schedule(latch::countDown, 80, TimeUnit.MILLISECONDS);
            var second = timer.schedule(() -> {
            }, 500, TimeUnit.MILLISECONDS);

            // When
            var cancelled = second.cancel();

            // Then
            assertTrue(cancelled);
            assertEquals(1, timer.size());
            assertTrue(latch.await(2, TimeUnit.SECONDS));
            assertTrue(first.isExpired());
            assertEquals(0, timer.size());
        } finally {
            timer.shutdown();
        }
    }

    @Test
    void givenScheduledAndCancelledTaskWhenMetricsAreReadThenSnapshotTracksAcceptedAndCancelledTimeouts() {
        // Given
        var timer = newTimer(10, 16);

        try {
            var timeout = timer.schedule(() -> {
            }, 500, TimeUnit.MILLISECONDS);

            // When
            var cancelled = timeout.cancel();
            var metrics = timer.metrics();

            // Then
            assertTrue(cancelled);
            assertEquals(1L, metrics.scheduledTimeouts());
            assertEquals(1L, metrics.cancelledTimeouts());
            assertEquals(0L, metrics.expiredTimeouts());
            assertEquals(0L, metrics.rejectedTimeouts());
            assertEquals(0L, metrics.pendingTimeouts());
        } finally {
            timer.shutdown();
        }
    }

    @Test
    void givenDelayedTaskWhenBucketExpiresThenMetricsTrackExpirationAndBucketActivity() throws InterruptedException {
        // Given
        var timer = newTimer(10, 16);
        var latch = new CountDownLatch(1);

        try {
            // When
            timer.schedule(latch::countDown, 80, TimeUnit.MILLISECONDS);

            // Then
            assertTrue(latch.await(2, TimeUnit.SECONDS));
            var metrics = timer.metrics();
            assertEquals(1L, metrics.scheduledTimeouts());
            assertEquals(1L, metrics.expiredTimeouts());
            assertEquals(0L, metrics.cancelledTimeouts());
            assertEquals(0L, metrics.pendingTimeouts());
            assertTrue(metrics.bucketOffers() >= 1L);
            assertTrue(metrics.bucketExpirations() >= 1L);
            assertTrue(metrics.maxBucketDelayMs() >= 0L);
        } finally {
            timer.shutdown();
        }
    }

    @Test
    void givenZeroDelayTaskWhenScheduledThenTaskRunsImmediately() throws InterruptedException {
        // Given
        var timer = newTimer(10, 16);
        var latch = new CountDownLatch(1);

        try {
            // When
            var timeout = timer.schedule(latch::countDown, 0, TimeUnit.MILLISECONDS);

            // Then
            assertTrue(latch.await(1, TimeUnit.SECONDS));
            assertTrue(timeout.isExpired());
            assertEquals(0, timer.size());
        } finally {
            timer.shutdown();
        }
    }

    @Test
    void givenExpiredTimeoutWhenCancelIsCalledThenCancellationFails() throws InterruptedException {
        // Given
        var timer = newTimer(10, 16);
        var latch = new CountDownLatch(1);

        try {
            var timeout = timer.schedule(latch::countDown, 0, TimeUnit.MILLISECONDS);
            assertTrue(latch.await(1, TimeUnit.SECONDS));

            // When
            var cancelled = timeout.cancel();

            // Then
            assertFalse(cancelled);
            assertTrue(timeout.isExpired());
            assertFalse(timeout.isCancelled());
        } finally {
            timer.shutdown();
        }
    }

    @Test
    void givenExternalExecutorWhenTimerIsShutdownThenExecutorRemainsOwnedByCaller() throws InterruptedException {
        // Given
        var executor = Executors.newSingleThreadExecutor(new TimerThreadFactory("external-worker"));
        var timer = TimerBuilder.builder()
            .tick(10, TimeUnit.MILLISECONDS)
            .wheelSize(16)
            .executorService(executor)
            .schedulerName("external-scheduler")
            .build();
        var latch = new CountDownLatch(1);
        var workerThreadName = new AtomicReference<String>();

        try {
            // When
            timer.schedule(() -> {
                workerThreadName.set(Thread.currentThread().getName());
                latch.countDown();
            }, 0, TimeUnit.MILLISECONDS);

            // Then
            assertTrue(latch.await(1, TimeUnit.SECONDS));
            timer.shutdown();
            assertFalse(executor.isShutdown());
            assertTrue(workerThreadName.get().startsWith("external-worker-"));
        } finally {
            timer.shutdown();
            executor.shutdownNow();
        }
    }

    @Test
    void givenExternalBossExecutorWhenTimerIsShutdownThenExecutorRemainsOwnedByCaller() throws InterruptedException {
        // Given
        var bossExecutor = Executors.newSingleThreadExecutor(new TimerThreadFactory("external-boss"));
        var timer = TimerBuilder.builder()
            .tick(10, TimeUnit.MILLISECONDS)
            .wheelSize(16)
            .bossExecutor(bossExecutor)
            .workerThreads(1)
            .build();
        var bossThreadName = new AtomicReference<String>();

        try {
            // When / Then
            assertTrue(waitUntilThreadObserved("external-boss-", bossThreadName, 1, TimeUnit.SECONDS));
            timer.shutdown();
            assertFalse(bossExecutor.isShutdown());
        } finally {
            timer.shutdown();
            bossExecutor.shutdownNow();
        }
    }

    @Test
    void givenBossNameWhenConfiguredThenSchedulerRunsOnNamedBossThread() throws InterruptedException {
        // Given
        var timer = TimerBuilder.builder()
            .tick(10, TimeUnit.MILLISECONDS)
            .wheelSize(16)
            .bossName("custom-boss")
            .workerThreads(1)
            .build();
        var bossThreadName = new AtomicReference<String>();

        try {
            // When / Then
            assertTrue(waitUntilThreadObserved("custom-boss-", bossThreadName, 1, TimeUnit.SECONDS));
            assertTrue(bossThreadName.get().startsWith("custom-boss-"));
        } finally {
            timer.shutdown();
        }
    }

    @Test
    void givenOwnedWorkersWhenWorkerNameIsConfiguredThenTaskRunsOnNamedWorkerThread() throws InterruptedException {
        // Given
        var timer = TimerBuilder.builder()
            .tick(10, TimeUnit.MILLISECONDS)
            .wheelSize(16)
            .workerThreads(1)
            .workerName("custom-worker")
            .schedulerName("custom-scheduler")
            .build();
        var latch = new CountDownLatch(1);
        var workerThreadName = new AtomicReference<String>();

        try {
            // When
            timer.schedule(() -> {
                workerThreadName.set(Thread.currentThread().getName());
                latch.countDown();
            }, 0, TimeUnit.MILLISECONDS);

            // Then
            assertTrue(latch.await(1, TimeUnit.SECONDS));
            assertTrue(workerThreadName.get().startsWith("custom-worker-"));
        } finally {
            timer.shutdown();
        }
    }

    @Test
    void givenSaturatedWorkerPoolWhenTaskExpiresThenBossKeepsScheduling() throws InterruptedException {
        // Given
        var worker = new ThreadPoolExecutor(
            1,
            1,
            0L,
            TimeUnit.MILLISECONDS,
            new ArrayBlockingQueue<>(1),
            new TimerThreadFactory("saturated-worker"),
            new ThreadPoolExecutor.AbortPolicy());
        var timer = TimerBuilder.builder()
            .tick(1, TimeUnit.MILLISECONDS)
            .wheelSize(8)
            .executorService(worker)
            .bossName("non-blocking-boss")
            .build();
        var firstStarted = new CountDownLatch(1);
        var releaseFirst = new CountDownLatch(1);
        var queuedExecution = new CountDownLatch(1);
        var recoveredExecution = new CountDownLatch(1);

        try {
            // When
            timer.schedule(() -> {
                firstStarted.countDown();
                await(releaseFirst);
            }, 0, TimeUnit.MILLISECONDS);
            assertTrue(firstStarted.await(1, TimeUnit.SECONDS));
            timer.schedule(queuedExecution::countDown, 0, TimeUnit.MILLISECONDS);
            for (int i = 0; i < 32; i++) {
                timer.schedule(() -> {
                }, 30, TimeUnit.MILLISECONDS);
            }
            assertTrue(waitUntilRejected(timer, 1, TimeUnit.SECONDS));
            releaseFirst.countDown();
            assertTrue(queuedExecution.await(1, TimeUnit.SECONDS));

            timer.schedule(recoveredExecution::countDown, 30, TimeUnit.MILLISECONDS);

            // Then
            assertTrue(recoveredExecution.await(1, TimeUnit.SECONDS));
            assertTrue(timer.metrics().rejectedTimeouts() > 0);
        } finally {
            releaseFirst.countDown();
            timer.shutdown();
            worker.shutdownNow();
        }
    }

    @Test
    void givenOwnedWorkerQueueCapacityWhenExceededThenSubmissionCanBeRejected() throws Exception {
        // Given
        var timer = TimerBuilder.builder()
            .tick(1, TimeUnit.MILLISECONDS)
            .wheelSize(8)
            .workerThreads(1)
            .workerQueueCapacity(1)
            .build();
        var firstStarted = new CountDownLatch(1);
        var releaseFirst = new CountDownLatch(1);
        var submit = SystemTimer.class.getDeclaredMethod("submit", TimerTaskEntry.class);
        submit.setAccessible(true);

        try {
            var first = new TimerTaskEntry(() -> {
                firstStarted.countDown();
                await(releaseFirst);
            }, System.currentTimeMillis() - 1);
            var second = new TimerTaskEntry(() -> {
            }, System.currentTimeMillis() - 1);
            var third = new TimerTaskEntry(() -> {
            }, System.currentTimeMillis() - 1);

            submit.invoke(timer, first);
            assertTrue(firstStarted.await(1, TimeUnit.SECONDS));
            submit.invoke(timer, second);

            // When / Then
            var thrown = assertThrows(Exception.class, () -> submit.invoke(timer, third));
            assertTrue(thrown.getCause() instanceof RejectedExecutionException);
            assertEquals(1L, timer.metrics().rejectedTimeouts());
        } finally {
            releaseFirst.countDown();
            timer.shutdown();
        }
    }

    @Test
    void givenTimerWhenClosedThenFurtherSchedulingIsRejected() {
        // Given
        var timer = newTimer(10, 16);

        // When
        timer.close();
        timer.shutdown();

        // Then
        assertThrows(IllegalStateException.class, () -> timer.schedule(() -> {
        }, 1, TimeUnit.MILLISECONDS));
    }

    @Test
    void givenInvalidScheduleArgumentsWhenSchedulingThenValidationFails() {
        // Given
        var timer = newTimer(10, 16);

        try {
            // When / Then
            assertThrows(NullPointerException.class, () -> timer.schedule(null, 1, TimeUnit.MILLISECONDS));
            assertThrows(NullPointerException.class, () -> timer.schedule(() -> {
            }, 1, null));
            assertThrows(IllegalArgumentException.class, () -> timer.schedule(() -> {
            }, -1, TimeUnit.MILLISECONDS));
        } finally {
            timer.shutdown();
        }
    }

    @Test
    void givenIdleTimerWhenNoBucketExpiresThenSchedulerCanShutdownCleanly() throws InterruptedException {
        // Given
        var timer = TimerBuilder.builder()
            .tick(10, TimeUnit.MILLISECONDS)
            .wheelSize(16)
            .workerThreads(1)
            .build();

        // When
        Thread.sleep(150);
        timer.shutdown();
        timer.shutdown();

        // Then
        assertThrows(IllegalStateException.class, () -> timer.schedule(() -> {
        }, 1, TimeUnit.MILLISECONDS));
    }

    @Test
    void givenDueTaskWhenTimerIsStoppedThenSchedulerDoesNotSubmitIt() throws Exception {
        // Given
        var timer = TimerBuilder.builder()
            .tick(1, TimeUnit.MILLISECONDS)
            .wheelSize(8)
            .workerThreads(1)
            .build();
        var executions = new AtomicInteger();
        var entry = new TimerTaskEntry(executions::incrementAndGet, System.currentTimeMillis() - 1);

        try {
            var addOrRun = SystemTimer.class.getDeclaredMethod("addOrRun", TimerTaskEntry.class);
            addOrRun.setAccessible(true);

            // When
            timer.shutdown();
            addOrRun.invoke(timer, entry);
            Thread.sleep(50);

            // Then
            assertEquals(0, executions.get());
        } finally {
            timer.shutdown();
        }
    }

    @Test
    void givenShutdownDuringTaskExpiryWhenSchedulerSubmitsThenSubmissionIsIgnored() throws Exception {
        // Given
        var timer = TimerBuilder.builder()
            .tick(1, TimeUnit.MILLISECONDS)
            .wheelSize(8)
            .workerThreads(1)
            .build();
        var executions = new AtomicInteger();
        var entry = new TimerTaskEntry(executions::incrementAndGet, System.currentTimeMillis() - 1, timer::shutdown);

        try {
            var submit = SystemTimer.class.getDeclaredMethod("submit", TimerTaskEntry.class);
            submit.setAccessible(true);

            // When
            assertDoesNotThrow(() -> submit.invoke(timer, entry));
            Thread.sleep(50);

            // Then
            assertTrue(entry.isExpired());
            assertEquals(0, executions.get());
            assertEquals(1L, timer.metrics().rejectedTimeouts());
        } finally {
            timer.shutdown();
        }
    }

    private static Timer newTimer(long tickMs, int wheelSize) {
        return TimerBuilder.builder()
            .tick(tickMs, TimeUnit.MILLISECONDS)
            .wheelSize(wheelSize)
            .workerThreads(1)
            .build();
    }

    private static boolean waitUntilThreadObserved(
        String prefix,
        AtomicReference<String> observedName,
        long timeout,
        TimeUnit unit) throws InterruptedException {
        long deadline = System.nanoTime() + unit.toNanos(timeout);
        while (System.nanoTime() < deadline) {
            Thread.getAllStackTraces().keySet().stream()
                .map(Thread::getName)
                .filter((name) -> name.startsWith(prefix))
                .findFirst()
                .ifPresent(observedName::set);
            if (observedName.get() != null) {
                return true;
            }
            Thread.sleep(10);
        }
        return false;
    }

    private static boolean waitUntilRejected(Timer timer, long timeout, TimeUnit unit) throws InterruptedException {
        long deadline = System.nanoTime() + unit.toNanos(timeout);
        while (System.nanoTime() < deadline) {
            if (timer.metrics().rejectedTimeouts() > 0) {
                return true;
            }
            Thread.sleep(10);
        }
        return false;
    }

    private static void await(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
