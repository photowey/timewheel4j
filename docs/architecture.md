# timewheel4j Architecture

This document explains the current design of `timewheel4j` for developers who
want to understand, maintain, or extend the scheduler.

## Design Goal

`timewheel4j` implements a Kafka-style hierarchical timing wheel for massive
delayed scheduling.

The most important design choice is:

> The JDK `DelayQueue` stores bucket lists, not individual tasks.

This keeps the global delay queue small when many tasks share nearby deadlines.
Tasks live inside wheel buckets. The delay queue only wakes the scheduler when a
non-empty bucket reaches its expiration time.

The implementation intentionally follows the same scheduling model used by
Apache Kafka's timer: a `DelayQueue` of bucket lists, intrusive task membership
inside each bucket, overflow wheels for deadlines outside the current interval,
and bucket flush/re-add when a coarse bucket expires. `timewheel4j` keeps that
core idea but exposes it as an independent Java 11 library with a smaller public
API, boss/worker executor separation, metrics snapshots, Spring Boot starters,
and JMH benchmark suites.

## Architecture Overview

The Maven reactor is split by responsibility:

```mermaid
flowchart TD
    Root["timewheel4j<br/>(parent reactor)"]
    Bom["timewheel4j-bom<br/>dependency management"]
    Core["timewheel4j-core<br/>Java 11 scheduler"]
    Spring["timewheel4j-spring<br/>Spring parent"]
    Auto["timewheel4j-spring-boot-autoconfigure<br/>shared properties and bean definitions"]
    Boot2["timewheel4j-spring-boot-starter<br/>Boot 2.x starter"]
    Boot3["timewheel4j-spring-boot3-starter<br/>Boot 3.x starter, Java 17"]
    Boot2Entry["Timewheel4jConfiguration / Timewheel4jAutoConfiguration<br/>starter-owned Boot 2 entries"]
    Boot3Entry["Timewheel4jAutoConfiguration<br/>starter-owned Boot 3 entry"]

    Root --> Bom
    Root --> Core
    Root --> Spring
    Spring --> Auto
    Spring --> Boot2
    Spring --> Boot3
    Auto --> Core
    Boot2 --> Auto
    Boot2 --> Boot2Entry
    Boot2Entry --> Auto
    Boot3 --> Auto
    Boot3 --> Boot3Entry
    Boot3Entry --> Auto
```

The scheduler itself lives in `timewheel4j-core`:

```mermaid
flowchart LR
    Client["Client code"]
    Timer["Timer API"]
    SystemTimer["SystemTimer"]
    Boss["Boss Executor<br/>single owner loop"]
    Clock["Clock"]
    Metrics["TimerMetrics"]
    DelayQueue["BucketDelayQueue<br/>(DelayQueue&lt;TimerTaskList&gt;)"]
    Wheel0["TimingWheel L0<br/>tickMs"]
    Wheel1["TimingWheel L1<br/>tickMs * wheelSize"]
    Wheel2["TimingWheel L2..."]
    Workers["Worker Executor"]

    Client -->|"schedule(Runnable, delay, unit)"| Timer
    Timer --> SystemTimer
    SystemTimer -->|"submit one long-running loop"| Boss
    Boss -->|"poll, advance, flush"| SystemTimer
    SystemTimer -->|"read nowMs"| Clock
    SystemTimer -->|"snapshot counters"| Metrics
    SystemTimer -->|"add entry"| Wheel0
    Wheel0 -->|"far deadline"| Wheel1
    Wheel1 -->|"farther deadline"| Wheel2
    Wheel0 -->|"offer bucket expiration"| DelayQueue
    Wheel1 -->|"offer bucket expiration"| DelayQueue
    Wheel2 -->|"offer bucket expiration"| DelayQueue
    SystemTimer -->|"poll expired bucket"| DelayQueue
    SystemTimer -->|"flush and re-add"| Wheel0
    SystemTimer -->|"expired task"| Workers
```

The Spring Boot integration is intentionally thin:

