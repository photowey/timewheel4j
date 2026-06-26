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
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.beans.factory.BeanFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.context.annotation.Conditional;
import org.springframework.core.env.Environment;
import org.springframework.core.type.AnnotatedTypeMetadata;

import io.github.photowey.timewheel4j.hierarchical.timewheel.timer.Timer;
import io.github.photowey.timewheel4j.hierarchical.timewheel.timer.TimerBuilder;

/**
 * Shared timewheel4j bean definitions for Spring Boot starters.
 *
 * @author photowey
 * @version 1.0.0
 * @since 2026/06/26
 */
@ConditionalOnClass(Timer.class)
@EnableConfigurationProperties(Timewheel4jProperties.class)
public abstract class AbstractTimewheel4jConfiguration {

    static final String BOSS_EXECUTOR_BEAN_NAME = "timewheel4jBossExecutor";
    static final String WORKER_EXECUTOR_BEAN_NAME = "timewheel4jWorkerExecutor";

    @Bean(name = BOSS_EXECUTOR_BEAN_NAME, destroyMethod = "shutdown")
    @ConditionalOnMissingBean(value = Timer.class, name = BOSS_EXECUTOR_BEAN_NAME)
    @ConditionalOnProperty(prefix = "timewheel4j", name = "enabled", havingValue = "true", matchIfMissing = true)
    @Conditional(BossExecutorAutoCreateCondition.class)
    public ExecutorService timewheel4jBossExecutor(Timewheel4jProperties properties) {
        return Executors.newSingleThreadExecutor(threadFactory(properties.getBoss().getName()));
    }

    @Bean(name = WORKER_EXECUTOR_BEAN_NAME, destroyMethod = "shutdown")
    @ConditionalOnMissingBean(value = Timer.class, name = WORKER_EXECUTOR_BEAN_NAME)
    @ConditionalOnProperty(prefix = "timewheel4j", name = "enabled", havingValue = "true", matchIfMissing = true)
    @Conditional(WorkerExecutorAutoCreateCondition.class)
    public ExecutorService timewheel4jWorkerExecutor(Timewheel4jProperties properties) {
        int workerThreads = this.workerThreads(properties.getWorker().getThreads());
        int queueCapacity = this.workerQueueCapacity(properties.getWorker().getQueueCapacity());

        return new ThreadPoolExecutor(
            workerThreads,
            workerThreads,
            0L,
            TimeUnit.MILLISECONDS,
            workerQueue(queueCapacity),
            threadFactory(properties.getWorker().getName()),
            new ThreadPoolExecutor.AbortPolicy());
    }

