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
package io.github.photowey.timewheel4j.hierarchical.timewheel.timer;

import java.util.Optional;
import java.util.concurrent.DelayQueue;
import java.util.concurrent.TimeUnit;

/**
 * BucketDelayQueue.
 *
 * @author photowey
 * @version 1.0.0
 * @since 2026/06/26
 */
final class BucketDelayQueue {

    private final DelayQueue<TimerTaskList> delegate = new DelayQueue<>();

    void offer(TimerTaskList bucket) {
        this.delegate.offer(bucket);
    }

    Optional<TimerTaskList> poll(long timeout, TimeUnit unit) throws InterruptedException {
        return Optional.ofNullable(this.delegate.poll(timeout, unit));
    }

    Optional<TimerTaskList> poll() {
        return Optional.ofNullable(this.delegate.poll());
    }

    Optional<TimerTaskList> peek() {
        return Optional.ofNullable(this.delegate.peek());
    }

    int size() {
        return this.delegate.size();
    }
}
