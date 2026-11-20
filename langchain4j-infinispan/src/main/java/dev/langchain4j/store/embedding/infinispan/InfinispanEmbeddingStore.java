package dev.langchain4j.store.embedding.infinispan;

import static dev.langchain4j.internal.CompletableFutureUtils.failOnThrow;
import static dev.langchain4j.internal.DefaultExecutorProvider.getDefaultExecutor;
import static dev.langchain4j.internal.Utils.isNullOrEmpty;
import static dev.langchain4j.internal.Utils.randomUUID;
import static dev.langchain4j.internal.ValidationUtils.ensureConsistentSizes;
import static dev.langchain4j.internal.ValidationUtils.ensureNotBlank;
import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;
import static dev.langchain4j.store.embedding.infinispan.InfinispanStoreConfiguration.DEFAULT_CACHE_CONFIG;
import static java.util.Collections.singletonList;
import static java.util.stream.Collectors.toList;

import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingSearchResult;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.filter.Filter;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;
import org.infinispan.client.hotrod.RemoteCache;
import org.infinispan.client.hotrod.RemoteCacheManager;
import org.infinispan.client.hotrod.configuration.ConfigurationBuilder;
import org.infinispan.client.hotrod.Search;
import org.infinispan.query.dsl.Query;
import org.infinispan.commons.configuration.XMLStringConfiguration;
import org.infinispan.commons.marshall.ProtoStreamMarshaller;
import org.infinispan.protostream.FileDescriptorSource;
import org.infinispan.protostream.SerializationContext;
import org.infinispan.protostream.ProtobufUtil;
import org.infinispan.protostream.WrappedMessage;
import org.infinispan.protostream.schema.Schema;
import org.infinispan.protostream.schema.Syntax;

/**
 * Infinispan Embedding Store
 */
public class InfinispanEmbeddingStore implements EmbeddingStore<TextSegment> {

    private final RemoteCache<String, LangChainInfinispanItem> remoteCache;
    private final InfinispanStoreConfiguration storeConfiguration;

    /**
     * Creates an Infinispan embedding store from a RemoteCacheManager
     * Assumes marshalling configuration is already provided by the RemoteCacheManager instance.
     *
     * @param remoteCacheManager the already configured remote cache manager
     * @param storeConfiguration the store configuration
     */
    public InfinispanEmbeddingStore(
            RemoteCacheManager remoteCacheManager, InfinispanStoreConfiguration storeConfiguration) {

        ensureNotNull(remoteCacheManager, "remoteCacheManager");
        ensureNotNull(storeConfiguration, "storeConfiguration");
        ensureNotNull(storeConfiguration.dimension(), "dimension");
        ensureNotBlank(storeConfiguration.cacheName(), "cacheName");

        this.storeConfiguration = storeConfiguration;

        if (storeConfiguration.createCache()) {
            this.remoteCache = remoteCacheManager
                    .administration()
                    .getOrCreateCache(
                            storeConfiguration.cacheName(),
                            new XMLStringConfiguration(computeCacheConfiguration(storeConfiguration)));
        } else {
            this.remoteCache = remoteCacheManager.getCache(storeConfiguration.cacheName());
        }
    }

