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

import com.photowey.hierarchical.timewheel.queue.delay.DelayQueue;
import com.photowey.hierarchical.timewheel.scheduler.DefaultScheduler;
import com.photowey.hierarchical.timewheel.scheduler.DefaultWorker;
import com.photowey.hierarchical.timewheel.scheduler.Scheduler;
import com.photowey.hierarchical.timewheel.scheduler.Worker;

import java.io.Serializable;
import java.util.concurrent.ExecutorService;

import static com.photowey.hierarchical.timewheel.core.fx.Functions.checkTure;

/**
 * {@code QueueBoostrap}
 *
 * @author photowey
 * @date 2023/04/04
 * @since 1.0.0
 */
public class QueueBoostrap implements Serializable {

    private static final long serialVersionUID = 3932289465514141374L;

    private Scheduler boss;
    private Worker worker;

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
            checkTure(level == this.intervals.length, "The level and intervals not matched");

            QueueBoostrap boostrap = new QueueBoostrap();
            Scheduler boss = new DefaultScheduler(this.delayMills, this.intervals[0]);
            Worker worker = new DefaultWorker();

            boostrap.setBoss(boss);
            boostrap.setWorker(worker);

            // TODO

            return boostrap;
        }
    }


    public DelayQueue start() {
        // TODO
        return null;
    }

    private void setBoss(Scheduler boss) {
        this.boss = boss;
    }

    private void setWorker(Worker worker) {
        this.worker = worker;
    }
}
