package dev.langchain4j.agentic.observability;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

import dev.langchain4j.agentic.planner.AgentInstance;
import dev.langchain4j.agentic.scope.AgenticScope;
import java.time.Duration;
import java.util.Collections;
import org.junit.jupiter.api.RepeatedTest;

/**
 * Verifies that sub-millisecond durations and public timestamps share one clock.
 *
 * @author 芋道源码
 */
class AgentInvocationClockTest {

    @RepeatedTest(10)
    void consecutiveInvocationsHaveConsistentTimestampAndDurationBounds() {
        AgenticScope scope = mock(AgenticScope.class);
        AgentInstance agent = mock(AgentInstance.class);
        AgentInvocation first = new AgentInvocation(new AgentRequest(scope, agent, Collections.emptyMap()));
        first.finished(new AgentResponse(scope, agent, Collections.emptyMap()));
        AgentInvocation next = new AgentInvocation(new AgentRequest(scope, agent, Collections.emptyMap()));
        assertTrue(first.duration().compareTo(Duration.ZERO) > 0);
        assertEquals(first.finishTime(), first.startTime().plus(first.duration()));
        assertFalse(next.startTime().isBefore(first.finishTime()));
    }
}
