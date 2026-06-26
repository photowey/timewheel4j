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
package io.github.photowey.timewheel4j.spring.boot.autoconfigure;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for the auto-configured timewheel4j timer.
 *
 * @author photowey
 * @version 1.0.0
 * @since 2026/06/26
 */
@ConfigurationProperties(prefix = "timewheel4j")
public class Timewheel4jProperties {

    private boolean enabled = true;
    private Duration tick = Duration.ofMillis(1);
    private int wheelSize = 512;
    private final Boss boss = new Boss();
    private final Worker worker = new Worker();

    public boolean isEnabled() {
        return this.enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public Duration getTick() {
        return this.tick;
    }

    public void setTick(Duration tick) {
        this.tick = tick;
    }

    public int getWheelSize() {
        return this.wheelSize;
    }

    public void setWheelSize(int wheelSize) {
        this.wheelSize = wheelSize;
    }

    public Boss getBoss() {
        return this.boss;
    }

    public Worker getWorker() {
        return this.worker;
    }

    public int getWorkerThreads() {
        return this.worker.getThreads();
    }

    public void setWorkerThreads(int workerThreads) {
        this.worker.setThreads(workerThreads);
    }

    public int getWorkerQueueCapacity() {
        return this.worker.getQueueCapacity();
    }

    public void setWorkerQueueCapacity(int workerQueueCapacity) {
        this.worker.setQueueCapacity(workerQueueCapacity);
    }

    public String getWorkerName() {
        return this.worker.getName();
    }

    public void setWorkerName(String workerName) {
        this.worker.setName(workerName);
    }

    public String getSchedulerName() {
        return this.boss.getName();
    }

    public void setSchedulerName(String schedulerName) {
        this.boss.setName(schedulerName);
    }

    /**
     * Boss.
     *
     * @author photowey
     * @version 1.0.0
     * @since 2026/06/26
     */
    public static class Boss {

        private String name = "timewheel-boss";
        private final Executor executor = new Executor();

        public String getName() {
            return this.name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public Executor getExecutor() {
            return this.executor;
        }

        public String getExecutorBeanName() {
            return this.executor.getBeanName();
        }

        public void setExecutorBeanName(String executorBeanName) {
            this.executor.setBeanName(executorBeanName);
        }
    }

    /**
     * Worker.
     *
     * @author photowey
     * @version 1.0.0
     * @since 2026/06/26
     */
    public static class Worker {

        private int threads = Runtime.getRuntime().availableProcessors();
        private int queueCapacity = 100_000;
        private String name = "timewheel-worker";
        private final Executor executor = new Executor();

        public int getThreads() {
            return this.threads;
        }

        public void setThreads(int threads) {
            this.threads = threads;
        }

        public int getQueueCapacity() {
            return this.queueCapacity;
        }

        public void setQueueCapacity(int queueCapacity) {
            this.queueCapacity = queueCapacity;
        }

        public String getName() {
            return this.name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public Executor getExecutor() {
            return this.executor;
        }

        public String getExecutorBeanName() {
            return this.executor.getBeanName();
        }

        public void setExecutorBeanName(String executorBeanName) {
            this.executor.setBeanName(executorBeanName);
        }
    }

    /**
     * Executor.
     *
     * @author photowey
     * @version 1.0.0
     * @since 2026/06/26
     */
    public static class Executor {

        private boolean autoCreate = true;
        private String beanName;

        public boolean isAutoCreate() {
            return this.autoCreate;
        }

        public void setAutoCreate(boolean autoCreate) {
            this.autoCreate = autoCreate;
        }

        public String getBeanName() {
            return this.beanName;
        }

        public void setBeanName(String beanName) {
            this.beanName = beanName;
        }
    }
}
