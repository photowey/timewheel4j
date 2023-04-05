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
package com.photowey.hierarchical.timewheel.registry;

import com.photowey.hierarchical.timewheel.group.EventGroup;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import static com.photowey.hierarchical.timewheel.core.fx.Functions.checkNotBlank;
import static com.photowey.hierarchical.timewheel.core.fx.Functions.checkNotNull;

/**
 * {@code EventGroupRegistry}
 *
 * @author photowey
 * @date 2023/04/05
 * @since 1.0.0
 */
public class EventGroupRegistry implements EventRegistry {

    private final ConcurrentHashMap<String, List<EventGroup>> ctx = new ConcurrentHashMap<>(4);

    @Override
    public void register(String topic, EventGroup group) {
        checkNotBlank(topic, "topic");
        checkNotNull(group, "group");

        if (this.ctx.contains(topic)) {
            this.ctx.get(topic).add(group);

            return;
        }

        List<EventGroup> eventGroups = new ArrayList<>();
        eventGroups.add(group);
        this.ctx.put(topic, eventGroups);
    }

    @Override
    public List<EventGroup> acquireEventGroup(String topic) {
        List<EventGroup> eventGroups = this.ctx.get(topic);
        return Optional.ofNullable(eventGroups).orElse(new ArrayList<>(0));
    }

    @Override
    public void clean() {
        this.ctx.clear();
    }

}
