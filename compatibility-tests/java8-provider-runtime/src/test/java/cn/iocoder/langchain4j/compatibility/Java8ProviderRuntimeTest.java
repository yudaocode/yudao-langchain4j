package cn.iocoder.langchain4j.compatibility;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.http.client.jdk.JdkHttpClient;
import dev.langchain4j.http.client.jdk.JdkHttpClientBuilder;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatModelStreamingEvent;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.PartialResponse;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.openai.OpenAiEmbeddingModel;
import dev.langchain4j.model.openai.OpenAiImageModel;
import dev.langchain4j.model.openai.OpenAiStreamingChatModel;
import dev.langchain4j.service.AiServices;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.ai.compat.jdkhttp.Flow;

/**
 * Sends real HTTP and SSE requests from a Java 8 VM without the upstream
 * Java17-only mock-server libraries on the consumer classpath.
 *
 * @author 芋道源码
 */
@Timeout(20)
class Java8ProviderRuntimeTest {
    private final List<JdkHttpClient> clients = new ArrayList<>();
    private MockWebServer server;

    @BeforeEach
    void start() throws Exception {
        assertThat(System.getProperty("java.specification.version")).isEqualTo("1.8");
        server = new MockWebServer();
        server.start(java.net.InetAddress.getByName("127.0.0.1"), 0);
    }

    @AfterEach
    void close() throws Exception {
        for (JdkHttpClient client : clients) client.close();
        server.shutdown();
    }

    private JdkHttpClientBuilder transport() {
        return new JdkHttpClientBuilder() {
            @Override public JdkHttpClient build() {
                JdkHttpClient client = super.build();
                clients.add(client);
                return client;
            }
        };
    }

    private OpenAiChatModel chatModel() {
        return OpenAiChatModel.builder().baseUrl(server.url("/v1").toString())
                .apiKey("local-fixture").modelName("mock").maxRetries(0).httpClientBuilder(transport()).build();
    }

    private OpenAiStreamingChatModel streamingModel() {
        return OpenAiStreamingChatModel.builder().baseUrl(server.url("/v1").toString())
                .apiKey("local-fixture").modelName("mock").httpClientBuilder(transport()).build();
    }

    private void reply(String body) {
        server.enqueue(new MockResponse().addHeader("Content-Type", "application/json").setBody(body));
    }

    private static String answer(String content) {
        return "{\"id\":\"chat-1\",\"object\":\"chat.completion\",\"created\":1,\"model\":\"mock\","
                + "\"choices\":[{\"index\":0,\"message\":{\"role\":\"assistant\",\"content\":\"" + content
                + "\"},\"finish_reason\":\"stop\"}],\"usage\":{\"prompt_tokens\":2,\"completion_tokens\":1,\"total_tokens\":3}}";
    }

    private void streamReply() {
        server.enqueue(new MockResponse().addHeader("Content-Type", "text/event-stream").setBody(
                "data: {\"id\":\"s1\",\"model\":\"mock\",\"choices\":[{\"index\":0,\"delta\":{\"role\":\"assistant\",\"content\":\"hello\"}}]}\n\n"
                + "data: {\"id\":\"s1\",\"model\":\"mock\",\"choices\":[{\"index\":0,\"delta\":{},\"finish_reason\":\"stop\"}]}\n\n"
                + "data: [DONE]\n\n"));
    }

    @Test
    void syncAndAsyncChatUseTheNative8Transport() throws Exception {
        reply(answer("first"));
        reply(answer("second"));
        OpenAiChatModel model = chatModel();
        assertThat(model.chat("one")).isEqualTo("first");
        assertThat(model.chatAsync("two").get(10, TimeUnit.SECONDS)).isEqualTo("second");
        JsonNode first = new ObjectMapper().readTree(server.takeRequest().getBody().readUtf8());
        assertThat(first.path("messages").get(0).path("content").asText()).isEqualTo("one");
        assertThat(server.takeRequest().getPath()).isEqualTo("/v1/chat/completions");
    }

    @Test
    void callbackStreamingDeliversBothTokensAndCompletion() throws Exception {
        streamReply();
        CompletableFuture<ChatResponse> complete = new CompletableFuture<>();
        StringBuilder tokens = new StringBuilder();
        streamingModel().chat("hello", new StreamingChatResponseHandler() {
            @Override public void onPartialResponse(String token) { tokens.append(token); }
            @Override public void onCompleteResponse(ChatResponse response) { complete.complete(response); }
            @Override public void onError(Throwable error) { complete.completeExceptionally(error); }
        });
        assertThat(complete.get(10, TimeUnit.SECONDS).aiMessage().text()).isEqualTo("hello");
        assertThat(tokens.toString()).isEqualTo("hello");
    }

