package dev.langchain4j.model.ollama.consumer;

import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.model.ollama.OllamaChatModel;
import dev.langchain4j.model.ollama.OllamaStreamingChatModel;
import org.junit.jupiter.api.Test;

/**
 * Exercises the public Ollama builders from a consumer package.
 *
 * @author 芋道源码
 */
class OllamaBuilderCompatibilityTest {

    @Test
    void discoversAndUsesBothBuildersFromOutsideTheProviderPackage() {
        assertThat(Models.class.getDeclaredClasses()).isEmpty();
        assertThat(Models.chat().defaultRequestParameters().modelName()).isEqualTo("local-model");
        assertThat(Models.streaming().defaultRequestParameters().modelName()).isEqualTo("local-model");
    }

    /**
     * Holds the inherited builder calls in the class inspected by reflection.
     *
     * @author 芋道源码
     */
    static class Models {

        static OllamaChatModel chat() {
            return OllamaChatModel.builder()
                    .baseUrl("http://127.0.0.1:11434")
                    .modelName("local-model")
                    .build();
        }

        static OllamaStreamingChatModel streaming() {
            return OllamaStreamingChatModel.builder()
                    .baseUrl("http://127.0.0.1:11434")
                    .modelName("local-model")
                    .build();
        }
    }
}
