package dev.langchain4j.http.client.jdk;

import static org.assertj.core.api.Assertions.assertThat;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import dev.langchain4j.http.client.HttpMethod;
import dev.langchain4j.http.client.HttpRequest;
import dev.langchain4j.http.client.sse.DefaultServerSentEventParser;
import dev.langchain4j.http.client.sse.HttpStreamingEvent;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Flow;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Checks demand saturation against a real SSE transport on Java 8. The upstream
 * TCK's spec317 fixture requires billions of elements and is capped at 100; this
 * regression requests overflowing demand before any response is delivered.
 *
 * @author 芋道源码
 */
class Java8PublisherDemandOverflowTest {
    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void should_saturate_demand_without_rejecting_overflow(boolean backpressure) throws Exception {
        WireMockServer server = new WireMockServer(WireMockConfiguration.options().dynamicPort());
        server.start();
        try (JdkHttpClient client = JdkHttpClient.builder().streamingBackpressure(backpressure).build()) {
            StringBuilder body = new StringBuilder();
            for (int i = 0; i < 100; i++) body.append("data: ").append(i).append("\n\n");
            server.stubFor(WireMock.get("/events").willReturn(WireMock.aResponse()
                    .withHeader("Content-Type", "text/event-stream").withBody(body.toString())));
            HttpRequest request = HttpRequest.builder().method(HttpMethod.GET).url(server.baseUrl() + "/events").build();
            List<HttpStreamingEvent> events = new CopyOnWriteArrayList<>();
            CompletableFuture<Void> complete = new CompletableFuture<>();
            client.stream(request, new DefaultServerSentEventParser()).subscribe(new Flow.Subscriber<HttpStreamingEvent>() {
                @Override
                public void onSubscribe(Flow.Subscription subscription) {
                    subscription.request(Long.MAX_VALUE - 1);
                    subscription.request(Long.MAX_VALUE - 1);
                    subscription.request(Long.MAX_VALUE);
                }
                @Override
                public void onNext(HttpStreamingEvent event) { events.add(event); }
                @Override
                public void onError(Throwable error) { complete.completeExceptionally(error); }
                @Override
                public void onComplete() { complete.complete(null); }
            });
            complete.get(10, TimeUnit.SECONDS);
            assertThat(events).hasSize(101); // response headers plus every SSE item
        } finally {
            server.stop();
        }
    }
}