    /**
     * Creates an instance of InfinispanEmbeddingStore
     */
    public InfinispanEmbeddingStore(ConfigurationBuilder builder, InfinispanStoreConfiguration storeConfiguration) {
        ensureNotNull(builder, "builder");
        ensureNotNull(storeConfiguration, "storeConfiguration");
        ensureNotBlank(storeConfiguration.cacheName(), "cacheName");
        ensureNotNull(storeConfiguration.dimension(), "dimension");
        this.storeConfiguration = storeConfiguration;
        Schema schema = LangchainSchemaCreator.buildSchema(storeConfiguration);

        if (storeConfiguration.createCache()) {
            String remoteCacheConfig = computeCacheConfiguration(storeConfiguration);
            builder.remoteCache(storeConfiguration.cacheName()).configuration(remoteCacheConfig);
        }

        // Registers the schema on the client
        ProtoStreamMarshaller marshaller = new ProtoStreamMarshaller();
        SerializationContext serializationContext = marshaller.getSerializationContext();
        // ProtoStream 4's local parser requires explicit labels. The server schema stays proto3.
        String localSchema = LangchainSchemaCreator.buildSchema(storeConfiguration, Syntax.PROTO2).getContent();
        FileDescriptorSource fileDescriptorSource =
                FileDescriptorSource.fromString(storeConfiguration.fileName(), localSchema);
        serializationContext.registerProtoFiles(fileDescriptorSource);
        serializationContext.registerMarshaller(
                new LangChainItemMarshaller(storeConfiguration.langchainItemFullType()));
        serializationContext.registerMarshaller(new LangChainMetadataMarshaller(storeConfiguration.metadataFullType()));
        builder.marshaller(marshaller);

        // creates the client
        RemoteCacheManager rmc = new RemoteCacheManager(builder.build());

        try {
            if (storeConfiguration.registerSchema()) {
                RemoteCache<String, String> schemaCache = rmc.getCache("___protobuf_metadata");
                if (schemaCache == null) {
                    throw new IllegalStateException("Infinispan server did not expose its protobuf schema cache");
                }
                schemaCache.put(storeConfiguration.fileName(), schema.getContent());
                String schemaError = schemaCache.get(storeConfiguration.fileName() + ".errors");
                if (schemaError != null) {
                    throw new IllegalArgumentException(schemaError);
                }
            }

            this.remoteCache = rmc.getCache(storeConfiguration.cacheName());
            if (remoteCache == null) {
                throw new IllegalStateException("Infinispan cache '" + storeConfiguration.cacheName() + "' does not exist");
            }
        } catch (RuntimeException e) {
            rmc.stop();
            throw e;
        }
    }

    /**
     * Gets the underlying Infinispan remote cache
     *
     * @return RemoteCache
     */
    public RemoteCache<String, LangChainInfinispanItem> getRemoteCache() {
        return remoteCache;
    }

    private String computeCacheConfiguration(InfinispanStoreConfiguration storeConfiguration) {
        String remoteCacheConfig = storeConfiguration.cacheConfig();
        if (remoteCacheConfig == null) {
            remoteCacheConfig = DEFAULT_CACHE_CONFIG
                    .replace("CACHE_NAME", storeConfiguration.cacheName())
                    .replace("LANGCHAINITEM", storeConfiguration.langchainItemFullType())
                    .replace("LANGCHAIN_METADATA", storeConfiguration.metadataFullType());
        }
        return remoteCacheConfig;
    }

    @Override
    public String add(Embedding embedding) {
        String id = randomUUID();
        add(id, embedding);
        return id;
    }

    @Override
    public void add(String id, Embedding embedding) {
        addInternal(id, embedding, null);
    }

    @Override
    public String add(Embedding embedding, TextSegment textSegment) {
        String id = randomUUID();
        addInternal(id, embedding, textSegment);
        return id;
    }

    @Override
    public List<String> addAll(List<Embedding> embeddings) {
        List<String> ids = embeddings.stream().map(ignored -> randomUUID()).collect(toList());
        addAll(ids, embeddings, null);
        return ids;
    }

    @Override
    public void removeAll() {
        remoteCache.clear();
    }

    @Override
    public void removeAll(Filter filter) {
        if (filter == null) {
            throw new IllegalArgumentException("filter cannot be null");
        }

        InfinispanMetadataFilterMapper.FilterResult filterResult = new InfinispanMetadataFilterMapper().map(filter);

        String deleteQuery = "DELETE FROM " + storeConfiguration.langchainItemFullType() + " i " + filterResult.join
                + " where " + filterResult.query;
        Query<LangChainInfinispanItem> query = Search.getQueryFactory(remoteCache).create(deleteQuery);
        query.executeStatement();
    }

