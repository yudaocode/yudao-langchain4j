package cn.iocoder.langchain4j.boot2;

import static org.assertj.core.api.Assertions.assertThat;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.rag.content.Content;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import dev.langchain4j.service.spring.AiService;
import java.util.Collections;
import java.util.concurrent.TimeUnit;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.SpringBootVersion;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;

/**
 * Exercises Boot 2 discovery, immutable property binding, AI service scanning, tools and RAG on Java 8.
 * @author 芋道源码
 */
class Boot2RuntimeTest {
    @Test
    @Timeout(30)
    void discoversStartersAndWiresToolsAndRagOnJava8() throws Exception {
        assertThat(System.getProperty("java.specification.version")).isEqualTo("1.8");
        assertThat(SpringBootVersion.getVersion()).isEqualTo("2.7.18");
        try (MockWebServer server = new MockWebServer()) {
            server.start();
            server.enqueue(json("{\"id\":\"one\",\"object\":\"chat.completion\",\"created\":1,\"choices\":[{\"index\":0,\"message\":{\"role\":\"assistant\",\"tool_calls\":[{\"id\":\"call-1\",\"type\":\"function\",\"function\":{\"name\":\"add\",\"arguments\":\"{\\\"a\\\":2,\\\"b\\\":3}\"}}]},\"finish_reason\":\"tool_calls\"}]}"));
            server.enqueue(json("{\"id\":\"two\",\"object\":\"chat.completion\",\"created\":1,\"choices\":[{\"index\":0,\"message\":{\"role\":\"assistant\",\"content\":\"5 / YUDAO-8\"},\"finish_reason\":\"stop\"}]}"));
            try (ConfigurableApplicationContext context = new SpringApplicationBuilder(App.class)
                    .web(WebApplicationType.NONE)
                    .properties("spring.main.banner-mode=off", "logging.level.root=WARN",
                            "langchain4j.open-ai.chat-model.api-key=local-test-key",
                            "langchain4j.open-ai.chat-model.model-name=compat-model",
                            "langchain4j.open-ai.chat-model.base-url=" + server.url("/v1"),
                            "langchain4j.open-ai.chat-model.timeout=5s",
                            "langchain4j.open-ai.chat-model.max-retries=0")
                    .run()) {
                Assistant assistant = context.getBean(Assistant.class);
                assertThat(assistant.ask("Add 2 and 3; include the release code")).isEqualTo("5 / YUDAO-8");
                assertThat(assistant.marker()).isEqualTo("default-method-on-java8");
                String first = server.takeRequest(5, TimeUnit.SECONDS).getBody().readUtf8();
                assertThat(first).contains("compat-model", "YUDAO-8", "add");
                String second = server.takeRequest(5, TimeUnit.SECONDS).getBody().readUtf8();
                com.fasterxml.jackson.databind.JsonNode responseMessages = new com.fasterxml.jackson.databind.ObjectMapper()
                        .readTree(second).get("messages");
                assertThat(responseMessages.get(2).get("role").asText()).isEqualTo("tool");
                assertThat(responseMessages.get(2).get("tool_call_id").asText()).isEqualTo("call-1");
                assertThat(responseMessages.get(2).get("content").asText()).isEqualTo("5");
            }
        }
    }

    private static MockResponse json(String body) {
        return new MockResponse().setHeader("Content-Type", "application/json").setBody(body);
    }

    /** @author 芋道源码 */
    @AiService
    interface Assistant {
        String ask(String prompt);
        default String marker() { return "default-method-on-java8"; }
    }

    /** @author 芋道源码 */
    static class Calculator {
        @Tool("Add two integers")
        int add(@dev.langchain4j.agent.tool.P(name = "a") int a, @dev.langchain4j.agent.tool.P(name = "b") int b) { return a + b; }
    }

    /** @author 芋道源码 */
    @SpringBootConfiguration
    @EnableAutoConfiguration
    static class App {
        @Bean Calculator calculator() { return new Calculator(); }
        @Bean ContentRetriever contentRetriever() {
            return query -> Collections.singletonList(Content.from("The release code is YUDAO-8."));
        }
    }
}
