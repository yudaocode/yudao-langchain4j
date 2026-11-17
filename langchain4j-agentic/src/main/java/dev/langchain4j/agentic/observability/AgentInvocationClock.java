package dev.langchain4j.agentic.observability;

import java.time.Instant;

/**
 * Gives invocation timestamps and elapsed times one high-resolution timeline on Java 8.
 * The timeline is anchored to wall time once; elapsed time uses the monotonic clock.
 *
 * @author 芋道源码
 */
final class AgentInvocationClock {

    private static final Instant ORIGIN = Instant.now();
    private static final long ORIGIN_NANOS = System.nanoTime();

    private AgentInvocationClock() {}

    static Instant now() {
        return ORIGIN.plusNanos(System.nanoTime() - ORIGIN_NANOS);
    }
}
