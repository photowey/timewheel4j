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
package com.photowey.hierarchical.timewheel.scheduler;

import com.photowey.hierarchical.timewheel.core.event.AdvanceTickEvent;
import com.photowey.hierarchical.timewheel.core.event.TickEvent;
import com.photowey.hierarchical.timewheel.core.task.TickTask;
import com.photowey.hierarchical.timewheel.engine.TimeWheelEngine;
import com.photowey.hierarchical.timewheel.publisher.EventPublisher;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import static com.photowey.hierarchical.timewheel.core.fx.Functions.checkPositive;

/**
 * {@code DefaultScheduler}
 *
 * @author photowey
 * @date 2023/04/05
 * @since 1.0.0
 */
public class DefaultScheduler implements Scheduler {

    private final long initialDelay;
    private final long period;
    private final TimeUnit unit;
    private final ScheduledExecutorService executorService;

    public DefaultScheduler(long delayMills, long periodMills) {
        checkPositive(delayMills, "delayMills");
        checkPositive(delayMills, "periodMills");

        this.initialDelay = delayMills;
        this.period = periodMills;
        this.unit = TimeUnit.MILLISECONDS;
        this.executorService = Executors.newSingleThreadScheduledExecutor();
    }

    @Override
    public void start() {
        this.executorService.scheduleAtFixedRate(this::schedule, this.initialDelay, this.period, this.unit);
    }

    @Override
    public void schedule() {
        TickEvent tickEvent = new AdvanceTickEvent(TickTask.create());

        EventPublisher eventPublisher = TimeWheelEngine.getInstance().eventPublisher();
        eventPublisher.publishEvent(tickEvent);
    }

    @Override
    public void shutdown() {
        this.executorService.shutdown();
    }
}