    @Bean(destroyMethod = "shutdown")
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "timewheel4j", name = "enabled", havingValue = "true", matchIfMissing = true)
    public Timer timewheel4jTimer(Timewheel4jProperties properties, BeanFactory beanFactory) {
        TimerBuilder builder = TimerBuilder.builder()
            .tick(this.tickMillis(properties.getTick()), TimeUnit.MILLISECONDS)
            .wheelSize(properties.getWheelSize())
            .bossName(properties.getBoss().getName())
            .workerThreads(this.workerThreads(properties.getWorker().getThreads()))
            .workerQueueCapacity(this.workerQueueCapacity(properties.getWorker().getQueueCapacity()))
            .workerName(properties.getWorker().getName());

        ExecutorService bossExecutor = this.executor(
            beanFactory,
            properties.getBoss().getExecutorBeanName(),
            properties.getBoss().getExecutor().isAutoCreate(),
            BOSS_EXECUTOR_BEAN_NAME);
        if (bossExecutor != null) {
            builder.bossExecutor(bossExecutor);
        }

        ExecutorService workerExecutor = this.executor(
            beanFactory,
            properties.getWorker().getExecutorBeanName(),
            properties.getWorker().getExecutor().isAutoCreate(),
            WORKER_EXECUTOR_BEAN_NAME);
        if (workerExecutor != null) {
            builder.workerExecutor(workerExecutor);
        }

        return builder.build();
    }

    private long tickMillis(Duration tick) {
        if (tick == null) {
            throw new IllegalArgumentException("timewheel4j.tick must not be null");
        }
        long tickMs = tick.toMillis();
        if (tickMs <= 0) {
            throw new IllegalArgumentException("timewheel4j.tick must be at least 1 millisecond");
        }

        return tickMs;
    }

    private int workerThreads(int workerThreads) {
        if (workerThreads <= 0) {
            throw new IllegalArgumentException("timewheel4j.worker.threads must be positive");
        }

        return workerThreads;
    }

    private int workerQueueCapacity(int workerQueueCapacity) {
        if (workerQueueCapacity <= 0) {
            throw new IllegalArgumentException("timewheel4j.worker.queue-capacity must be positive");
        }

        return workerQueueCapacity;
    }

    private ExecutorService executor(
        BeanFactory beanFactory,
        String beanName,
        boolean autoCreate,
        String defaultBeanName) {
        if (hasText(beanName)) {
            return beanFactory.getBean(beanName, ExecutorService.class);
        }
        if (autoCreate && beanFactory.containsBean(defaultBeanName)) {
            return beanFactory.getBean(defaultBeanName, ExecutorService.class);
        }

        return null;
    }

    private static ThreadFactory threadFactory(String prefix) {
        AtomicInteger sequence = new AtomicInteger();
        return (runnable) -> {
            Thread thread = new Thread(runnable, prefix + '-' + sequence.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        };
    }

    private static BlockingQueue<Runnable> workerQueue(int capacity) {
        if (capacity == Integer.MAX_VALUE) {
            return new LinkedBlockingQueue<>();
        }
        return new LinkedBlockingQueue<>(capacity);
    }

    private static boolean hasText(String candidate) {
        return candidate != null && !candidate.isBlank();
    }

    /**
     * BossExecutorAutoCreateCondition.
     *
     * @author photowey
     * @version 1.0.0
     * @since 2026/06/26
     */
    public static final class BossExecutorAutoCreateCondition extends ExecutorAutoCreateCondition {

        public BossExecutorAutoCreateCondition() {
            super(
                new String[] {
                    "timewheel4j.boss.executor.auto-create",
                    "timewheel4j.boss.executor.autoCreate"
                },
                new String[] {
                    "timewheel4j.boss.executor.bean-name",
                    "timewheel4j.boss.executor.beanName",
                    "timewheel4j.boss.executor-bean-name",
                    "timewheel4j.boss.executorBeanName"
                });
        }
    }

    /**
     * WorkerExecutorAutoCreateCondition.
     *
     * @author photowey
     * @version 1.0.0
     * @since 2026/06/26
     */
    public static final class WorkerExecutorAutoCreateCondition extends ExecutorAutoCreateCondition {

        public WorkerExecutorAutoCreateCondition() {
            super(
                new String[] {
                    "timewheel4j.worker.executor.auto-create",
                    "timewheel4j.worker.executor.autoCreate"
                },
                new String[] {
                    "timewheel4j.worker.executor.bean-name",
                    "timewheel4j.worker.executor.beanName",
                    "timewheel4j.worker.executor-bean-name",
                    "timewheel4j.worker.executorBeanName"
                });
        }
    }

    /**
     * ExecutorAutoCreateCondition.
     *
     * @author photowey
     * @version 1.0.0
     * @since 2026/06/26
     */
    abstract static class ExecutorAutoCreateCondition implements Condition {

        private final String[] autoCreateKeys;
        private final String[] beanNameKeys;

        ExecutorAutoCreateCondition(String[] autoCreateKeys, String[] beanNameKeys) {
            this.autoCreateKeys = autoCreateKeys;
            this.beanNameKeys = beanNameKeys;
        }

        @Override
        public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
            Environment environment = context.getEnvironment();
            return this.isAutoCreateEnabled(environment) && !this.hasExplicitBeanName(environment);
        }

        private boolean isAutoCreateEnabled(Environment environment) {
            for (String key : this.autoCreateKeys) {
                String value = environment.getProperty(key);
                if (value != null) {
                    return Boolean.parseBoolean(value);
                }
            }

            return true;
        }

        private boolean hasExplicitBeanName(Environment environment) {
            for (String key : this.beanNameKeys) {
                if (hasText(environment.getProperty(key))) {
                    return true;
                }
            }

            return false;
        }
    }
}