    @Override
    public void removeAll(Collection<String> ids) {
        if (isNullOrEmpty(ids)) {
            return;
        }

        for (String id : ids) {
            remoteCache.remove(id);
        }
    }

    @Override
    public EmbeddingSearchResult<TextSegment> search(EmbeddingSearchRequest request) {
        Query<Object[]> query = buildVectorQuery(request);
        List<Object[]> hits = query.list();
        return toSearchResult(hits, request.minScore());
    }

    @Override
    public CompletableFuture<EmbeddingSearchResult<TextSegment>> searchAsync(EmbeddingSearchRequest request) {
        // Hot Rod 13 exposes vector queries through the synchronous query DSL.
        return failOnThrow(() -> CompletableFuture.supplyAsync(() -> search(request), getDefaultExecutor()));
    }

    private Query<Object[]> buildVectorQuery(EmbeddingSearchRequest request) {
        InfinispanMetadataFilterMapper.FilterResult filteringQuery =
                new InfinispanMetadataFilterMapper().map(request.filter());
        String joinPart = "";
        String filteringPart = "";

        if (filteringQuery != null) {
            joinPart = filteringQuery.join;
            filteringPart = " filtering(" + filteringQuery.query + ")";
        }

        String vectorQuery = "select i, score(i) from " + storeConfiguration.langchainItemFullType()
                + " i "
                + joinPart
                + " where i.embedding <-> "
                + Arrays.toString(request.queryEmbedding().vector()) + "~"
                + storeConfiguration.distance()
                + filteringPart;

        return Search.getQueryFactory(remoteCache).<Object[]>create(vectorQuery).maxResults(request.maxResults());
    }

    private EmbeddingSearchResult<TextSegment> toSearchResult(List<Object[]> hits, double minScore) {
        List<EmbeddingMatch<TextSegment>> matches = hits.stream()
                .map(obj -> {
                    LangChainInfinispanItem item = (LangChainInfinispanItem) unwrapProjection(obj[0]);
                    Number score = (Number) unwrapProjection(obj[1]);
                    if (score.doubleValue() < minScore) {
                        return null;
                    }

                    TextSegment embedded = null;
                    if (item.text() != null) {
                        Map<String, Object> map = new HashMap<>();
                        for (LangChainMetadata metadata : item.metadata()) {
                            map.put(metadata.name(), metadata.value());
                        }
                        embedded = new TextSegment(item.text(), new Metadata(map));
                    }
                    Embedding embedding = new Embedding(item.embedding());
                    return new EmbeddingMatch<>(score.doubleValue(), item.id(), embedding, embedded);
                })
                .filter(Objects::nonNull)
                .collect(toList());

        return new EmbeddingSearchResult<>(matches);
    }

    private Object unwrapProjection(Object projection) {
        // Hot Rod 13 leaves an additional ProtoStream envelope around projected entities from recent servers.
        if (!(projection instanceof WrappedMessage)) {
            return projection;
        }
        Object value = ((WrappedMessage) projection).getValue();
        if (!(value instanceof byte[])) {
            return value;
        }
        ProtoStreamMarshaller marshaller = (ProtoStreamMarshaller) remoteCache.getRemoteCacheManager().getMarshaller();
        try {
            return ProtobufUtil.fromWrappedByteArray(marshaller.getSerializationContext(), (byte[]) value);
        } catch (java.io.IOException e) {
            throw new java.io.UncheckedIOException("Unable to decode Infinispan vector query projection", e);
        }
    }

    private void addInternal(String id, Embedding embedding, TextSegment embedded) {
        addAll(singletonList(id), singletonList(embedding), embedded == null ? null : singletonList(embedded));
    }

