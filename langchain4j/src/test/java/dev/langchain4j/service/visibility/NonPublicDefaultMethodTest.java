package dev.langchain4j.service.visibility;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.decision.DecisionModel;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.decision.DecisionServices;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

/**
 * @author 芋道源码
 */
class NonPublicDefaultMethodTest {

    interface LocalService {

        default String join(String... words) {
            return String.join(" ", words);
        }

        default String noArguments() {
            return "local";
        }

        default void fail(AtomicInteger calls) throws IllegalAccessException {
            calls.incrementAndGet();
            throw new IllegalAccessException("failure from the user default method");
        }
    }

    @Test
    void aiServicesInvokesDefaultMethodsOnANonPublicInterface() {
        LocalService service = AiServices.create(LocalService.class, mock(ChatModel.class));

        assertThat(service.join("first", "second")).isEqualTo("first second");
        assertThat(service.noArguments()).isEqualTo("local");
        assertPropagatesUserExceptionOnce(service);
    }

    @Test
    void decisionServicesInvokesDefaultMethodsOnANonPublicInterface() {
        LocalService service = DecisionServices.builder(LocalService.class)
                .decisionModel(mock(DecisionModel.class))
                .build();

        assertThat(service.join("first", "second")).isEqualTo("first second");
        assertThat(service.noArguments()).isEqualTo("local");
        assertPropagatesUserExceptionOnce(service);
    }

    private static void assertPropagatesUserExceptionOnce(LocalService service) {
        AtomicInteger calls = new AtomicInteger();
        assertThatThrownBy(() -> service.fail(calls))
                .isInstanceOf(IllegalAccessException.class)
                .hasMessage("failure from the user default method");
        assertThat(calls).hasValue(1);
    }
}
