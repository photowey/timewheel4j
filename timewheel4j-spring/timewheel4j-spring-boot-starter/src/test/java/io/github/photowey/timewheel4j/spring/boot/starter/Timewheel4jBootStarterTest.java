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
package io.github.photowey.timewheel4j.spring.boot.starter;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

import io.github.photowey.timewheel4j.hierarchical.timewheel.timer.Timer;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Timewheel4jBootStarterTest.
 *
 * @author photowey
 * @version 1.0.0
 * @since 2026/06/26
 */
class Timewheel4jBootStarterTest {

    private static final String CONFIGURATION_CLASS_NAME =
        "io.github.photowey.timewheel4j.spring.boot.autoconfigure.Timewheel4jConfiguration";
    private static final String AUTO_CONFIGURATION_CLASS_NAME =
        "io.github.photowey.timewheel4j.spring.boot.autoconfigure.Timewheel4jAutoConfiguration";

    private Class<?> configurationClass;
    private Class<?> autoConfigurationClass;
    private ApplicationContextRunner contextRunner;

    @BeforeEach
    void setup() throws ClassNotFoundException {
        this.configurationClass = Class.forName(CONFIGURATION_CLASS_NAME);
        this.autoConfigurationClass = Class.forName(AUTO_CONFIGURATION_CLASS_NAME);
        this.contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(this.autoConfigurationClass));
    }

    @Test
    void givenBootStarterWhenConfigurationIsLoadedThenItUsesConfigurationAnnotation() {
        assertThat(this.configurationClass.getAnnotation(Configuration.class)).isNotNull();
    }

    @Test
    void givenBootStarterWhenAutoConfigurationIsLoadedThenItUsesAutoConfigurationAnnotation() {
        assertThat(this.autoConfigurationClass.getAnnotation(AutoConfiguration.class)).isNotNull();
    }

    @Test
    void givenBootStarterWhenMetadataIsReadThenStarterOwnsEntries() throws IOException {
        String factories = resource("META-INF/spring.factories");
        String imports = resource("META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports");

        assertThat(factories).contains(CONFIGURATION_CLASS_NAME);
        assertThat(imports).contains(AUTO_CONFIGURATION_CLASS_NAME);
    }

    @Test
    void givenBootRuntimeWhenContextRunsThenTimerBeanIsCreated() {
        this.contextRunner
            .withPropertyValues("timewheel4j.worker-threads=1")
            .run((context) -> assertThat(context).hasSingleBean(Timer.class));
    }

    private static String resource(String name) throws IOException {
        try (InputStream input = Timewheel4jBootStarterTest.class.getClassLoader().getResourceAsStream(name)) {
            assertThat(input).isNotNull();
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