    @Test
    void publisherStreamingIsColdAndCompletesOnNative8() throws Exception {
        streamReply();
        Flow.Publisher<ChatModelStreamingEvent> publisher = streamingModel().chat(
                ChatRequest.builder().messages(UserMessage.from("hello")).build());
        assertThat(server.getRequestCount()).isZero();
        List<ChatModelStreamingEvent> events = new CopyOnWriteArrayList<>();
        CompletableFuture<Void> complete = new CompletableFuture<>();
        publisher.subscribe(new Flow.Subscriber<ChatModelStreamingEvent>() {
            @Override public void onSubscribe(Flow.Subscription subscription) { subscription.request(Long.MAX_VALUE); }
            @Override public void onNext(ChatModelStreamingEvent event) { events.add(event); }
            @Override public void onError(Throwable error) { complete.completeExceptionally(error); }
            @Override public void onComplete() { complete.complete(null); }
        });
        complete.get(10, TimeUnit.SECONDS);
        assertThat(events.stream().filter(PartialResponse.class::isInstance).map(PartialResponse.class::cast)
                .map(PartialResponse::text)).containsExactly("hello");
        assertThat(server.getRequestCount()).isEqualTo(1);
    }

    @Test
    void aiServiceExecutesToolsAndDefaultInterfaceMethods() throws Exception {
        reply("{\"id\":\"tool-1\",\"model\":\"mock\",\"choices\":[{\"index\":0,\"message\":{\"role\":\"assistant\",\"content\":null,"
                + "\"tool_calls\":[{\"id\":\"call-1\",\"type\":\"function\",\"function\":{\"name\":\"add\",\"arguments\":\"{\\\"a\\\":2,\\\"b\\\":3}\"}}]},\"finish_reason\":\"tool_calls\"}]}");
        reply(answer("5"));
        MathTools tools = new MathTools();
        Assistant assistant = AiServices.builder(Assistant.class).chatModel(chatModel()).tools(tools).build();
        assertThat(assistant.askDefault()).isEqualTo("5");
        assertThat(tools.calls.get()).isEqualTo(1);
        server.takeRequest();
        JsonNode second = new ObjectMapper().readTree(server.takeRequest().getBody().readUtf8());
        assertThat(second.path("messages").findValuesAsText("role")).contains("tool");
        assertThat(second.path("messages").findValuesAsText("tool_call_id")).contains("call-1");
    }

    @Test
    void embeddingsDecodeVectorsOnNative8() {
        reply("{\"object\":\"list\",\"model\":\"mock\",\"data\":[{\"object\":\"embedding\",\"index\":0,\"embedding\":[0.1,0.2,0.3]}],\"usage\":{\"prompt_tokens\":2,\"total_tokens\":2}}");
        OpenAiEmbeddingModel model = OpenAiEmbeddingModel.builder().baseUrl(server.url("/v1").toString())
                .apiKey("local-fixture").modelName("mock").maxRetries(0).httpClientBuilder(transport()).build();
        assertThat(model.embed("words").content().vector()).containsExactly(0.1f, 0.2f, 0.3f);
    }

    @Test
    void imageGenerationDecodesTheProviderResponseOnNative8() {
        reply("{\"created\":1,\"data\":[{\"url\":\"https://example.com/image.png\"}]}");
        OpenAiImageModel model = OpenAiImageModel.builder().baseUrl(server.url("/v1").toString())
                .apiKey("local-fixture").modelName("mock").httpClientBuilder(transport()).build();
        assertThat(model.generate("a tree").content().url().toString()).isEqualTo("https://example.com/image.png");
    }

    @Test
    @Timeout(180)
    void nativeEmbeddingModelFeedsTheRagPrompt() throws Exception {
        dev.langchain4j.model.embedding.onnx.allminilml6v2q.AllMiniLmL6V2QuantizedEmbeddingModel model =
                new dev.langchain4j.model.embedding.onnx.allminilml6v2q.AllMiniLmL6V2QuantizedEmbeddingModel();
        String question = "What is the release code?";
        dev.langchain4j.data.embedding.Embedding vector = model.embed(question).content();
        assertThat(vector.vector()).hasSize(384);
        dev.langchain4j.store.embedding.inmemory.InMemoryEmbeddingStore<dev.langchain4j.data.segment.TextSegment> store =
                new dev.langchain4j.store.embedding.inmemory.InMemoryEmbeddingStore<>();
        store.add(vector, dev.langchain4j.data.segment.TextSegment.from("The release code is YUDAO-8."));
        dev.langchain4j.rag.content.retriever.ContentRetriever retriever =
                dev.langchain4j.rag.content.retriever.EmbeddingStoreContentRetriever.builder()
                        .embeddingModel(model).embeddingStore(store).maxResults(1).minScore(0.9).build();
        reply(answer("YUDAO-8"));
        Assistant assistant = AiServices.builder(Assistant.class).chatModel(chatModel()).contentRetriever(retriever).build();
        assertThat(assistant.chat(question)).isEqualTo("YUDAO-8");
        JsonNode request = new ObjectMapper().readTree(server.takeRequest().getBody().readUtf8());
        assertThat(request.path("messages").toString()).contains("The release code is YUDAO-8.");
    }

    /** @author 芋道源码 */
    public interface Assistant {
        String chat(@dev.langchain4j.service.UserMessage String question);
        default String askDefault() { return chat("add two and three"); }
    }

    /** @author 芋道源码 */
    public static final class MathTools {
        final AtomicInteger calls = new AtomicInteger();
        @Tool("Add two integers")
        public int add(@P(name = "a") int a, @P(name = "b") int b) { calls.incrementAndGet(); return a + b; }
    }
}