```mermaid
sequenceDiagram
    autonumber
    participant App as Spring Application
    participant Meta as Boot Auto-Config Metadata
    participant Entry as Starter Auto-Config Entry
    participant Shared as AbstractTimewheel4jConfiguration
    participant Props as Timewheel4jProperties
    participant Builder as TimerBuilder
    participant Timer as SystemTimer

    App->>Meta: discovers starter metadata
    Meta->>Entry: load spring.factories or AutoConfiguration.imports
    Entry->>Shared: inherit shared bean definitions
    Shared->>Props: bind timewheel4j.* properties
    Shared->>Shared: check enabled and missing Timer bean
    Shared->>Builder: configure tick, wheel size, boss, workers, executors
    Builder-->>Shared: build Timer
    Shared-->>App: register Timer bean
    App->>Timer: destroyMethod shutdown on context close
```

## Core Types

```mermaid
classDiagram
    class Timer {
        <<interface>>
        +schedule(Runnable task, long delay, TimeUnit unit) Timeout
        +size() long
        +metrics() TimerMetrics
        +shutdown() void
    }

    class Timeout {
        <<interface>>
        +cancel() boolean
        +isCancelled() boolean
        +isExpired() boolean
    }

    class TimerBuilder {
        +builder() TimerBuilder
        +tick(long tick, TimeUnit unit) TimerBuilder
        +wheelSize(int wheelSize) TimerBuilder
        +bossExecutor(ExecutorService bossExecutor) TimerBuilder
        +bossName(String bossName) TimerBuilder
        +schedulerName(String schedulerName) TimerBuilder
        +workerThreads(int workerThreads) TimerBuilder
        +workerQueueCapacity(int workerQueueCapacity) TimerBuilder
        +workerExecutor(ExecutorService workerExecutor) TimerBuilder
        +executorService(ExecutorService executorService) TimerBuilder
        +build() Timer
    }

    class TimerMetrics {
        +scheduledTimeouts() long
        +expiredTimeouts() long
        +cancelledTimeouts() long
        +rejectedTimeouts() long
        +pendingTimeouts() long
        +bucketOffers() long
        +bucketExpirations() long
        +maxBucketDelayMs() long
    }

    class Clock {
        <<interface>>
        +nowMs() long
    }

    class SystemTimer {
        -BucketDelayQueue delayQueue
        -TimingWheel timingWheel
        -ExecutorService boss
        -ExecutorService workers
        -AtomicLong size
        -Clock clock
        +schedule(Runnable task, long delay, TimeUnit unit) Timeout
        +size() long
        +metrics() TimerMetrics
        +shutdown() void
    }

    class TimingWheel {
        -long tickMs
        -int wheelSize
        -long interval
        -TimerTaskList[] buckets
        -TimingWheel overflowWheel
        +add(TimerTaskEntry entry) boolean
        +advanceClock(long timeMs) void
    }

    class TimerTaskList {
        -AtomicLong expiration
        -TimerTaskEntry root
        +add(TimerTaskEntry entry) void
        +remove(TimerTaskEntry entry) void
        +flush(Consumer~TimerTaskEntry~ consumer) void
    }

    class TimerTaskEntry {
        -Runnable task
        -long deadlineMs
        -AtomicInteger state
        +cancel() boolean
        +isCancelled() boolean
        +isExpired() boolean
    }

    Timer <|.. SystemTimer
    Timeout <|.. TimerTaskEntry
    TimerBuilder --> SystemTimer
    SystemTimer --> TimingWheel
    SystemTimer --> TimerTaskList
    SystemTimer --> Clock
    SystemTimer --> TimerMetrics
    TimingWheel --> TimerTaskList
    TimerTaskList --> TimerTaskEntry
```

## Scheduling Sequence

```mermaid
sequenceDiagram
    autonumber
    participant C as Client
    participant T as SystemTimer
    participant W0 as TimingWheel L0
    participant WO as Overflow Wheel
    participant B as TimerTaskList
    participant DQ as DelayQueue

    C->>T: schedule(task, delay, unit)
    T->>T: deadlineMs = clock.nowMs() + delay
    T->>T: create TimerTaskEntry
    T->>W0: add(entry)

    alt deadline is already due
        W0-->>T: false
        T->>T: submit(entry)
    else deadline fits current wheel interval
        W0->>B: add(entry)
        W0->>B: setExpiration(bucketExpiration)
        alt bucket expiration changed
            W0->>DQ: offer(bucket)
            W0->>T: record bucket offer
        end
        W0-->>T: true
    else deadline is outside current interval
        W0->>WO: add(entry)
        WO-->>W0: true
        W0-->>T: true
    end

    T-->>C: Timeout
```

