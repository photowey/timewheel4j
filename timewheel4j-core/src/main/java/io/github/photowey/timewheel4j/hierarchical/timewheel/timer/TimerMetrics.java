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

/**
 * Immutable point-in-time metrics for a {@link Timer}.
 *
 * @author photowey
 * @version 1.0.0
 * @since 2026/06/26
 */
public final class TimerMetrics {

    private final long scheduledTimeouts;
    private final long expiredTimeouts;
    private final long cancelledTimeouts;
    private final long rejectedTimeouts;
    private final long pendingTimeouts;
    private final long bucketOffers;
    private final long bucketExpirations;
    private final long maxBucketDelayMs;

    TimerMetrics(
        long scheduledTimeouts,
        long expiredTimeouts,
        long cancelledTimeouts,
        long rejectedTimeouts,
        long pendingTimeouts,
        long bucketOffers,
        long bucketExpirations,
        long maxBucketDelayMs) {
        this.scheduledTimeouts = scheduledTimeouts;
        this.expiredTimeouts = expiredTimeouts;
        this.cancelledTimeouts = cancelledTimeouts;
        this.rejectedTimeouts = rejectedTimeouts;
        this.pendingTimeouts = pendingTimeouts;
        this.bucketOffers = bucketOffers;
        this.bucketExpirations = bucketExpirations;
        this.maxBucketDelayMs = maxBucketDelayMs;
    }

    public long scheduledTimeouts() {
        return this.scheduledTimeouts;
    }

    public long expiredTimeouts() {
        return this.expiredTimeouts;
    }

    public long cancelledTimeouts() {
        return this.cancelledTimeouts;
    }

    public long rejectedTimeouts() {
        return this.rejectedTimeouts;
    }

    public long pendingTimeouts() {
        return this.pendingTimeouts;
    }

    public long bucketOffers() {
        return this.bucketOffers;
    }

    public long bucketExpirations() {
        return this.bucketExpirations;
    }

    public long maxBucketDelayMs() {
        return this.maxBucketDelayMs;
    }
}
