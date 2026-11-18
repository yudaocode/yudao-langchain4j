package dev.langchain4j.store.embedding.elasticsearch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.RETURNS_SELF;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.Refresh;
import co.elastic.clients.elasticsearch.core.BulkRequest;
import co.elastic.clients.elasticsearch.core.BulkResponse;
import com.sun.net.httpserver.HttpServer;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.rag.content.retriever.elasticsearch.ElasticsearchContentRetriever;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.apache.commons.io.IOUtils;
import org.apache.http.HttpHost;
import org.elasticsearch.client.RestClient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;

@SuppressWarnings("removal")
class ElasticsearchEmbeddingStoreRefreshTest {

    @ParameterizedTest
    @EnumSource(Refresh.class)
    void should_apply_refresh_to_vector_and_text_writes(Refresh refresh) throws IOException {
        ElasticsearchClient client = mockClient();
        ElasticsearchEmbeddingStore store = ElasticsearchEmbeddingStore.builder()
                .client(client)
                .indexName("refresh-test")
                .refresh(refresh)
                .build();

        writeVectorsAndText(store);

        assertWriteRequests(client, refresh);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void should_keep_refresh_disabled_by_default(boolean useConstructor) throws IOException {
        ElasticsearchClient client = mockClient();
        ElasticsearchEmbeddingStore store = useConstructor
                ? new ElasticsearchEmbeddingStore(
                        ElasticsearchConfigurationKnn.builder().build(), client, "refresh-test")
                : ElasticsearchEmbeddingStore.builder()
                        .client(client)
                        .indexName("refresh-test")
                        .build();

        writeVectorsAndText(store);

        assertWriteRequests(client, Refresh.False);
    }

    @ParameterizedTest
    @EnumSource(Refresh.class)
    void should_apply_refresh_with_rest_client(Refresh refresh) throws IOException {
        try (BulkFixture fixture = new BulkFixture();
                RestClient restClient = RestClient.builder(HttpHost.create(fixture.address())).build()) {
            ElasticsearchEmbeddingStore store = ElasticsearchEmbeddingStore.builder()
                    .restClient(restClient)
                    .indexName("refresh-test")
                    .refresh(refresh)
                    .build();

            writeVectorsAndText(store);
            fixture.assertRefresh(refresh);
        }
    }

    @ParameterizedTest
    @EnumSource(Refresh.class)
    void should_apply_refresh_with_server_url(Refresh refresh) throws IOException {
        try (BulkFixture fixture = new BulkFixture()) {
            ElasticsearchEmbeddingStore store = ElasticsearchEmbeddingStore.builder()
                    .serverUrl(fixture.address())
                    .indexName("refresh-test")
                    .refresh(refresh)
                    .build();
            try {
                writeVectorsAndText(store);
                fixture.assertRefresh(refresh);
            } finally {
                store.client.close();
            }
        }
    }

    @ParameterizedTest
    @EnumSource(Refresh.class)
    void should_apply_refresh_to_removal_by_id(Refresh refresh) throws IOException {
        ElasticsearchClient client = mockClient();
        ElasticsearchEmbeddingStore store = ElasticsearchEmbeddingStore.builder()
                .client(client)
                .refresh(refresh)
                .build();

        store.removeAll(List.of("old-chunk"));

        ArgumentCaptor<BulkRequest> request = ArgumentCaptor.forClass(BulkRequest.class);
        verify(client).bulk(request.capture());
        assertThat(request.getValue().refresh()).isEqualTo(refresh);
        assertThat(request.getValue().operations()).singleElement().satisfies(operation -> {
            assertThat(operation.isDelete()).isTrue();
            assertThat(operation.delete().id()).isEqualTo("old-chunk");
        });
    }

    @Test
    void should_fall_back_to_default_when_refresh_is_null() throws IOException {
        ElasticsearchClient client = mockClient();
        ElasticsearchEmbeddingStore store = ElasticsearchEmbeddingStore.builder()
                .client(client)
                .indexName("refresh-test")
                .refresh(null)
                .build();

        writeVectorsAndText(store);

        assertWriteRequests(client, Refresh.False);
    }

    @ParameterizedTest
    @EnumSource(Refresh.class)
    void should_apply_refresh_to_content_retriever_writes(Refresh refresh) throws IOException {
        ElasticsearchClient client = mockClient();
        ElasticsearchContentRetriever retriever = ElasticsearchContentRetriever.builder()
                .client(client)
                .indexName("refresh-test")
                .refresh(refresh)
                .build();

        writeVectorsAndText(retriever);

        assertWriteRequests(client, refresh);
    }

    private static ElasticsearchClient mockClient() throws IOException {
        ElasticsearchClient client = mock(ElasticsearchClient.class, RETURNS_SELF);
        when(client.bulk(any(BulkRequest.class)))
                .thenReturn(BulkResponse.of(
                        response -> response.took(0).errors(false).items(List.of())));
        return client;
    }

    private static void writeVectorsAndText(AbstractElasticsearchEmbeddingStore store) {
        store.addAll(List.of(Embedding.from(new float[] {1, 0}), Embedding.from(new float[] {0, 1})));
        store.addAllText(List.of("first chunk", "second chunk"));
    }

    private static void assertWriteRequests(ElasticsearchClient client, Refresh refresh) throws IOException {
        ArgumentCaptor<BulkRequest> requests = ArgumentCaptor.forClass(BulkRequest.class);
        verify(client, times(2)).bulk(requests.capture());
        assertThat(requests.getAllValues()).allSatisfy(request -> {
            assertThat(request.refresh()).isEqualTo(refresh);
            assertThat(request.operations()).hasSize(2).allSatisfy(operation -> {
                assertThat(operation.isIndex()).isTrue();
                assertThat(operation.index().index()).isEqualTo("refresh-test");
            });
        });
    }

    /**
     * Responds to bulk requests through the installed Elasticsearch REST client.
     *
     * @author 芋道源码
     */
    private static final class BulkFixture implements AutoCloseable {

        private final HttpServer server;
        private final List<URI> requests = new CopyOnWriteArrayList<>();

        BulkFixture() throws IOException {
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/_bulk", exchange -> {
                try {
                    requests.add(exchange.getRequestURI());
                    IOUtils.consume(exchange.getRequestBody());
                    byte[] response = "{\"took\":0,\"errors\":false,\"items\":[]}"
                            .getBytes(StandardCharsets.UTF_8);
                    exchange.getResponseHeaders().set("X-Elastic-Product", "Elasticsearch");
                    exchange.getResponseHeaders().set("Content-Type", "application/json");
                    exchange.sendResponseHeaders(200, response.length);
                    exchange.getResponseBody().write(response);
                } finally {
                    exchange.close();
                }
            });
            server.start();
        }

        String address() {
            return "http://127.0.0.1:" + server.getAddress().getPort();
        }

        void assertRefresh(Refresh refresh) {
            assertThat(requests).hasSize(2).allSatisfy(uri -> {
                assertThat(uri.getPath()).isEqualTo("/_bulk");
                assertThat(uri.getQuery()).contains("refresh=" + refresh.jsonValue());
            });
        }

        @Override
        public void close() {
            server.stop(0);
        }
    }
}
