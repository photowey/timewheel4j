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
import com.photowey.hierarchical.timewheel.core.event.ScheduledTaskEvent;
import com.photowey.hierarchical.timewheel.core.hardware.HardwareUtils;
import com.photowey.hierarchical.timewheel.core.shared.io.netty.util.concurrent.DefaultThreadFactory;

import java.util.concurrent.*;

/**
 * {@code DefaultWorkerGroup}
 *
 * @author photowey
 * @date 2023/04/05
 * @since 1.0.0
 */
public class DefaultWorkerGroup implements WorkerGroup {

    private static final int corePoolSize = HardwareUtils.getNcpu();
    private static final int maximumPoolSize = HardwareUtils.getDoubleNcpu();
    private static final long keepAliveTime = 5000L;
    private static final String DEFAULT_POOL_NAME = "timewheel";

    private final ExecutorService executorService;

    public DefaultWorkerGroup() {
        this(corePoolSize);
    }

    public DefaultWorkerGroup(int corePoolSize) {
        this(corePoolSize, maximumPoolSize);
    }

    public DefaultWorkerGroup(int corePoolSize, String poolName) {
        this(corePoolSize, maximumPoolSize, keepAliveTime, poolName);
    }

    public DefaultWorkerGroup(
            int corePoolSize,
            int maximumPoolSize) {
        this(corePoolSize, maximumPoolSize, keepAliveTime);
    }

    public DefaultWorkerGroup(
            int corePoolSize,
            int maximumPoolSize,
            long keepAliveTime) {
        this(corePoolSize, maximumPoolSize, keepAliveTime, DEFAULT_POOL_NAME);
    }

    public DefaultWorkerGroup(
            int corePoolSize,
            int maximumPoolSize,
            long keepAliveTime,
            String poolName) {
        this(corePoolSize, maximumPoolSize, keepAliveTime, new DefaultThreadFactory(poolName));
    }

    public DefaultWorkerGroup(
            int corePoolSize,
            int maximumPoolSize,
            long keepAliveTime,
            ThreadFactory threadFactory) {
        this(corePoolSize, maximumPoolSize, keepAliveTime,
                new LinkedBlockingQueue<>(), threadFactory,
                new ThreadPoolExecutor.CallerRunsPolicy());
    }

    public DefaultWorkerGroup(
            int corePoolSize,
            int maximumPoolSize,
            long keepAliveTime,
            BlockingQueue<Runnable> workQueue,
            ThreadFactory threadFactory) {
        this(corePoolSize, maximumPoolSize,
                keepAliveTime, workQueue, threadFactory, new ThreadPoolExecutor.CallerRunsPolicy());
    }

    public DefaultWorkerGroup(
            int corePoolSize,
            int maximumPoolSize,
            long keepAliveTime,
            BlockingQueue<Runnable> workQueue,
            ThreadFactory threadFactory,
            RejectedExecutionHandler handler) {
        this(new ThreadPoolExecutor(
                corePoolSize,
                maximumPoolSize,
                keepAliveTime,
                TimeUnit.MILLISECONDS,
                workQueue,
                threadFactory,
                handler
        ));
    }

    public DefaultWorkerGroup(ExecutorService executorService) {
        this.executorService = executorService;
    }

    @Override
    public void handleEvent(Event event) {
        if (event instanceof ScheduledTaskEvent) {
            ScheduledTaskEvent taskEvent = (ScheduledTaskEvent) event;
            this.executorService.execute(taskEvent);
        }
    }
}
