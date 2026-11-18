package dev.langchain4j.docu.chatbot.updater;

import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.nio.file.Paths;
import java.util.List;
import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.DocumentSplitter;
import dev.langchain4j.data.document.loader.FileSystemDocumentLoader;
import dev.langchain4j.data.document.splitter.DocumentSplitters;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.googleai.GoogleAiEmbeddingModel;
import dev.langchain4j.model.openai.OpenAiTokenCountEstimator;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.EmbeddingStoreIngestor;
import dev.langchain4j.store.embedding.milvus.MilvusEmbeddingStore;
import io.milvus.client.MilvusServiceClient;
import io.milvus.param.ConnectParam;
import io.milvus.param.collection.DropCollectionParam;
import io.milvus.param.collection.RenameCollectionParam;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Updater {

    private static final Logger log = LoggerFactory.getLogger(Updater.class);

    private static final String COLLECTION_NAME = "docu"; // chatbot app expects this name
    private static final String COLLECTION_NAME_OLD = "docu_old";

    public static void main(String[] args) {

        Path docsPath = Paths.get("").toAbsolutePath().resolve("docs/docs");
        EmbeddingModel embeddingModel = GoogleAiEmbeddingModel.builder()
                .apiKey(System.getenv("GOOGLE_AI_GEMINI_API_KEY"))
                .modelName("gemini-embedding-001") // chatbot app expects this embedding model
                .build();

        update(docsPath, System.getenv("MILVUS_URI"), System.getenv("MILVUS_API_KEY"), embeddingModel);
    }

    static List<Document> loadDocuments(Path docsPath) {
        PathMatcher markdown = FileSystems.getDefault().getPathMatcher("glob:**.{md,mdx}");
        return FileSystemDocumentLoader.loadDocumentsRecursively(docsPath, markdown);
    }

    static void update(Path docsPath, String milvusUri, String milvusToken, EmbeddingModel embeddingModel) {
        List<Document> documents = loadDocuments(docsPath);
        DocumentSplitter documentSplitter = DocumentSplitters.recursive(
                600, 150, new OpenAiTokenCountEstimator("gpt-5"));

        MilvusServiceClient milvusServiceClient = new MilvusServiceClient(ConnectParam.newBuilder()
                .withUri(milvusUri)
                .withToken(milvusToken)
                .build());
        try {
            updateCollection(docsPath, documents, documentSplitter, embeddingModel, milvusServiceClient);
        } finally {
            milvusServiceClient.close();
        }
    }

    private static void updateCollection(
            Path docsPath,
            List<Document> documents,
            DocumentSplitter documentSplitter,
            EmbeddingModel embeddingModel,
            MilvusServiceClient milvusServiceClient) {
        log.info("docs path: {}", docsPath);
        log.info("about to rename collection '" + COLLECTION_NAME + "' into '" + COLLECTION_NAME_OLD + "'");
        milvusServiceClient.renameCollection(RenameCollectionParam.newBuilder()
                .withOldCollectionName(COLLECTION_NAME)
                .withNewCollectionName(COLLECTION_NAME_OLD)
                .build());
        log.info("renamed collection '" + COLLECTION_NAME + "' into '" + COLLECTION_NAME_OLD + "'");

        EmbeddingStore<TextSegment> embeddingStore = MilvusEmbeddingStore.builder()
                .milvusClient(milvusServiceClient)
                .collectionName(COLLECTION_NAME)
                .dimension(embeddingModel.dimension())
                .build();

        EmbeddingStoreIngestor ingestor = EmbeddingStoreIngestor.builder()
                .documentTransformer(document -> {
                    String absoluteDirectory = document.metadata().getString("absolute_directory_path");
                    Path relativeDirectory = docsPath.toAbsolutePath().normalize()
                            .relativize(Paths.get(absoluteDirectory).toAbsolutePath().normalize());
                    document.metadata().put("relative_directory_path", relativeDirectory.toString());
                    document.metadata().remove("absolute_directory_path");
                    return document;
                })
                .documentSplitter(documentSplitter)
                .embeddingModel(embeddingModel)
                .embeddingStore(embeddingStore)
                .build();

        try {
            ingestor.ingest(documents);

            log.info("about to remove collection '" + COLLECTION_NAME_OLD + "'");
            milvusServiceClient.dropCollection(DropCollectionParam.newBuilder()
                    .withCollectionName(COLLECTION_NAME_OLD)
                    .build());
            log.info("removed collection '" + COLLECTION_NAME_OLD + "'");

        } catch (Exception e) {

            log.info("Failed to ingest", e);

            // rollback
            log.info("rollback: about to rename collection '" + COLLECTION_NAME_OLD + "' into '" + COLLECTION_NAME + "'");
            milvusServiceClient.renameCollection(RenameCollectionParam.newBuilder()
                    .withOldCollectionName(COLLECTION_NAME_OLD)
                    .withNewCollectionName(COLLECTION_NAME)
                    .build());
            log.info("rollback: renamed collection '" + COLLECTION_NAME_OLD + "' into '" + COLLECTION_NAME + "'");
        }
    }
}
