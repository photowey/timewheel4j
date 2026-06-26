# timewheel4j

A hierarchical time-wheel algorithm implemented in Java

## Current API

```java
DelayQueue queue = new QueueBoostrap.QueueBoostrapBuilder()
        .delayMills(1)
        .maxDelay(60_000)
        .level(1)
        .intervals(new int[]{10})
        .build()
        .start();

queue.

schedule(() ->System.out.

println("done"), 100,TimeUnit.MILLISECONDS);
        queue.

shutdown();
```

The current implementation provides a runnable delay queue facade backed by an
event registry, tick scheduler, hierarchical bucket wheel, and worker pool.
`delayMills` is the base tick duration. `intervals` defines the slot count of
each wheel level; for example `new int[]{64, 64}` creates a 64-slot base wheel
and a second 64-slot wheel that cascades tasks back down as lower levels rotate.
`maxDelay` must fit within `delayMills * product(intervals)`.
