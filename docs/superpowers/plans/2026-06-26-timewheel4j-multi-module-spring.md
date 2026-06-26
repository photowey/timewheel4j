# Timewheel4j Multi-Module Spring Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:
> executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Turn timewheel4j into a Maven reactor with a Java 11 core, BOM, Spring Boot 2.x/3.x starters, CI toolchain
support, and updated developer documentation.

**Architecture:** Keep the Kafka-style hierarchical timing wheel in `timewheel4j-core`. Add a Spring aggregator that
mirrors `mongo-plus`: one shared Spring configuration support module, one Boot 2 starter with starter-owned metadata,
and one Boot 3 starter that switches dependency management and compilation to Java 17. Toolchains are available through
a profile and enabled in CI so local development remains usable when `~/.m2/toolchains.xml` is absent.

**Tech Stack:** Java 11 core, Java 17 Boot 3 starter, Maven multi-module reactor, Spring Boot 2.7.5/3.2.4, JUnit 5,
JaCoCo, JMH, GitHub Actions, Maven Central publishing plugin.

---

### Task 1: Reactor Structure

**Files:**

- Modify: `pom.xml`
- Create: `timewheel4j-core/pom.xml`
- Create: `timewheel4j-bom/pom.xml`
- Create: `timewheel4j-spring/pom.xml`
- Move: `src/main`, `src/test`, `src/jmh` into `timewheel4j-core/`

- [x] Move the current Java 11 core implementation and tests into `timewheel4j-core`.
- [x] Replace the root POM with a packaging `pom` reactor containing `timewheel4j-bom`, `timewheel4j-core`, and
  `timewheel4j-spring`.
- [x] Add parent dependency management for Spring Boot 2.7.5, JUnit 5, JMH, Netty, and all internal artifacts.
- [x] Add a `toolchain` profile using `maven-toolchains-plugin` so CI can require JDK 11/17 without breaking local
  builds lacking `~/.m2/toolchains.xml`.
- [x] Keep benchmark packaging in `timewheel4j-core` under the existing `benchmark` profile.
- [x] Run `mvn -pl timewheel4j-core test` and expect all core JUnit tests to pass.

### Task 2: Spring Boot Modules

**Files:**

- Create: `timewheel4j-spring/timewheel4j-spring-boot-autoconfigure/pom.xml`
- Create:
  `timewheel4j-spring/timewheel4j-spring-boot-autoconfigure/src/main/java/io/github/photowey/timewheel4j/spring/boot/autoconfigure/Timewheel4jProperties.java`
- Create:
  `timewheel4j-spring/timewheel4j-spring-boot-autoconfigure/src/main/java/io/github/photowey/timewheel4j/spring/boot/autoconfigure/AbstractTimewheel4jConfiguration.java`
- Create:
  `timewheel4j-spring/timewheel4j-spring-boot-starter/src/main/java/io/github/photowey/timewheel4j/spring/boot/autoconfigure/Timewheel4jConfiguration.java`
- Create:
  `timewheel4j-spring/timewheel4j-spring-boot-starter/src/main/java/io/github/photowey/timewheel4j/spring/boot/autoconfigure/Timewheel4jAutoConfiguration.java`
- Create: `timewheel4j-spring/timewheel4j-spring-boot-starter/src/main/resources/META-INF/spring.factories`
- Create:
  `timewheel4j-spring/timewheel4j-spring-boot-starter/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`
- Create:
  `timewheel4j-spring/timewheel4j-spring-boot-autoconfigure/src/test/java/io/github/photowey/timewheel4j/spring/boot/autoconfigure/AbstractTimewheel4jConfigurationTest.java`
- Create: `timewheel4j-spring/timewheel4j-spring-boot-starter/pom.xml`
- Create: `timewheel4j-spring/timewheel4j-spring-boot3-starter/pom.xml`
- Create:
  `timewheel4j-spring/timewheel4j-spring-boot3-starter/src/main/java/io/github/photowey/timewheel4j/spring/boot/autoconfigure/Timewheel4jAutoConfiguration.java`
- Create:
  `timewheel4j-spring/timewheel4j-spring-boot3-starter/src/test/java/io/github/photowey/timewheel4j/spring/boot3/starter/Timewheel4jBoot3StarterTest.java`

- [x] Implement `timewheel4j.enabled`, `timewheel4j.tick`, `timewheel4j.wheel-size`, `timewheel4j.worker-threads`,
  `timewheel4j.worker-name`, and `timewheel4j.scheduler-name`.
- [x] Auto-create a `Timer` bean with `destroyMethod = "shutdown"` when enabled and no user bean exists.
- [x] Register auto-configuration metadata in the Boot 2 and Boot 3 starter modules.
- [x] Use `provided` Spring Boot dependencies in autoconfigure so the Boot 3 starter does not pull Boot 2 transitively.
- [x] Make `timewheel4j-spring-boot3-starter` compile with Java 17 and import the Boot 3 dependency BOM.
- [x] Run Spring context tests for default creation, disabled mode, user bean override, custom properties, and Boot 3
  runtime compatibility.

### Task 3: CI, Release, And Docs

**Files:**

- Modify: `.github/workflows/maven.yml`
- Modify: `.github/workflows/maven-central.yml`
- Modify: `README.md`
- Modify: `docs/architecture.md`

- [x] Configure GitHub Actions to install JDK 11 and JDK 17, write `~/.m2/toolchains.xml`, run Maven on JDK 17, and
  execute `mvn -B -Ptoolchain verify`.
- [x] Configure Maven Central workflow with the same toolchains and release profile.
- [x] Update README installation coordinates for core, BOM, Boot 2 starter, and Boot 3 starter.
- [x] Update architecture docs with the new module graph and Spring auto-configuration flow.
- [x] Run `mvn verify`, `mvn -Pbenchmark -pl timewheel4j-core -am -DskipTests package`, and `mvn -Ptoolchain verify`
  when local toolchains are available.
- [x] Run `gitnexus_detect_changes(scope = "all")` before any commit.
