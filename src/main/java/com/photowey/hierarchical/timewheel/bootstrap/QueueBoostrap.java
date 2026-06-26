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
package com.photowey.hierarchical.timewheel.bootstrap;

import com.photowey.hierarchical.timewheel.group.DefaultSchedulerGroup;
import com.photowey.hierarchical.timewheel.group.DefaultWorkerGroup;
import com.photowey.hierarchical.timewheel.group.SchedulerGroup;
import com.photowey.hierarchical.timewheel.group.WorkerGroup;
import com.photowey.hierarchical.timewheel.publisher.DefaultEventPublisher;
import com.photowey.hierarchical.timewheel.publisher.EventPublisher;
import com.photowey.hierarchical.timewheel.queue.delay.DefaultDelayQueue;
import com.photowey.hierarchical.timewheel.queue.delay.DelayQueue;
import com.photowey.hierarchical.timewheel.registry.EventGroupRegistry;
import com.photowey.hierarchical.timewheel.registry.EventRegistry;
import com.photowey.hierarchical.timewheel.scheduler.DefaultScheduler;
import com.photowey.hierarchical.timewheel.scheduler.DefaultWorker;
import com.photowey.hierarchical.timewheel.scheduler.Scheduler;
import com.photowey.hierarchical.timewheel.scheduler.Worker;

import java.io.Serializable;
import java.util.concurrent.ExecutorService;

import static com.photowey.hierarchical.timewheel.core.fx.Functions.*;

/**
 * {@code QueueBoostrap}
 *
 * @author photowey
 * @version 1.0.0
 * @since 2023/04/04
 */
public class QueueBoostrap implements Serializable {

    private static final long serialVersionUID = 3932289465514141374L;

    private Scheduler boss;
    private SchedulerGroup schedulerGroup;
    private WorkerGroup workerGroup;
    private long maxDelayMills;
    private long tickMillis;

    private QueueBoostrap() {

    }

    public static class QueueBoostrapBuilder implements Serializable {

        private long delayMills;
        private long maxDelay;
        private int level;
        private long corePoolSize;
        private long maximumPoolSize;
        private long keepAliveTime;
        private String workerPoolName;
        private int[] intervals;

        private ExecutorService executorService;

        public QueueBoostrapBuilder delayMills(long delayMills) {
            this.delayMills = delayMills;
            return this;
        }

        public QueueBoostrapBuilder maxDelay(long maxDelay) {
            this.maxDelay = maxDelay;
            return this;
        }

        public QueueBoostrapBuilder level(int level) {
            this.level = level;
            return this;
        }

        public QueueBoostrapBuilder intervals(int[] intervals) {
            this.intervals = intervals;
            return this;
        }

        public QueueBoostrapBuilder corePoolSize(int corePoolSize) {
            this.corePoolSize = corePoolSize;
            return this;
        }

        public QueueBoostrapBuilder maximumPoolSize(int maximumPoolSize) {
            this.maximumPoolSize = maximumPoolSize;
            return this;
        }

        public QueueBoostrapBuilder keepAliveTime(long keepAliveTime) {
            this.keepAliveTime = keepAliveTime;
            return this;
        }

        public QueueBoostrapBuilder workerPoolName(String workerPoolName) {
            this.workerPoolName = workerPoolName;
            return this;
        }

        public QueueBoostrapBuilder executorService(ExecutorService executorService) {
            this.executorService = executorService;
            return this;
        }

        public QueueBoostrap build() {
            checkPositive(this.delayMills, "delayMills");
            checkPositive(this.maxDelay, "maxDelay");
            checkPositive(this.level, "level");
            checkNotNull(this.intervals, "intervals");
            checkTure(level == this.intervals.length, "The level and intervals not matched");
            long capacityMillis = DefaultSchedulerGroup.capacityMillis(this.delayMills, this.intervals);
            checkTure(this.maxDelay <= capacityMillis, "The maxDelay exceeds time-wheel capacity");

            QueueBoostrap boostrap = new QueueBoostrap();
            EventRegistry eventRegistry = new EventGroupRegistry();
            EventPublisher eventPublisher = new DefaultEventPublisher(eventRegistry);
            Worker worker = new DefaultWorker(eventPublisher);
            SchedulerGroup schedulerGroup = new DefaultSchedulerGroup(worker, this.delayMills, this.intervals);
            WorkerGroup workerGroup = this.createWorkerGroup();
            Scheduler boss = new DefaultScheduler(this.delayMills, this.delayMills, eventPublisher);

            eventRegistry.register(schedulerGroup.topic(), schedulerGroup);
            eventRegistry.register(workerGroup.topic(), workerGroup);

            boostrap.setBoss(boss);
            boostrap.setSchedulerGroup(schedulerGroup);
            boostrap.setWorkerGroup(workerGroup);
            boostrap.setMaxDelayMills(this.maxDelay);
            boostrap.setTickMillis(this.delayMills);

            return boostrap;
        }

        private WorkerGroup createWorkerGroup() {
            if (this.executorService != null) {
                return new DefaultWorkerGroup(this.executorService);
            }

            int corePoolSize = this.corePoolSize > 0 ? (int) this.corePoolSize : Runtime.getRuntime().availableProcessors();
            int maximumPoolSize = this.maximumPoolSize > 0 ? (int) this.maximumPoolSize : corePoolSize << 1;
            long keepAliveTime = this.keepAliveTime > 0 ? this.keepAliveTime : 5000L;
            String workerPoolName = this.workerPoolName == null ? "timewheel" : checkNotBlank(this.workerPoolName, "workerPoolName");

            return new DefaultWorkerGroup(corePoolSize, maximumPoolSize, keepAliveTime, workerPoolName);
        }
    }


    public DelayQueue start() {
        this.boss.start();

        return new DefaultDelayQueue(this.boss, this.schedulerGroup, this.workerGroup, this.maxDelayMills, this.tickMillis);
    }

    private void setBoss(Scheduler boss) {
        this.boss = boss;
    }

    private void setSchedulerGroup(SchedulerGroup schedulerGroup) {
        this.schedulerGroup = schedulerGroup;
    }

    private void setWorkerGroup(WorkerGroup workerGroup) {
        this.workerGroup = workerGroup;
    }

    private void setMaxDelayMills(long maxDelayMills) {
        this.maxDelayMills = maxDelayMills;
    }

    private void setTickMillis(long tickMillis) {
        this.tickMillis = tickMillis;
    }
}
