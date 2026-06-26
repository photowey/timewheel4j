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

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.github.photowey.timewheel4j.hierarchical.timewheel.timer.SystemTimer;
import io.github.photowey.timewheel4j.hierarchical.timewheel.timer.Timeout;
import io.github.photowey.timewheel4j.hierarchical.timewheel.timer.Timer;
import io.github.photowey.timewheel4j.hierarchical.timewheel.timer.TimerBuilder;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * AbstractTimewheel4jConfigurationTest.
 *
 * @author photowey
 * @version 1.0.0
 * @since 2026/06/26
 */
class AbstractTimewheel4jConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(TestTimewheel4jConfiguration.class));

    @Test
    void givenDefaultPropertiesWhenContextRunsThenTimerBeanIsCreated() {
        this.contextRunner.run((context) -> {
            assertThat(context).hasSingleBean(Timer.class);
            assertThat(context).hasSingleBean(Timewheel4jProperties.class);
        });
    }

    @Test
    void givenDefaultPropertiesWhenContextRunsThenBossAndWorkerExecutorsAreAutoCreated() {
        this.contextRunner
            .withPropertyValues("timewheel4j.worker.threads=1")
            .run((context) -> {
                Timer timer = context.getBean(Timer.class);
                ExecutorService bossExecutor =
                    context.getBean("timewheel4jBossExecutor", ExecutorService.class);
                ExecutorService workerExecutor =
                    context.getBean("timewheel4jWorkerExecutor", ExecutorService.class);

                assertThat(actualExecutor(timer, "boss")).isSameAs(bossExecutor);
                assertThat(actualExecutor(timer, "workers")).isSameAs(workerExecutor);
                assertThat(workerExecutor).isInstanceOf(ThreadPoolExecutor.class);
                assertThat(((ThreadPoolExecutor) workerExecutor).getCorePoolSize()).isEqualTo(1);
                assertThat(((ThreadPoolExecutor) workerExecutor).getQueue().remainingCapacity())
                    .isEqualTo(100_000);
            });
    }

    @Test
    void givenDisabledPropertyWhenContextRunsThenTimerBeanIsNotCreated() {
        this.contextRunner
            .withPropertyValues("timewheel4j.enabled=false")
            .run((context) -> {
                assertThat(context).doesNotHaveBean(Timer.class);
                assertThat(context).doesNotHaveBean("timewheel4jBossExecutor");
                assertThat(context).doesNotHaveBean("timewheel4jWorkerExecutor");
            });
    }

    @Test
    void givenUserTimerWhenContextRunsThenAutoConfiguredTimerBacksOff() {
        this.contextRunner
            .withUserConfiguration(UserTimerConfiguration.class)
            .run((context) -> {
                assertThat(context).hasSingleBean(Timer.class);
                assertThat(context.getBean(Timer.class)).isSameAs(UserTimerConfiguration.TIMER);
                assertThat(context).doesNotHaveBean("timewheel4jBossExecutor");
                assertThat(context).doesNotHaveBean("timewheel4jWorkerExecutor");
            });
    }

    @Test
    void givenCustomPropertiesWhenContextRunsThenTimerUsesConfiguredValues() {
        this.contextRunner
            .withPropertyValues(
                "timewheel4j.tick=5ms",
                "timewheel4j.wheel-size=64",
                "timewheel4j.worker-threads=1",
                "timewheel4j.worker-queue-capacity=16",
                "timewheel4j.worker-name=tw-worker",
                "timewheel4j.scheduler-name=tw-scheduler")
            .run((context) -> {
                assertThat(context).hasSingleBean(Timer.class);
                Timewheel4jProperties properties = context.getBean(Timewheel4jProperties.class);

                assertThat(properties.getTick().toMillis()).isEqualTo(5L);
                assertThat(properties.getWheelSize()).isEqualTo(64);
                assertThat(properties.getWorkerThreads()).isEqualTo(1);
                assertThat(properties.getWorkerQueueCapacity()).isEqualTo(16);
                assertThat(properties.getWorkerName()).isEqualTo("tw-worker");
                assertThat(properties.getSchedulerName()).isEqualTo("tw-scheduler");
                assertThat(properties.getBoss().getName()).isEqualTo("tw-scheduler");
                assertThat(properties.getWorker().getThreads()).isEqualTo(1);
                assertThat(properties.getWorker().getQueueCapacity()).isEqualTo(16);
                assertThat(properties.getWorker().getName()).isEqualTo("tw-worker");
            });
    }

    @Test
    void givenNestedExecutionPropertiesWhenContextRunsThenTimerUsesConfiguredValues() {
        this.contextRunner
            .withPropertyValues(
                "timewheel4j.boss.name=tw-boss",
                "timewheel4j.worker.threads=1",
                "timewheel4j.worker.queue-capacity=32",
                "timewheel4j.worker.name=tw-worker")
            .run((context) -> {
                assertThat(context).hasSingleBean(Timer.class);
                Timewheel4jProperties properties = context.getBean(Timewheel4jProperties.class);

                assertThat(properties.getBoss().getName()).isEqualTo("tw-boss");
                assertThat(properties.getWorker().getThreads()).isEqualTo(1);
                assertThat(properties.getWorker().getQueueCapacity()).isEqualTo(32);
                assertThat(properties.getWorker().getName()).isEqualTo("tw-worker");
            });
    }

    @Test
    void givenExecutorBeanNamesWhenContextRunsThenAutoConfiguredTimerUsesExternalExecutors() {
        this.contextRunner
            .withUserConfiguration(ExecutorConfiguration.class)
            .withPropertyValues(
                "timewheel4j.boss.executor.bean-name=timewheel4jBossExecutor",
                "timewheel4j.worker.executor.bean-name=timewheel4jWorkerExecutor")
            .run((context) -> {
                Timer timer = context.getBean(Timer.class);
                ExecutorService bossExecutor = context.getBean("timewheel4jBossExecutor", ExecutorService.class);
                ExecutorService workerExecutor = context.getBean("timewheel4jWorkerExecutor", ExecutorService.class);
                CountDownLatch latch = new CountDownLatch(1);
                AtomicReference<String> workerThreadName = new AtomicReference<>();
                Timewheel4jProperties properties = context.getBean(Timewheel4jProperties.class);

                assertThat(properties.getBoss().getExecutor().getBeanName())
                    .isEqualTo("timewheel4jBossExecutor");
                assertThat(properties.getWorker().getExecutor().getBeanName())
                    .isEqualTo("timewheel4jWorkerExecutor");
                assertThat(actualExecutor(timer, "boss")).isSameAs(bossExecutor);
                assertThat(actualExecutor(timer, "workers")).isSameAs(workerExecutor);

                timer.schedule(() -> {
                    workerThreadName.set(Thread.currentThread().getName());
                    latch.countDown();
                }, 0, TimeUnit.MILLISECONDS);

                assertThat(latch.await(1, TimeUnit.SECONDS)).isTrue();
                assertThat(workerThreadName.get()).startsWith("spring-worker-");

                timer.shutdown();
                assertThat(bossExecutor.isShutdown()).isFalse();
                assertThat(workerExecutor.isShutdown()).isFalse();
                bossExecutor.shutdownNow();
                workerExecutor.shutdownNow();
            });
    }

    @Test
    void givenExecutorBeanNamesWhenContextRunsThenDefaultExecutorsAreNotAutoCreated() {
        this.contextRunner
            .withUserConfiguration(NamedExecutorConfiguration.class)
            .withPropertyValues(
                "timewheel4j.boss.executor-bean-name=customBossExecutor",
                "timewheel4j.worker.executor-bean-name=customWorkerExecutor")
            .run((context) -> {
                Timer timer = context.getBean(Timer.class);
                ExecutorService bossExecutor = context.getBean("customBossExecutor", ExecutorService.class);
                ExecutorService workerExecutor = context.getBean("customWorkerExecutor", ExecutorService.class);
                Timewheel4jProperties properties = context.getBean(Timewheel4jProperties.class);

                assertThat(context).doesNotHaveBean("timewheel4jBossExecutor");
                assertThat(context).doesNotHaveBean("timewheel4jWorkerExecutor");
                assertThat(properties.getBoss().getExecutor().getBeanName()).isEqualTo("customBossExecutor");
                assertThat(properties.getWorker().getExecutor().getBeanName()).isEqualTo("customWorkerExecutor");
                assertThat(actualExecutor(timer, "boss")).isSameAs(bossExecutor);
                assertThat(actualExecutor(timer, "workers")).isSameAs(workerExecutor);
            });
    }

    @Test
    void givenExecutorAutoCreateDisabledWhenContextRunsThenTimerUsesCoreOwnedExecutors() {
        this.contextRunner
            .withPropertyValues(
                "timewheel4j.boss.executor.auto-create=false",
                "timewheel4j.worker.executor.auto-create=false",
                "timewheel4j.worker.threads=1")
            .run((context) -> {
                Timer timer = context.getBean(Timer.class);

                assertThat(context).doesNotHaveBean("timewheel4jBossExecutor");
                assertThat(context).doesNotHaveBean("timewheel4jWorkerExecutor");
                assertThat(actualExecutor(timer, "boss")).isNotNull();
                assertThat(actualExecutor(timer, "workers")).isInstanceOf(ThreadPoolExecutor.class);
            });
    }

    @Test
    void givenInvalidTickWhenContextRunsThenStartupFails() {
        this.contextRunner
            .withPropertyValues("timewheel4j.tick=1ns")
            .run((context) -> assertThat(context).hasFailed());
    }

    @Test
    void givenAutoConfiguredTimerWhenTaskIsScheduledThenTimeoutIsReturned() {
        this.contextRunner
            .withPropertyValues("timewheel4j.worker-threads=1")
            .run((context) -> {
                Timer timer = context.getBean(Timer.class);
                Timeout timeout = timer.schedule(() -> {
                }, 1, TimeUnit.SECONDS);

                assertThat(timeout.isCancelled()).isFalse();
                assertThat(timeout.cancel()).isTrue();
            });
    }

    private static ExecutorService actualExecutor(Timer timer, String fieldName) {
        assertThat(timer).isInstanceOf(SystemTimer.class);
        try {
            var field = SystemTimer.class.getDeclaredField(fieldName);
            field.setAccessible(true);
            return (ExecutorService) field.get(timer);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("Unable to read SystemTimer executor field: " + fieldName, e);
        }
    }

    /**
     * UserTimerConfiguration.
     *
     * @author photowey
     * @version 1.0.0
     * @since 2026/06/26
     */
    @Configuration(proxyBeanMethods = false)
    static class UserTimerConfiguration {

        static final Timer TIMER = TimerBuilder.builder()
            .workerThreads(1)
            .workerName("user-timewheel-worker")
            .schedulerName("user-timewheel-scheduler")
            .build();

        @Bean(destroyMethod = "shutdown")
        Timer userTimer() {
            return TIMER;
        }
    }

    /**
     * ExecutorConfiguration.
     *
     * @author photowey
     * @version 1.0.0
     * @since 2026/06/26
     */
    @Configuration(proxyBeanMethods = false)
    static class ExecutorConfiguration {

        @Bean(destroyMethod = "")
        ExecutorService timewheel4jBossExecutor() {
            return Executors.newSingleThreadExecutor((task) -> {
                Thread thread = new Thread(task);
                thread.setName("spring-boss");
                thread.setDaemon(true);
                return thread;
            });
        }

        @Bean(destroyMethod = "")
        ExecutorService timewheel4jWorkerExecutor() {
            return Executors.newSingleThreadExecutor((task) -> {
                Thread thread = new Thread(task);
                thread.setName("spring-worker-1");
                thread.setDaemon(true);
                return thread;
            });
        }
    }

    /**
     * NamedExecutorConfiguration.
     *
     * @author photowey
     * @version 1.0.0
     * @since 2026/06/26
     */
    @Configuration(proxyBeanMethods = false)
    static class NamedExecutorConfiguration {

        @Bean(destroyMethod = "")
        ExecutorService customBossExecutor() {
            return Executors.newSingleThreadExecutor((task) -> {
                Thread thread = new Thread(task);
                thread.setName("custom-boss");
                thread.setDaemon(true);
                return thread;
            });
        }

        @Bean(destroyMethod = "")
        ExecutorService customWorkerExecutor() {
            return Executors.newSingleThreadExecutor((task) -> {
                Thread thread = new Thread(task);
                thread.setName("custom-worker");
                thread.setDaemon(true);
                return thread;
            });
        }
    }

    /**
     * TestTimewheel4jConfiguration.
     *
     * @author photowey
     * @version 1.0.0
     * @since 2026/06/26
     */
    @Configuration(proxyBeanMethods = false)
    static class TestTimewheel4jConfiguration extends AbstractTimewheel4jConfiguration {
    }
}
