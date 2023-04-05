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
package com.photowey.hierarchical.timewheel.publisher;

import com.photowey.hierarchical.timewheel.core.event.Event;
import com.photowey.hierarchical.timewheel.engine.TimeWheelEngine;
import com.photowey.hierarchical.timewheel.group.EventGroup;
import com.photowey.hierarchical.timewheel.registry.EventRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

import static com.photowey.hierarchical.timewheel.core.fx.Functions.checkNotNull;

/**
 * {@code DefaultEventPublisher}
 *
 * @author photowey
 * @date 2023/04/05
 * @since 1.0.0
 */
public class DefaultEventPublisher implements EventPublisher {

    private static final Logger log = LoggerFactory.getLogger(DefaultEventPublisher.class);

    @Override
    public void publishEvent(Event event) {
        checkNotNull(event, "event");

        this.preLog(event);

        EventRegistry registry = TimeWheelEngine.getInstance().eventRegistry();
        List<EventGroup> eventGroups = registry.acquireEventGroup(event.topic());
        for (EventGroup eventGroup : eventGroups) {
            eventGroup.handleEvent(event);
        }

        this.posLog(event, eventGroups);
    }

    private void preLog(Event even) {
        log.info("Prepare publish event:[{}]", even.topic());
    }

    private void posLog(Event event, List<EventGroup> eventGroups) {
        log.info("Post publish event:[{}],successfully...,the handler size:[{}]", event.topic(), eventGroups.size());
    }
}