## Expiration And Cascade Sequence

```mermaid
sequenceDiagram
    autonumber
    participant Boss as Boss Executor
    participant DQ as DelayQueue
    participant TW as TimingWheel
    participant B as Expired Bucket
    participant EX as Worker Executor

    Boss->>DQ: poll(timeout)
    DQ-->>Boss: expired TimerTaskList
    Boss->>TW: advanceClock(bucket.expiration)
    Boss->>Boss: record bucket expiration
    Boss->>B: flush(entryConsumer)

    loop each entry in bucket
        B-->>Boss: entry
        Boss->>TW: add(entry)
        alt still not due
            TW-->>Boss: true
            Note over TW: entry cascades into lower wheel<br/>or remains in overflow wheel
        else due now
            TW-->>Boss: false
            Boss->>Boss: mark entry expired and record timeout expiration
            Boss->>EX: submit entry.run
            alt worker rejects
                EX-->>Boss: RejectedExecutionException
                Boss->>Boss: record rejection and continue loop
            end
        end
    end
```

## Execution Model

`SystemTimer` uses a boss/worker split:

The boss side is optimized for timer correctness and low scheduling latency. It
is the single owner that polls bucket expirations, advances the hierarchical
wheel, flushes buckets, and decides whether an entry should be cascaded or
submitted. It never runs user `Runnable` code directly. The worker side is
optimized for event handling: worker threads execute expired user tasks and can
be sized, bounded, or supplied by the caller independently from the boss loop.

```mermaid
flowchart LR
    Producers["Producer threads"]
    Timer["Timer.schedule"]
    Wheel["TimingWheel<br/>bucket placement"]
    Boss["Boss Executor<br/>one submitted loop"]
    DelayQueue["BucketDelayQueue"]
    Workers["Worker Executor<br/>bounded queue by default"]
    Task["User Runnable"]

    Producers --> Timer
    Timer --> Wheel
    Boss --> DelayQueue
    DelayQueue --> Boss
    Boss --> Wheel
    Boss -->|"due task submit"| Workers
    Workers --> Task
```

The default boss executor is a single-thread executor. A caller may supply any
`ExecutorService`, but each `SystemTimer` submits exactly one long-running boss
loop to it. Multiple boss threads do not concurrently advance the same wheel.

The default worker executor is a fixed-size `ThreadPoolExecutor` with a bounded
queue. A worker rejection increments `rejectedTimeouts`; when rejection happens
inside the boss loop, the loop continues processing later buckets. Immediate
zero-delay submissions from caller threads still surface
`RejectedExecutionException` to the caller.

## Spring Executor Lifecycle

The Spring Boot starter keeps executor management outside the core timer API.
When no user `Timer` bean exists, the auto-configuration can create two named
executor beans:

- `timewheel4jBossExecutor`
- `timewheel4jWorkerExecutor`

The auto-configuration resolves executors in this order:

1. Use `timewheel4j.boss.executor.bean-name` and
   `timewheel4j.worker.executor.bean-name` when configured.
2. Use the auto-created Spring managed beans when `executor.auto-create=true`.
3. Let `SystemTimer` create and own its internal executors when
   `executor.auto-create=false` and no bean name is configured.

```mermaid
flowchart TD
    Start["Spring auto-configuration"]
    HasTimer{"User Timer bean exists?"}
    Skip["Back off completely"]
    BeanName{"executor.bean-name configured?"}
    UseExternal["Use caller-owned ExecutorService bean"]
    AutoCreate{"executor.auto-create enabled?"}
    SpringBean["Create Spring managed executor bean"]
    CoreOwned["Use core owned executor"]
    Build["Build SystemTimer"]

    Start --> HasTimer
    HasTimer -->|"yes"| Skip
    HasTimer -->|"no"| BeanName
    BeanName -->|"yes"| UseExternal
    BeanName -->|"no"| AutoCreate
    AutoCreate -->|"yes"| SpringBean
    AutoCreate -->|"no"| CoreOwned
    UseExternal --> Build
    SpringBean --> Build
    CoreOwned --> Build
```

## Cancellation Sequence

