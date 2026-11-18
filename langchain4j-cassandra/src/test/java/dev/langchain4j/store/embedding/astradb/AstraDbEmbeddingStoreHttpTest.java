package dev.langchain4j.store.embedding.astradb;

import static org.assertj.core.api.Assertions.assertThat;

import com.dtsx.astra.sdk.AstraDB;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sun.net.httpserver.HttpServer;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import java.net.InetSocketAddress;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

/**
 * @author 芋道源码
 */
class AstraDbEmbeddingStoreHttpTest {

    @Test
    void mapsAstraDataApiDocumentsOnJava8() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        AtomicReference<JsonNode> inserted = new AtomicReference<>();
        AtomicReference<String> token = new AtomicReference<>();
        String apiPath = "/.apps.astra.datastax.com/api/json";
        HttpServer http = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        http.createContext(apiPath, exchange -> {
            try {
                token.set(exchange.getRequestHeaders().getFirst("Authorization"));
                JsonNode request = mapper.readTree(exchange.getRequestBody());
                JsonNode reply;
                if (request.has("findNamespaces")) {
                    reply = mapper.readTree("{\"status\":{\"namespaces\":[\"keyspace\"]}}");
                } else if (request.has("findCollections")) {
                    reply = mapper.readTree("{\"status\":{\"collections\":[{\"name\":\"documents\"}]}}");
                } else if (request.has("insertOne")) {
                    JsonNode document = request.path("insertOne").path("document");
                    inserted.set(document);
                    reply = mapper.createObjectNode().set("status", mapper.createObjectNode()
                            .set("insertedIds", mapper.createArrayNode().add(document.path("_id").asText())));
                } else if (request.has("find")) {
                    ObjectNode document = inserted.get().deepCopy();
                    document.put("$similarity", 0.99);
                    reply = mapper.createObjectNode().set("data", mapper.createObjectNode()
                            .set("documents", mapper.createArrayNode().add(document)));
                } else {
                    exchange.sendResponseHeaders(400, -1);
                    return;
                }
                byte[] response = mapper.writeValueAsBytes(reply);
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, response.length);
                exchange.getResponseBody().write(response);
            } finally {
                exchange.close();
            }
        });
        http.start();
        try {
            String endpoint = "http://127.0.0.1:" + http.getAddress().getPort() + apiPath;
            AstraDbEmbeddingStore store = new AstraDbEmbeddingStore(new AstraDB("test-token", endpoint, "keyspace")
                    .collection("documents"));
            Embedding vector = Embedding.from(new float[] {1.0f, 0.0f, 0.0f});
            String id = store.add(vector, TextSegment.from("芋道文档"));
            assertThat(token).hasValue("Bearer test-token");
            assertThat(inserted.get().path("_id").asText()).isEqualTo(id);
            assertThat(inserted.get().path("$vector").size()).isEqualTo(3);
            assertThat(store.search(EmbeddingSearchRequest.builder()
                            .queryEmbedding(vector)
                            .maxResults(1)
                            .build())
                    .matches()).singleElement()
                    .satisfies(match -> {
                        assertThat(match.embeddingId()).isEqualTo(id);
                        assertThat(match.embedded().text()).isEqualTo("芋道文档");
                    });
        } finally {
            http.stop(0);
        }
    }
}
