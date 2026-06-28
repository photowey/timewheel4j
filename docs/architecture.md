# timewheel4j Architecture

Engineering specification for the `timewheel4j` scheduler, module layout,
runtime model, Spring Boot integration, tests, and benchmark assets.

## Design Scope

`timewheel4j` is a Java 11 scheduling library based on a Kafka-style
hierarchical timing wheel.

Core placement model:

- `Timer.schedule(Runnable, long, TimeUnit)` creates `TimerTaskEntry` handles.
- `TimingWheel` places entries into `TimerTaskList` buckets by deadline.
- `BucketDelayQueue` stores `TimerTaskList` bucket expiration entries.
- Overflow `TimingWheel` levels represent coarser time intervals.
- Expired bucket flush re-adds entries to lower wheel levels or dispatches due
  entries.
- Worker executors run expired scheduled `Runnable` instances.

Reference model:

- Apache Kafka timer-style bucket-level delay queue.
- Intrusive task membership inside each bucket.
- Overflow wheel cascading for deadlines beyond the active interval.
- Bucket flush and re-add on coarse bucket expiration.

## Architecture Overview

Maven reactor:

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

Core scheduler graph:

```mermaid
flowchart LR
    Client["Client"]
    Timer["Timer API"]
    SystemTimer["SystemTimer"]
    Boss["Boss Executor<br/>bucket expiration loop"]
    Clock["Clock"]
    Metrics["TimerMetrics"]
    DelayQueue["BucketDelayQueue<br/>(DelayQueue&lt;TimerTaskList&gt;)"]
    Wheel0["TimingWheel L0<br/>tickMs interval"]
    Wheel1["TimingWheel L1<br/>tickMs * wheelSize interval"]
    Wheel2["TimingWheel L2..."]
    Workers["Worker Executor"]

    Client -->|"schedule(Runnable, delay, unit)"| Timer
    Timer --> SystemTimer
    SystemTimer -->|"submit one long-running expiration loop"| Boss
    Boss -->|"poll, advance, flush"| SystemTimer
    SystemTimer -->|"read nowMs"| Clock
    SystemTimer -->|"snapshot counters"| Metrics
    SystemTimer -->|"add entry"| Wheel0
    Wheel0 -->|"deadline beyond L0 interval"| Wheel1
    Wheel1 -->|"deadline beyond L1 interval"| Wheel2
    Wheel0 -->|"offer bucket expiration"| DelayQueue
    Wheel1 -->|"offer bucket expiration"| DelayQueue
    Wheel2 -->|"offer bucket expiration"| DelayQueue
    SystemTimer -->|"poll expired bucket"| DelayQueue
    SystemTimer -->|"flush and re-add"| Wheel0
    SystemTimer -->|"due entry"| Workers
```

Spring Boot auto-configuration:

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
    Shared->>Shared: evaluate enabled flag and Timer bean condition
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

    alt deadline already due
        W0-->>T: false
        T->>T: submit(entry)
    else deadline inside active wheel interval
        W0->>B: add(entry)
        W0->>B: setExpiration(bucketExpiration)
        alt bucket expiration changed
            W0->>DQ: offer(bucket)
            W0->>T: record bucket offer
        end
        W0-->>T: true
    else deadline belongs to overflow interval
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
        alt entry remains scheduled
            TW-->>Boss: true
            Note over TW: entry cascades into lower wheel<br/>or remains in overflow wheel
        else due entry
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

`SystemTimer` uses a boss/worker execution model.

Boss executor responsibilities:

- Poll `BucketDelayQueue`.
- Advance `TimingWheel` clocks.
- Flush expired `TimerTaskList` buckets.
- Re-add flushed entries to wheel levels.
- Submit due entries to the worker executor.
- Maintain bucket expiration metrics.

Worker executor responsibilities:

- Execute expired scheduled `Runnable` instances.
- Isolate scheduled task execution from bucket expiration processing.
- Apply configured worker thread count and queue capacity.

```mermaid
flowchart LR
    Producers["Producer threads"]
    Timer["Timer.schedule"]
    Wheel["TimingWheel<br/>bucket placement"]
    Boss["Boss Executor<br/>one expiration loop"]
    DelayQueue["BucketDelayQueue"]
    Workers["Worker Executor<br/>bounded queue by default"]
    Task["Scheduled Runnable"]

    Producers --> Timer
    Timer --> Wheel
    Boss --> DelayQueue
    DelayQueue --> Boss
    Boss --> Wheel
    Boss -->|"due task submit"| Workers
    Workers --> Task
```

