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
package com.photowey.hierarchical.timewheel.core.fx;

import com.photowey.hierarchical.timewheel.core.exception.BlankStringException;

/**
 * {@code Functions}
 *
 * @author photowey
 * @date 2023/04/05
 * @since 1.0.0
 */
public interface Functions {

    long LONG_ZERO = 0L;

    static String checkNotBlank(String arg, String text) {
        if (arg == null || arg.trim().length() == 0) {
            throw new BlankStringException(text);
        }

        return arg;
    }

    static <T> T checkNotNull(T arg, String text) {
        if (arg == null) {
            throw new NullPointerException(text);
        }
        return arg;
    }

    static int checkInRange(int i, int start, int end, String name) {
        if (i < start || i > end) {
            throw new IllegalArgumentException(name + ": " + i + " (expected: " + start + "-" + end + ")");
        }
        return i;
    }

    static long checkPositive(long l, String name) {
        if (l <= LONG_ZERO) {
            throw new IllegalArgumentException(name + " : " + l + " (expected: > 0)");
        }
        return l;
    }

    static void checkTure(boolean expression, String message) {
        if (!expression) {
            throw new IllegalArgumentException(message);
        }
    }
}
