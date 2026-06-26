/*
 * Copyright © 2023 the original author or authors.
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
package com.photowey.hierarchical.timewheel.queue.delay;

import com.photowey.hierarchical.timewheel.bootstrap.QueueBoostrap;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DelayQueueIntegrationTest {

    @Test
    void shouldExecuteScheduledTaskAfterDelay() throws InterruptedException {
        DelayQueue queue = new QueueBoostrap.QueueBoostrapBuilder()
                .delayMills(1)
                .maxDelay(1_000)
                .level(2)
                .intervals(new int[]{64, 64})
                .corePoolSize(1)
                .maximumPoolSize(1)
                .keepAliveTime(100)
                .workerPoolName("timewheel-test")
                .build()
                .start();

        CountDownLatch latch = new CountDownLatch(1);
        try {
            queue.schedule(latch::countDown, 20, TimeUnit.MILLISECONDS);

            assertTrue(latch.await(1, TimeUnit.SECONDS));
        } finally {
            queue.shutdown();
        }
    }

    @Test
    void shouldRejectDelayGreaterThanMaxDelay() {
        DelayQueue queue = new QueueBoostrap.QueueBoostrapBuilder()
                .delayMills(1)
                .maxDelay(10)
                .level(1)
                .intervals(new int[]{16})
                .build()
                .start();

        try {
            assertThrows(IllegalArgumentException.class, () -> queue.schedule(() -> {
            }, 11, TimeUnit.MILLISECONDS));
        } finally {
            queue.shutdown();
        }
    }

    @Test
    void shouldRejectMaxDelayGreaterThanWheelCapacity() {
        assertThrows(IllegalArgumentException.class, () -> new QueueBoostrap.QueueBoostrapBuilder()
                .delayMills(1)
                .maxDelay(17)
                .level(1)
                .intervals(new int[]{16})
                .build());
    }
}
