package dev.langchain4j.rag.easy;

import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.loader.FileSystemDocumentLoader;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.rag.content.Content;
import dev.langchain4j.rag.content.retriever.EmbeddingStoreContentRetriever;
import dev.langchain4j.rag.query.Query;
import dev.langchain4j.store.embedding.EmbeddingStoreIngestor;
import dev.langchain4j.store.embedding.inmemory.InMemoryEmbeddingStore;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author 芋道源码
 */
class EasyRagLocalTest {

    @Test
    void discoversDefaultParserSplitterAndEmbeddingModel(@TempDir Path directory) throws IOException {
        Path policy = directory.resolve("policy.html");
        String policyText = "Booking cancellations are free up to 17 days before pickup.";
        String html = "<!DOCTYPE html><html><body><p>" + policyText + "</p></body></html>";
        Files.write(policy, html.getBytes(StandardCharsets.UTF_8));

        Document document = FileSystemDocumentLoader.loadDocument(policy);
        assertThat(document.text().trim()).isEqualTo(policyText);

        InMemoryEmbeddingStore<TextSegment> store = new InMemoryEmbeddingStore<>();
        EmbeddingStoreIngestor.ingest(document, store);
        List<Content> results = EmbeddingStoreContentRetriever.from(store).retrieve(Query.from(policyText));

        assertThat(results).singleElement().satisfies(content ->
                assertThat(content.textSegment().text().trim()).isEqualTo(policyText));
    }
}