```mermaid
sequenceDiagram
    autonumber
    participant C as Client
    participant E as TimerTaskEntry / Timeout
    participant B as TimerTaskList
    participant T as SystemTimer

    C->>E: cancel()
    E->>E: CAS INIT -> CANCELLED

    alt cancellation wins
        E->>B: remove(entry)
        E->>T: decrement pending size and record cancellation
        E-->>C: true
    else already expired or cancelled
        E-->>C: false
    end
```

## Multi-Level Wheel Intuition

Assume `tickMs = 10` and `wheelSize = 8`.

```text
L0 interval = 10ms * 8 = 80ms
L1 interval = 80ms * 8 = 640ms
L2 interval = 640ms * 8 = 5120ms
```

A task scheduled for 220ms cannot fit into L0, so it is placed in L1. When the
L1 bucket expires, the scheduler flushes that bucket and re-adds the task. At
that time, the task is close enough to fit into L0. It is then placed in a fine
bucket and eventually submitted to workers.

```mermaid
flowchart TD
    Entry["Task deadline = now + 220ms"]
    L0Check{"Fits L0 interval?<br/>deadline < current + 80ms"}
    L1Check{"Fits L1 interval?<br/>deadline < current + 640ms"}
    L1Bucket["Place in L1 bucket"]
    L1Expire["L1 bucket expires"]
    Readd["Flush and re-add entry"]
    L0Bucket["Place in L0 bucket"]
    Execute["Submit to worker executor"]

    Entry --> L0Check
    L0Check -->|"no"| L1Check
    L1Check -->|"yes"| L1Bucket
    L1Bucket --> L1Expire
    L1Expire --> Readd
    Readd --> L0Bucket
    L0Bucket --> Execute
```

## Important Invariants

- A `TimerTaskEntry` belongs to at most one `TimerTaskList` at a time.
- A bucket is offered to `BucketDelayQueue` only when its expiration changes.
- `BucketDelayQueue` wraps the JDK `DelayQueue` and returns `Optional` from
  poll/peek operations so scheduler code does not handle raw `null` buckets.
- The underlying JDK `DelayQueue` contains buckets, never individual tasks.
- A `SystemTimer` submits exactly one boss loop. That loop is the owner that
  polls bucket expirations and advances the wheel.
- Boss code never runs user `Runnable` tasks. It only submits due tasks to the
  worker executor.
- `TimerTaskEntry.cancel()` removes the entry from its current bucket when
  cancellation wins the state transition.
- Pending size is incremented once when scheduling and decremented once when the
  entry reaches a terminal state: cancelled or expired.
- `TimingWheel.add(entry)` returns `false` only when the entry is due and should
  be executed now.
- `Clock` is package-private so production uses the system clock while tests can
  run exact cascade assertions without sleeping.
- `TimerMetrics` is a snapshot; counters are monotonic except pending timeout
  count, which reflects the current active timeout count.

## Current Implementation Files

Core files live under `timewheel4j-core/src/main/java`.

| File                      | Responsibility                                                                      |
|---------------------------|-------------------------------------------------------------------------------------|
| `Timer.java`              | Public scheduling API.                                                              |
| `Timeout.java`            | Public cancellation and state handle.                                               |
| `TimerMetrics.java`       | Immutable metrics snapshot.                                                         |
| `TimerBuilder.java`       | User-facing builder for `SystemTimer`.                                              |
| `Clock.java`              | Package-private time source for production and deterministic tests.                 |
| `BucketDelayQueue.java`   | Optional-based wrapper around the JDK bucket delay queue.                           |
| `SystemTimer.java`        | Boss loop, delay queue polling, wheel advancement, worker submission, pending size. |
| `TimingWheel.java`        | Hierarchical wheel placement, overflow wheel creation, clock advancement.           |
| `TimerTaskList.java`      | Delayed bucket and intrusive linked list.                                           |
| `TimerTaskEntry.java`     | Scheduled task node and timeout state machine.                                      |
| `TimerThreadFactory.java` | Daemon thread creation for scheduler and owned workers.                             |

Spring files live under `timewheel4j-spring`.

