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
package com.photowey.hierarchical.timewheel.engine;

import com.photowey.hierarchical.timewheel.publisher.DefaultEventPublisher;
import com.photowey.hierarchical.timewheel.publisher.EventPublisher;
import com.photowey.hierarchical.timewheel.registry.EventGroupRegistry;
import com.photowey.hierarchical.timewheel.registry.EventRegistry;

/**
 * {@code TimeWheelEngine}
 *
 * @author photowey
 * @date 2023/04/05
 * @since 1.0.0
 */
public class TimeWheelEngine implements Engine {

    private EventRegistry eventRegistry;
    private EventPublisher eventPublisher;

    public TimeWheelEngine() {
        this.eventRegistry = new EventGroupRegistry();
        this.eventPublisher = new DefaultEventPublisher();
    }

    public static class TimeWheelEngineHolder {
        private static final TimeWheelEngine INSTANCE = new TimeWheelEngine();
    }

    public static TimeWheelEngine getInstance() {
        return TimeWheelEngineHolder.INSTANCE;
    }

    public EventRegistry eventRegistry() {
        return eventRegistry;
    }

    public EventPublisher eventPublisher() {
        return eventPublisher;
    }
}
