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
package com.photowey.hierarchical.timewheel.group;

import com.photowey.hierarchical.timewheel.core.event.AdvanceTickEvent;
import com.photowey.hierarchical.timewheel.core.task.ScheduledTask;
import com.photowey.hierarchical.timewheel.core.task.TickTask;
import com.photowey.hierarchical.timewheel.scheduler.Worker;
import org.junit.jupiter.api.Test;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DefaultSchedulerGroupTest {

    @Test
    void shouldCascadeTaskFromHigherLevelWheelBeforeExecuting() {
        AtomicInteger executions = new AtomicInteger();
        Worker worker = task -> {
            task.run();
            executions.incrementAndGet();
        };
        DefaultSchedulerGroup schedulerGroup = new DefaultSchedulerGroup(worker, 1, new int[]{4, 4});

        schedulerGroup.schedule(new ScheduledTask(() -> {
        }, 6, TimeUnit.MILLISECONDS));

        tick(schedulerGroup, 5);
        assertEquals(0, executions.get());

        tick(schedulerGroup, 1);
        assertEquals(1, executions.get());
    }

    @Test
    void shouldNotExecuteTaskBeforeItsDeadlineWithinSameBucket() {
        AtomicInteger executions = new AtomicInteger();
        Worker worker = task -> {
            task.run();
            executions.incrementAndGet();
        };
        DefaultSchedulerGroup schedulerGroup = new DefaultSchedulerGroup(worker, 1, new int[]{8});

        schedulerGroup.schedule(new ScheduledTask(() -> {
        }, 3, TimeUnit.MILLISECONDS));

        tick(schedulerGroup, 2);
        assertEquals(0, executions.get());

        tick(schedulerGroup, 1);
        assertEquals(1, executions.get());
    }

    private static void tick(DefaultSchedulerGroup schedulerGroup, int times) {
        for (int i = 0; i < times; i++) {
            schedulerGroup.handleEvent(new AdvanceTickEvent(TickTask.create()));
        }
    }
}