| File                                                                                                                                   | Responsibility                                                             |
|----------------------------------------------------------------------------------------------------------------------------------------|----------------------------------------------------------------------------|
| `timewheel4j-spring-boot-autoconfigure/.../Timewheel4jProperties.java`                                                                 | Binds `timewheel4j.*` properties.                                          |
| `timewheel4j-spring-boot-autoconfigure/.../AbstractTimewheel4jConfiguration.java`                                                      | Shared bean definitions for the starter-owned auto-configuration entries.  |
| `timewheel4j-spring-boot-starter/.../Timewheel4jConfiguration.java`                                                                    | Traditional Boot 2.x `@Configuration` entrypoint for `spring.factories`.   |
| `timewheel4j-spring-boot-starter/.../Timewheel4jAutoConfiguration.java`                                                                | Boot 2.7+ `@AutoConfiguration` entrypoint for `AutoConfiguration.imports`. |
| `timewheel4j-spring-boot-starter/src/main/resources/META-INF/spring.factories`                                                         | Boot 2.x starter-owned auto-configuration metadata.                        |
| `timewheel4j-spring-boot-starter/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`  | Boot 2.7+ starter-owned auto-configuration metadata.                       |
| `timewheel4j-spring-boot-starter/pom.xml`                                                                                              | Boot 2.x starter dependencies.                                             |
| `timewheel4j-spring-boot3-starter/.../Timewheel4jAutoConfiguration.java`                                                               | Boot 3.x starter-owned `@AutoConfiguration` entrypoint.                    |
| `timewheel4j-spring-boot3-starter/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports` | Boot 3.x starter-owned auto-configuration metadata.                        |
| `timewheel4j-spring-boot3-starter/pom.xml`                                                                                             | Boot 3.x starter dependencies and Java 17 compiler release.                |

## Testing Strategy

All unit tests use JUnit 5 and follow a Given-When-Then layout. The test names
also use the same shape so failures read like behavior specifications:

```java
@Test
void givenCancelledTaskWhenDeadlineExpiresThenTaskIsNotExecuted() {
    // Given
    ...

    // When
    ...

    // Then
    ...
}
```

The current test suite covers:

- `SystemTimer` public scheduling behavior, shutdown, cancellation, zero-delay
  execution, external boss/worker executors, owned boss/worker naming, bounded
  worker queues, metrics, shutdown race rejection, and invalid arguments.
- `TimerBuilder` defaults and all validation branches.
- `TimerTaskEntry` state transitions and completion callback idempotency.
- `TimerTaskList` expiration, move-between-lists behavior, flush behavior, and
  ordering.
- `BucketDelayQueue` empty and expired-bucket Optional semantics.
- `TimingWheel` due-task detection, bucket offer behavior, overflow placement,
  cancelled-entry handling, deterministic cascade behavior, and clock
  advancement.
- `AbstractTimewheel4jConfiguration` default creation, disabled mode, user bean
  override, nested and compatibility property binding, external executor bean
  names, invalid tick rejection, and schedule smoke.
- `Timewheel4jBootStarterTest` validates the Boot 2 starter entrypoints and
  starter-owned metadata on a Boot 2 runtime.
- `Timewheel4jBoot3StarterTest` validates the Boot 3 starter-owned
  `@AutoConfiguration` entrypoint and metadata on a Boot 3 runtime.

## Benchmark And Stress Strategy

JMH suites live under `timewheel4j-core/src/jmh/java` and are isolated by the
Maven `benchmark` profile:

- `TimerBenchmark` compares `SystemTimer`, JDK `ScheduledExecutorService`,
  Netty `HashedWheelTimer`, and a simple one-entry-per-task `DelayQueue`.
- `TimerStressBenchmark` runs high-volume schedule/cancel matrices, including
  million-task workloads.
- Producer concurrency is modeled with JMH threads, for example `-t 4`.
- Stress defaults are intentionally excluded from normal CI.

`mvn verify` runs the full reactor suite and enforces the core JaCoCo coverage
gate:

```text
line coverage   >= 85%
branch coverage >= 85%
```

CI also runs:

```bash
mvn -B -Ptoolchain verify
mvn -B -Pbenchmark -pl timewheel4j-core -am -DskipTests package
```

The `toolchain` profile expects JDK 11 and JDK 17 entries in
`~/.m2/toolchains.xml`. Local builds can run without that profile when the
developer does not have toolchains configured.

## Future Work

The current code is intentionally compact. The next engineering work should be:

1. Publish benchmark result history for regression tracking.
2. Add optional Micrometer or metrics-backend adapters.
3. Tune contention around hot buckets and high cancellation rates.
4. Add richer delay distribution generators for benchmark workloads.