Boss executor configuration:

- Default: single-thread executor.
- External executor option: `TimerBuilder.bossExecutor(ExecutorService)`.
- Runtime unit: one long-running bucket expiration loop per `SystemTimer`.
- Ownership: one boss loop owns bucket expiration polling and wheel advancement
  for a `SystemTimer`.

Worker executor configuration:

- Default: fixed-size `ThreadPoolExecutor`.
- Queue: bounded by `workerQueueCapacity`.
- External executor options:
  `TimerBuilder.workerExecutor(ExecutorService)` and
  `TimerBuilder.executorService(ExecutorService)`.
- Rejection metric: `TimerMetrics.rejectedTimeouts()`.

## Spring Executor Lifecycle

Spring Boot starter executor beans:

- `timewheel4jBossExecutor`
- `timewheel4jWorkerExecutor`

Executor resolution order:

1. Resolve `timewheel4j.boss.executor.bean-name` and
   `timewheel4j.worker.executor.bean-name` when configured.
2. Create Spring managed executor beans when `executor.auto-create=true`.
3. Delegate executor creation to `SystemTimer` when
   `executor.auto-create=false`.

```mermaid
flowchart TD
    Start["Spring auto-configuration"]
    HasTimer{"Application Timer bean exists?"}
    Skip["Skip timer auto-configuration"]
    BeanName{"executor.bean-name configured?"}
    UseExternal["External ExecutorService bean"]
    AutoCreate{"executor.auto-create enabled?"}
    SpringBean["Create Spring managed executor bean"]
    CoreOwned["Use core owned executor"]
    Build["Build SystemTimer"]

    Start --> HasTimer
    HasTimer -->|"present"| Skip
    HasTimer -->|"absent"| BeanName
    BeanName -->|"configured"| UseExternal
    BeanName -->|"empty"| AutoCreate
    AutoCreate -->|"enabled"| SpringBean
    AutoCreate -->|"disabled"| CoreOwned
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

    C->>E: cancel
    E->>E: CAS INIT -> CANCELLED

    alt cancellation wins
        E->>B: remove(entry)
        E->>T: decrement pending size and record cancellation
        E-->>C: true
    else already expired or cancelled
        E-->>C: false
    end
```

## Multi-Level Wheel Placement

Example parameters:

```text
tickMs = 10
wheelSize = 8
L0 interval = 10ms * 8 = 80ms
L1 interval = 80ms * 8 = 640ms
L2 interval = 640ms * 8 = 5120ms
```

Placement for a 220ms deadline:

1. `TimingWheel L0` compares deadline against the 80ms interval.
2. `TimingWheel L1` accepts the deadline inside the 640ms interval.
3. The L1 bucket expiration reaches the delay queue.
4. The boss loop flushes the L1 bucket and re-adds the entry.
5. The entry moves into an L0 bucket.
6. The L0 bucket expiration dispatches the entry to the worker executor.

```mermaid
flowchart TD
    Entry["Task deadline = now + 220ms"]
    L0Check{"L0 interval match?<br/>deadline within 80ms window"}
    L1Check{"L1 interval match?<br/>deadline within 640ms window"}
    L1Bucket["Place in L1 bucket"]
    L1Expire["L1 bucket expires"]
    Readd["Flush and re-add entry"]
    L0Bucket["Place in L0 bucket"]
    Execute["Submit to worker executor"]

    Entry --> L0Check
    L0Check -->|"mismatch"| L1Check
    L1Check -->|"match"| L1Bucket
    L1Bucket --> L1Expire
    L1Expire --> Readd
    Readd --> L0Bucket
    L0Bucket --> Execute
```

## Core Invariants

- A `TimerTaskEntry` belongs to at most one `TimerTaskList` at a time.
- A bucket is offered to `BucketDelayQueue` only when its expiration changes.
- `BucketDelayQueue` wraps the JDK `DelayQueue` and returns `Optional` from
  poll/peek operations.
- The underlying JDK `DelayQueue` stores `TimerTaskList` buckets.
- A `SystemTimer` submits exactly one boss loop. That loop owns bucket
  expiration polling and wheel advancement.
- Boss code submits due entries to the worker executor.
- `TimerTaskEntry.cancel()` removes the entry from its assigned bucket after a
  successful cancellation state transition.
- Pending size is incremented once when scheduling and decremented once when the
  entry reaches a terminal state: cancelled or expired.
