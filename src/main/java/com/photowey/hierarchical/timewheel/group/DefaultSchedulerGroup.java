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

import com.photowey.hierarchical.timewheel.core.event.Event;
import com.photowey.hierarchical.timewheel.core.event.TickEvent;
import com.photowey.hierarchical.timewheel.core.task.ScheduledTask;
import com.photowey.hierarchical.timewheel.scheduler.Worker;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

import static com.photowey.hierarchical.timewheel.core.fx.Functions.checkNotNull;
import static com.photowey.hierarchical.timewheel.core.fx.Functions.checkPositive;

/**
 * {@code DefaultSchedulerGroup}
 *
 * @author photowey
 * @version 1.0.0
 * @since 2023/04/05
 */
public class DefaultSchedulerGroup implements SchedulerGroup {

    private final List<List<Bucket>> wheels;
    private final Worker worker;
    private final long tickMillis;
    private final long[] spans;
    private long currentTick;

    public DefaultSchedulerGroup(Worker worker) {
        this(worker, 1L, new int[]{512, 512});
    }

    public DefaultSchedulerGroup(Worker worker, long tickMillis, int[] intervals) {
        this.worker = checkNotNull(worker, "worker");
        this.tickMillis = checkPositive(tickMillis, "tickMillis");
        this.spans = buildSpans(intervals);
        this.wheels = buildWheels(intervals);
    }

    @Override
    public synchronized void schedule(ScheduledTask task) {
        this.addTask(checkNotNull(task, "task"));
    }

    @Override
    public synchronized void handleEvent(Event event) {
        if (event instanceof TickEvent) {
            this.advanceClock();
        }
    }

    private void advanceClock() {
        this.currentTick++;

        this.cascade();
        Bucket bucket = this.bucket(0, this.currentTick);
        List<TaskEntry> tasks = bucket.drain();
        for (TaskEntry task : tasks) {
            if (task.deadlineTick <= this.currentTick) {
                this.worker.run(task.task);
            } else {
                this.addTask(task);
            }
        }
    }

    private void cascade() {
        for (int level = this.wheels.size() - 1; level > 0; level--) {
            if (this.currentTick % this.spans[level - 1] != 0) {
                continue;
            }

            Bucket bucket = this.bucket(level, this.currentTick);
            List<TaskEntry> tasks = bucket.drain();
            for (TaskEntry task : tasks) {
                this.addTask(task);
            }
        }
    }

    private void addTask(ScheduledTask task) {
        this.addTask(new TaskEntry(task, this.currentTick + task.delayTicks(this.tickMillis)));
    }

    private void addTask(TaskEntry task) {
        long delayTicks = Math.max(0, task.deadlineTick - this.currentTick);
        int level = this.resolveLevel(delayTicks);
        this.bucket(level, task.deadlineTick).add(task);
    }

    private int resolveLevel(long delayTicks) {
        for (int i = 0; i < this.spans.length; i++) {
            if (delayTicks < this.spans[i]) {
                return i;
            }
        }

        return this.spans.length - 1;
    }

    private Bucket bucket(int level, long deadlineTick) {
        List<Bucket> buckets = this.wheels.get(level);
        long span = level == 0 ? 1L : this.spans[level - 1];
        int index = (int) ((deadlineTick / span) % buckets.size());
        return buckets.get(index);
    }

    private static long[] buildSpans(int[] intervals) {
        checkNotNull(intervals, "intervals");
        if (intervals.length == 0) {
            throw new IllegalArgumentException("intervals must not be empty");
        }

        long[] spans = new long[intervals.length];
        long span = 1L;
        for (int i = 0; i < intervals.length; i++) {
            checkPositive(intervals[i], "intervals[" + i + "]");
            span = Math.multiplyExact(span, intervals[i]);
            spans[i] = span;
        }
        return spans;
    }

    private static List<List<Bucket>> buildWheels(int[] intervals) {
        List<List<Bucket>> wheels = new ArrayList<>(intervals.length);
        for (int interval : intervals) {
            List<Bucket> buckets = new ArrayList<>(interval);
            for (int i = 0; i < interval; i++) {
                buckets.add(new Bucket());
            }
            wheels.add(buckets);
        }
        return wheels;
    }

    public static long capacityMillis(long tickMillis, int[] intervals) {
        long[] spans = buildSpans(intervals);
        return Math.multiplyExact(checkPositive(tickMillis, "tickMillis"), spans[spans.length - 1]);
    }

    private static class Bucket {
        private final List<TaskEntry> tasks = new LinkedList<>();

        void add(TaskEntry task) {
            this.tasks.add(task);
        }

        List<TaskEntry> drain() {
            List<TaskEntry> snapshot = new ArrayList<>(this.tasks);
            this.tasks.clear();
            return snapshot;
        }
    }

    private static class TaskEntry {
        private final ScheduledTask task;
        private final long deadlineTick;

        private TaskEntry(ScheduledTask task, long deadlineTick) {
            this.task = task;
            this.deadlineTick = deadlineTick;
        }
    }
}
