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

import com.photowey.hierarchical.timewheel.core.task.ScheduledTask;
import com.photowey.hierarchical.timewheel.group.SchedulerGroup;
import com.photowey.hierarchical.timewheel.group.WorkerGroup;
import com.photowey.hierarchical.timewheel.scheduler.Scheduler;

import java.util.concurrent.TimeUnit;

import static com.photowey.hierarchical.timewheel.core.fx.Functions.checkNotNull;
import static com.photowey.hierarchical.timewheel.core.fx.Functions.checkPositive;

/**
 * {@code DefaultDelayQueue}
 *
 * @author photowey
 * @version 1.0.0
 * @since 2023/04/04
 */
public class DefaultDelayQueue implements DelayQueue {

    private static final long serialVersionUID = -2376740430639645971L;

    private final Scheduler scheduler;
    private final SchedulerGroup schedulerGroup;
    private final WorkerGroup workerGroup;
    private final long maxDelayMills;
    private final long tickMillis;

    public DefaultDelayQueue(
            Scheduler scheduler,
            SchedulerGroup schedulerGroup,
            WorkerGroup workerGroup,
            long maxDelayMills,
            long tickMillis) {
        this.scheduler = checkNotNull(scheduler, "scheduler");
        this.schedulerGroup = checkNotNull(schedulerGroup, "schedulerGroup");
        this.workerGroup = checkNotNull(workerGroup, "workerGroup");
        this.maxDelayMills = checkPositive(maxDelayMills, "maxDelayMills");
        this.tickMillis = checkPositive(tickMillis, "tickMillis");
    }

    @Override
    public SchedulerGroup scheduler() {
        return this.schedulerGroup;
    }

    @Override
    public WorkerGroup worker() {
        return this.workerGroup;
    }

    @Override
    public void schedule(Runnable task, long delay, TimeUnit unit) {
        checkNotNull(task, "task");
        checkNotNull(unit, "unit");
        long delayMills = unit.toMillis(delay);
        if (delayMills > this.maxDelayMills) {
            throw new IllegalArgumentException("delayMills : " + delayMills + " (expected: <= " + this.maxDelayMills + ")");
        }

        this.schedulerGroup.schedule(new ScheduledTask(task, delay, unit));
    }

    @Override
    public void shutdown() {
        this.scheduler.shutdown();
        this.schedulerGroup.shutdown();
        this.workerGroup.shutdown();
    }
}