- `TimingWheel.add(entry)` returns `false` for due entries.
- `Clock` is package-private and supplies deterministic time for tests.
- `TimerMetrics` is a snapshot; counters are monotonic except pending timeout
  count.

## Implementation Files

Core package: `timewheel4j-core/src/main/java`.

| File                      | Responsibility                                                                      |
|---------------------------|-------------------------------------------------------------------------------------|
| `Timer.java`              | Public scheduling API.                                                              |
| `Timeout.java`            | Public cancellation and state handle.                                               |
| `TimerMetrics.java`       | Immutable metrics snapshot.                                                         |
| `TimerBuilder.java`       | Builder for `SystemTimer`.                                                          |
| `Clock.java`              | Package-private time source for production and deterministic tests.                 |
| `BucketDelayQueue.java`   | Optional-based wrapper around the JDK bucket delay queue.                           |
| `SystemTimer.java`        | Boss loop, delay queue polling, wheel advancement, worker submission, pending size. |
| `TimingWheel.java`        | Hierarchical wheel placement, overflow wheel creation, clock advancement.           |
| `TimerTaskList.java`      | Delayed bucket and intrusive linked list.                                           |
| `TimerTaskEntry.java`     | Scheduled task node and timeout state machine.                                      |
| `TimerThreadFactory.java` | Daemon thread creation for scheduler and owned workers.                             |

Spring package: `timewheel4j-spring`.

`timewheel4j-spring-boot-autoconfigure`:

- `Timewheel4jProperties.java`: binds `timewheel4j.*` properties.
- `AbstractTimewheel4jConfiguration.java`: shared bean definitions for
  starter-owned auto-configuration entries.

`timewheel4j-spring-boot-starter`:

- `Timewheel4jConfiguration.java`: traditional Boot 2.x `@Configuration`
  entrypoint for `spring.factories`.
- `Timewheel4jAutoConfiguration.java`: Boot 2.7+ `@AutoConfiguration`
  entrypoint for `AutoConfiguration.imports`.
- `META-INF/spring.factories`: Boot 2.x starter-owned auto-configuration
  metadata.
- `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`:
  Boot 2.7+ starter-owned auto-configuration metadata.
- `pom.xml`: Boot 2.x starter dependencies.

`timewheel4j-spring-boot3-starter`:

- `Timewheel4jAutoConfiguration.java`: Boot 3.x starter-owned
  `@AutoConfiguration` entrypoint.
- `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`:
  Boot 3.x starter-owned auto-configuration metadata.
- `pom.xml`: Boot 3.x starter dependencies and Java 17 compiler release.

## Testing Specification

Unit test format:

- Framework: JUnit 5.
- Structure: Given-When-Then.
- Naming: behavior specification style.

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

Test coverage scope:

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
- `AbstractTimewheel4jConfiguration` default creation, disabled mode,
  application bean override, nested property binding, compatibility property
  binding, external executor bean names, invalid tick rejection, and schedule
  operation validation.
- `Timewheel4jBootStarterTest` validates the Boot 2 starter entrypoints and
  starter-owned metadata on a Boot 2 runtime.
- `Timewheel4jBoot3StarterTest` validates the Boot 3 starter-owned
  `@AutoConfiguration` entrypoint and metadata on a Boot 3 runtime.

## Benchmark And Stress Strategy

Benchmark source set: `timewheel4j-core/src/jmh/java`.

Maven profile: `benchmark`.

- `TimerBenchmark` compares `SystemTimer`, JDK `ScheduledExecutorService`,
  Netty `HashedWheelTimer`, and a per-task `DelayQueue` baseline.
- `TimerStressBenchmark` runs high-volume schedule/cancel matrices, including
  million-task workloads.
- Producer concurrency uses JMH thread parameters, for example `-t 4`.
- CI benchmark phase builds the JMH benchmark jar.

Coverage gate:

```text
line coverage   >= 85%
branch coverage >= 85%
```

CI commands:

```bash
mvn -B -Ptoolchain verify
mvn -B -Pbenchmark -pl timewheel4j-core -am -DskipTests package
```

Toolchain profile requirements:

- JDK 11 entry in `~/.m2/toolchains.xml`.
- JDK 17 entry in `~/.m2/toolchains.xml`.

## Extension Surface

Extension areas:

1. Benchmark result history.
2. Micrometer and metrics backend adapters.
3. Hot-bucket contention tuning.
4. High-cancellation workload tuning.
5. Delay distribution generators for benchmark workloads.
