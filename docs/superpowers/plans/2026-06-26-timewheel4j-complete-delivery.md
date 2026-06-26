# Timewheel4j Complete Delivery Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:
> executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Complete the current delivery slice for a Kafka-style hierarchical timing wheel: deterministic testing hooks,
runtime metrics, benchmark/stress suites, and updated developer documentation.

**Architecture:** Keep the scheduling core compact and Kafka-shaped: tasks stay in wheel buckets, `DelayQueue` stores
buckets, and scheduler flushes buckets back through the wheel until entries are due. Add a small package-private `Clock`
abstraction for deterministic unit tests and a public immutable metrics snapshot exposed from `Timer` without adding
heavy dependencies.

**Tech Stack:** Java 11, Maven, JUnit 5, JaCoCo, JMH 1.37, GitHub Actions.

---

## File Structure

- Create: `timewheel4j-core/src/main/java/io/github/photowey/timewheel4j/hierarchical/timewheel/timer/Clock.java`
    - Package-private wall-clock abstraction used by `SystemTimer`, `TimingWheel`, and `TimerTaskList`.
- Create: `timewheel4j-core/src/main/java/io/github/photowey/timewheel4j/hierarchical/timewheel/timer/TimerMetrics.java`
    - Public immutable snapshot of counters: scheduled, expired, cancelled, rejected, pending, bucket offers, bucket
      expirations, max bucket delay.
- Modify: `timewheel4j-core/src/main/java/io/github/photowey/timewheel4j/hierarchical/timewheel/timer/Timer.java`
    - Add `metrics()` default surface to expose `TimerMetrics`.
- Modify: `timewheel4j-core/src/main/java/io/github/photowey/timewheel4j/hierarchical/timewheel/timer/TimerBuilder.java`
    - Wire the default system clock into `SystemTimer`.
- Modify: `timewheel4j-core/src/main/java/io/github/photowey/timewheel4j/hierarchical/timewheel/timer/SystemTimer.java`
    - Use `Clock` for deadlines, update metrics counters, expose `metrics()`.
- Modify: `timewheel4j-core/src/main/java/io/github/photowey/timewheel4j/hierarchical/timewheel/timer/TimingWheel.java`
    - Use `Clock` in bucket lists and increment bucket-offer metrics.
- Modify: `timewheel4j-core/src/main/java/io/github/photowey/timewheel4j/hierarchical/timewheel/timer/TimerTaskEntry.java`
    - Distinguish cancellation completion from expiration completion so metrics can count the winning terminal state.
- Modify: `timewheel4j-core/src/main/java/io/github/photowey/timewheel4j/hierarchical/timewheel/timer/TimerTaskList.java`
    - Use `Clock` in delay calculation.
- Create: `timewheel4j-core/src/test/java/io/github/photowey/timewheel4j/hierarchical/timewheel/timer/ManualClock.java`
    - Deterministic test clock.
- Modify tests in `timewheel4j-core/src/test/java/io/github/photowey/timewheel4j/hierarchical/timewheel/timer/`
    - Add Given-When-Then tests for deterministic timing and metrics.
- Modify: `timewheel4j-core/src/jmh/java/io/github/photowey/timewheel4j/hierarchical/timewheel/benchmark/TimerBenchmark.java`
    - Add multi-producer and million-task-ready benchmark parameters.
- Create: `timewheel4j-core/src/jmh/java/io/github/photowey/timewheel4j/hierarchical/timewheel/benchmark/TimerStressBenchmark.java`
    - Long-running stress-oriented JMH suites separated from smoke defaults.
- Modify: `README.md`
    - Document metrics, benchmark commands, stress commands, and remove completed roadmap items.
- Modify: `docs/architecture.md`
    - Update architecture and sequence diagrams with clock and metrics.

## Tasks

### Task 1: Deterministic Clock

- [x] Write failing tests that build `TimingWheel` and `TimerTaskList` with a manual clock.
- [x] Run targeted tests and confirm compilation fails because `Clock`/manual-clock constructors do not exist.
- [x] Add package-private `Clock` and thread it through `TimerTaskList`, `TimingWheel`, and `SystemTimer`.
- [x] Run targeted tests and confirm deterministic clock behavior passes.

### Task 2: Timer Metrics

- [x] Write failing tests for metrics after schedule, cancel, expire, bucket offer, bucket expiration, and shutdown
  rejection.
- [x] Run targeted tests and confirm failures.
- [x] Add public `TimerMetrics` snapshot and `Timer.metrics()`.
- [x] Update `SystemTimer` and `TimerTaskEntry` counters so terminal states are counted once.
- [x] Run targeted tests and confirm metrics behavior passes.

### Task 3: Benchmark And Stress Suites

- [x] Extend `TimerBenchmark` with configurable producer threads while keeping smoke defaults fast.
- [x] Add `TimerStressBenchmark` for high-volume schedule/cancel matrices.
- [x] Run `mvn -Pbenchmark -DskipTests package`.
- [x] Run one smoke benchmark and one tiny stress benchmark to verify both entry points work.

### Task 4: Documentation And Verification

- [x] Update README benchmark, stress, metrics, and roadmap sections.
- [x] Update architecture diagrams and invariants for clock and metrics.
- [x] Run package scan to ensure old `com.photowey` package is gone.
- [x] Run `mvn test`, `mvn clean verify`, benchmark package, JMH smoke, and GitNexus `detect_changes`.
- [x] Report exact verification evidence and any residual risks.

## Risk Notes

- GitNexus reports `TimingWheel` and `TimingWheel.add` as HIGH risk because they affect `build`, `schedule`, `advance`,
  and overflow flows. Keep changes additive and covered by deterministic unit tests.
- GitNexus reports `SystemTimer.submit` as HIGH risk because it is the due-task gateway. Keep shutdown rejection
  handling unchanged except for metrics counting.
- New public API should be minimal and stable: `Timer.metrics()` and immutable `TimerMetrics`.
