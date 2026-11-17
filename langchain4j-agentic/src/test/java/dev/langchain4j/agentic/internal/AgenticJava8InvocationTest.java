package dev.langchain4j.agentic.internal;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import dev.langchain4j.agentic.agent.AgentInvocationException;
import dev.langchain4j.agentic.observability.AgentListener;
import dev.langchain4j.agentic.scope.AgenticScope;
import dev.langchain4j.agentic.scope.AgenticSystemSuspendedException;
import dev.langchain4j.agentic.scope.DefaultAgenticScope;
import dev.langchain4j.data.message.ImageContent;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.TextContent;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.invocation.LangChain4jManaged;
import dev.langchain4j.model.chat.request.ChatRequest;
import java.util.Arrays;
import java.util.Collections;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * Preserves private-interface invocation and multimodal transformation contracts on Java 8.
 *
 * @author 芋道源码
 */
class AgenticJava8InvocationTest {

    @AfterEach
    void clearManagedContext() {
        LangChain4jManaged.removeCurrent();
    }

    @Test
    void restoresCallerContextAfterSuccessFailureAndSuspension() throws Exception {
        DefaultAgenticScope caller = mock(DefaultAgenticScope.class);
        DefaultAgenticScope invoked = mock(DefaultAgenticScope.class);
        Map<Class<? extends LangChain4jManaged>, LangChain4jManaged> previous =
                Collections.singletonMap(AgenticScope.class, caller);
        Worker worker = new Worker();
        AgentInvoker invoker = mock(AgentInvoker.class);
        AgentListener listener = mock(AgentListener.class);
        AgentInvocationArguments args =
                new AgentInvocationArguments(Collections.singletonMap("value", "answer"), new Object[] {"answer"});
        LangChain4jManaged.setCurrent(previous);
        when(invoker.method()).thenReturn(Worker.class.getMethod("echo", String.class));
        assertEquals(
                "answer",
                AgentInvocationSupport.invoke(invoker, invoked, listener, worker, args, new AgentModelResponse()));
        assertSame(invoked, worker.seenScope);
        assertSame(previous, LangChain4jManaged.current());

        when(invoker.method()).thenReturn(Worker.class.getMethod("fail", String.class));
        AgentInvocationException failure = assertThrows(
                AgentInvocationException.class,
                () -> AgentInvocationSupport.invoke(
                        invoker, invoked, listener, worker, args, new AgentModelResponse()));
        assertSame(worker.failure, failure.getCause().getCause());
        assertSame(previous, LangChain4jManaged.current());

        worker.suspension = new AgenticSystemSuspendedException(invoked);
        when(invoker.method()).thenReturn(Worker.class.getMethod("suspend", String.class));
        assertSame(
                worker.suspension,
                assertThrows(
                        AgenticSystemSuspendedException.class,
                        () -> AgentInvocationSupport.invoke(
                                invoker, invoked, listener, worker, args, new AgentModelResponse())));
        assertSame(previous, LangChain4jManaged.current());
    }

    @Test
    void transformsOnlyTheLastUserMessageAndPreservesMultimodalMetadata() {
        UserMessage earlier = UserMessage.from("earlier");
        ImageContent image = ImageContent.from("https://example.invalid/image.png");
        UserMessage last = UserMessage.builder()
                .name("user")
                .contents(Arrays.asList(TextContent.from("first"), image, TextContent.from("second")))
                .attributes(Collections.singletonMap("trace", "original"))
                .build();
        ChatRequest request =
                ChatRequest.builder().messages(Arrays.asList(earlier, last)).build();
        Object id = new Object();
        UserMessageTransformer transformer = (text, memoryId) -> {
            assertEquals("first\nsecond", text);
            assertSame(id, memoryId);
            return "transformed";
        };
        ChatRequest result = transformer.apply(request, id);
        UserMessage actual = (UserMessage) result.messages().get(1);
        assertSame(earlier, result.messages().get(0));
        assertEquals(Arrays.asList(TextContent.from("transformed"), image), actual.contents());
        assertEquals(last.name(), actual.name());
        assertEquals(last.attributes(), actual.attributes());
        assertEquals(3, last.contents().size());
        assertEquals(request.parameters(), result.parameters());
    }

    @Test
    void keepsUntransformedImagesAndRequestsWithoutUserMessages() {
        UserMessage imageOnly = UserMessage.from(ImageContent.from("https://example.invalid/image.png"));
        ChatRequest request = ChatRequest.builder().messages(imageOnly).build();
        UserMessageTransformer empty = (text, id) -> "";
        assertSame(imageOnly, empty.apply(request, null).messages().get(0));
        UserMessageTransformer filled = (text, id) -> "caption";
        UserMessage withText =
                (UserMessage) filled.apply(request, null).messages().get(0);
        assertEquals(TextContent.from("caption"), withText.contents().get(0));
        assertSame(imageOnly.contents().get(0), withText.contents().get(1));
        ChatRequest systemOnly =
                ChatRequest.builder().messages(SystemMessage.from("system")).build();
        assertSame(systemOnly, filled.apply(systemOnly, null));
    }

    /** @author 芋道源码 */
    public static final class Worker {
        AgenticScope seenScope;
        final RuntimeException failure = new IllegalStateException("failure");
        AgenticSystemSuspendedException suspension;

        public Object echo(String value) {
            seenScope = LangChain4jManaged.current(AgenticScope.class);
            return value;
        }

        public Object fail(String value) {
            throw failure;
        }

        public Object suspend(String value) {
            throw suspension;
        }
    }
}