    @Override
    public void addAll(List<String> ids, List<Embedding> embeddings, List<TextSegment> embedded) {
        ensureConsistentSizes(ids, embeddings, embedded);
        if (isNullOrEmpty(embeddings)) {
            return;
        }

        int size = ids.size();
        Map<String, LangChainInfinispanItem> elements = new HashMap<>(size);
        for (int i = 0; i < size; i++) {
            String id = ids.get(i);
            Embedding embedding = embeddings.get(i);
            TextSegment textSegment = embedded == null ? null : embedded.get(i);
            if (textSegment != null) {
                Set<LangChainMetadata> metadata = textSegment.metadata().toMap().entrySet().stream()
                        .map(e -> new LangChainMetadata(e.getKey(), e.getValue()))
                        .collect(Collectors.toSet());
                elements.put(
                        id,
                        new LangChainInfinispanItem(
                                id,
                                embedding.vector(),
                                textSegment.text(),
                                metadata,
                                textSegment.metadata().toMap()));
            } else {
                elements.put(id, new LangChainInfinispanItem(id, embedding.vector(), null, null, null));
            }
        }
        // blocking call
        remoteCache.putAll(elements);
    }

    public static Builder builder() {
        return new Builder();
    }

    public RemoteCache<String, LangChainInfinispanItem> remoteCache() {
        return remoteCache;
    }

    public void clearCache() {
        remoteCache.clear();
    }

    public static class Builder {
        private ConfigurationBuilder configurationBuilder;
        private String cacheName;
        private Integer dimension;
        private Integer distance;
        private String similarity;
        private String cacheConfig;
        private String packageName;
        private String fileName;
        private String langchainItemName;
        private String metadataItemName;
        private boolean registerSchema = true;
        private boolean createCache = true;

        /**
         * Infinispan cache name to be used, will be created on first access
         */
        public Builder cacheName(String name) {
            this.cacheName = name;
            return this;
        }

        /**
         * Infinispan cache config to be used, will be created on first access
         */
        public Builder cacheConfig(String cacheConfig) {
            this.cacheConfig = cacheConfig;
            return this;
        }

        /**
         * Infinispan vector dimension
         */
        public Builder dimension(Integer dimension) {
            this.dimension = dimension;
            return this;
        }

        /**
         * Infinispan distance for knn query
         */
        public Builder distance(Integer distance) {
            this.distance = distance;
            return this;
        }

        /**
         * Infinispan similarity for the embedding definition
         */
        public Builder similarity(String similarity) {
            this.similarity = similarity;
            return this;
        }

        /**
         * Infinispan schema package name
         */
        public Builder packageName(String packageName) {
            this.packageName = packageName;
            return this;
        }

        /**
         * Infinispan schema file name
         */
        public Builder fileName(String fileName) {
            this.fileName = fileName;
            return this;
        }

        /**
         * Infinispan schema langchainItemName
         */
        public Builder langchainItemName(String langchainItemName) {
            this.langchainItemName = langchainItemName;
            return this;
        }

        /**
         * Infinispan schema metadataItemName
         */
        public Builder metadataItemName(String metadataItemName) {
            this.metadataItemName = metadataItemName;
            return this;
        }

        /**
         * Register Langchain schema in the server
         */
        public Builder registerSchema(boolean registerSchema) {
            this.registerSchema = registerSchema;
            return this;
        }

        /**
         * Create cache in the server
         */
        public Builder createCache(boolean createCache) {
            this.createCache = createCache;
            return this;
        }

        /**
         * Infinispan Configuration Builder
         */
        public Builder infinispanConfigBuilder(ConfigurationBuilder builder) {
            this.configurationBuilder = builder;
            return this;
        }

        /**
         * Builds the store
         *
         * @return InfinispanEmbeddingStore
         */
        public InfinispanEmbeddingStore build() {
            return new InfinispanEmbeddingStore(
                    configurationBuilder,
                    new InfinispanStoreConfiguration(
                            cacheName,
                            dimension,
                            distance,
                            similarity,
                            cacheConfig,
                            packageName,
                            fileName,
                            langchainItemName,
                            metadataItemName,
                            createCache,
                            registerSchema));
        }
    }
}
