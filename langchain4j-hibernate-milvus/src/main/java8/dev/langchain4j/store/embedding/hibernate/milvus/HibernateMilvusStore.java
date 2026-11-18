package dev.langchain4j.store.embedding.hibernate.milvus;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingSearchResult;
import dev.langchain4j.store.embedding.RelevanceScore;
import dev.langchain4j.store.embedding.filter.Filter;
import io.milvus.v2.client.MilvusClientV2;
import io.milvus.v2.common.ConsistencyLevel;
import io.milvus.v2.common.IndexParam;
import io.milvus.v2.service.vector.request.DeleteReq;
import io.milvus.v2.service.vector.request.QueryReq;
import io.milvus.v2.service.vector.request.SearchReq;
import io.milvus.v2.service.vector.request.UpsertReq;
import io.milvus.v2.service.vector.request.data.FloatVec;
import io.milvus.v2.service.vector.response.QueryResp.QueryResult;
import io.milvus.v2.service.vector.response.SearchResp.SearchResult;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.engine.spi.SessionFactoryImplementor;
import org.hibernate.engine.spi.SharedSessionContractImplementor;

/**
 * A Java 8 Milvus store using Hibernate 5 entity mappings.
 * Writes use the SDK and are independent of Hibernate transactions. Search and query return detached entities.
 *
 * @param <E> Hibernate-mapped entity type
 * @author 芋道源码
 */
public final class HibernateMilvusStore<E> {

    private final MilvusClientV2 client;
    private final SessionFactoryImplementor factory;
    private final String collection;
    private final MilvusEntityMapping<E> mapping;
    private final MilvusEntityFilter filters;

    /**
     * Uses the supplied client and factory, whose lifecycles remain the caller's responsibility.
     *
     * @param client configured Milvus client
     * @param factory configured Hibernate 5 session factory
     * @param entityType mapped entity
     * @param collection Milvus collection name
     * @param vectorProperty mapped Java property of type float[]
     * @param dimension vector dimension
     */
    public HibernateMilvusStore(
            MilvusClientV2 client, SessionFactory factory, Class<E> entityType,
            String collection, String vectorProperty, int dimension) {
        this.client = Objects.requireNonNull(client, "client");
        this.factory = factory.unwrap(SessionFactoryImplementor.class);
        this.collection = MilvusEntityMapping.identifier(collection);
        this.mapping = new MilvusEntityMapping<>(
                entityType, this.factory.getMetamodel().entityPersister(entityType), vectorProperty, dimension);
        this.filters = new MilvusEntityFilter(mapping::scalar);
    }

    /** Creates or checks the collection, ensures a COSINE index, and waits for it to load. */
    public void initializeSchema() {
        initializeSchema(true);
    }

    /**
     * Creates or checks the collection, ensures a COSINE index and requests loading.
     *
     * @param waitForSchema wait for index completion and loading via the SDK
     */
    public void initializeSchema(boolean waitForSchema) {
        new MilvusCollectionSchema(client, collection, mapping).initialize(waitForSchema);
    }

    /** Upserts a complete entity using its application-assigned identifier. */
    public void upsert(E entity) {
        long count = client.upsert(UpsertReq.builder().collectionName(collection)
                .data(Collections.singletonList(mapping.row(entity))).build()).getUpsertCnt();
        if (count != 1) {
            throw new IllegalStateException("Milvus upsert returned " + count + " rows");
        }
    }

    /** Deletes an entity by its application-assigned identifier. */
    public void remove(Object id) {
        client.delete(DeleteReq.builder().collectionName(collection)
                .ids(Collections.singletonList(mapping.identifierValue(id))).build());
    }

    /** Searches with cosine similarity and optional scalar filters over mapped Java properties. */
    public EmbeddingSearchResult<E> search(EmbeddingSearchRequest request) {
        checkLimit(request.maxResults());
        float[] vector = mapping.validateVector(request.queryEmbedding().vector());
        SearchReq.SearchReqBuilder query = SearchReq.builder()
                .collectionName(collection)
                .annsField(mapping.vectorColumn())
                .metricType(IndexParam.MetricType.COSINE)
                .consistencyLevel(ConsistencyLevel.STRONG)
                .data(Collections.singletonList(new FloatVec(vector)))
                .outputFields(mapping.outputColumns())
                .topK(request.maxResults());
        if (request.filter() != null) {
            query.filter(filters.expression(request.filter()));
        }
        List<List<SearchResult>> responses = client.search(query.build()).getSearchResults();
        List<EmbeddingMatch<E>> matches = new ArrayList<>();
        if (!responses.isEmpty() && !responses.get(0).isEmpty()) {
            try (Session session = factory.openSession()) {
                SharedSessionContractImplementor context = (SharedSessionContractImplementor) session;
                for (SearchResult result : responses.get(0)) {
                    double score = RelevanceScore.fromCosineSimilarity(Math.max(-1, Math.min(1, result.getScore())));
                    if (score >= request.minScore()) {
                        float[] matchVector = mapping.readVector(result.getEntity());
                        E entity = mapping.hydrate(context, result.getId(), result.getEntity(), matchVector);
                        matches.add(new EmbeddingMatch<>(score, result.getId().toString(), Embedding.from(matchVector), entity));
                    }
                }
            }
        }
        return new EmbeddingSearchResult<>(matches);
    }

    /** Queries detached entities using a scalar filter and an explicit result limit. */
    public List<E> query(Filter filter, int limit) {
        checkLimit(limit);
        List<String> fields = mapping.outputColumns();
        fields.add(mapping.idColumn());
        List<QueryResult> rows = client.query(QueryReq.builder()
                .collectionName(collection).consistencyLevel(ConsistencyLevel.STRONG)
                .filter(filters.expression(Objects.requireNonNull(filter, "filter")))
                .outputFields(fields).limit(limit).build()).getQueryResults();
        List<E> entities = new ArrayList<>();
        try (Session session = factory.openSession()) {
            SharedSessionContractImplementor context = (SharedSessionContractImplementor) session;
            for (QueryResult result : rows) {
                Map<String, Object> row = result.getEntity();
                entities.add(mapping.hydrate(context, row.get(mapping.idColumn()), row, mapping.readVector(row)));
            }
        }
        return entities;
    }

    private static void checkLimit(int limit) {
        if (limit < 1 || limit > 16384) {
            throw new IllegalArgumentException("Milvus result limit must be between 1 and 16384");
        }
    }
}
