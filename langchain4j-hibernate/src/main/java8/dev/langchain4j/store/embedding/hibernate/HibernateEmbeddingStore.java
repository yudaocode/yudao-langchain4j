package dev.langchain4j.store.embedding.hibernate;

import static dev.langchain4j.internal.ValidationUtils.ensureNotBlank;
import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

import com.pgvector.PGvector;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.internal.Json;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingSearchResult;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.filter.Filter;
import dev.langchain4j.store.embedding.hibernate.pgvector.HibernatePgVectorSearch;
import dev.langchain4j.store.embedding.hibernate.pgvector.HibernatePgVectorSearch.Match;
import dev.langchain4j.store.embedding.hibernate.pgvector.HibernatePgVectorSearch.Metric;
import java.io.Serializable;
import java.lang.annotation.Annotation;
import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Member;
import java.sql.PreparedStatement;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.stream.Collectors;
import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.CriteriaDelete;
import javax.persistence.criteria.Predicate;
import javax.persistence.criteria.Root;
import javax.persistence.metamodel.EntityType;
import javax.persistence.metamodel.SingularAttribute;
import javax.sql.DataSource;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.Transaction;
import org.hibernate.engine.spi.SessionFactoryImplementor;
import org.hibernate.engine.spi.SharedSessionContractImplementor;
import org.hibernate.id.Assigned;
import org.hibernate.id.IdentifierGeneratorHelper;
import org.hibernate.persister.entity.EntityPersister;
import org.postgresql.ds.PGSimpleDataSource;

/**
 * PostgreSQL implementation of the Hibernate embedding store on Hibernate 5.
 *
 * @param <E> the mapped entity type
 * @author 芋道源码
 */
public class HibernateEmbeddingStore<E> implements EmbeddingStore<TextSegment>, AutoCloseable {

    private final SessionFactory factory;
    private final Class<E> entityClass;
    private final EntityPersister persister;
    private final String vectorProperty;
    private final String textProperty;
    private final String metadataProperty;
    private final Map<String, String> mappedMetadata;
    private final HibernatePgVectorSearch<E> nearest;
    private final PostgresMetadataFilter filters;
    private final Metric metric;
    private final Map<String, Object> hints;
    private final String ownedTable;

    private HibernateEmbeddingStore(Builder<E> builder, String ownedTable) {
        this.factory = ensureNotNull(builder.factory, "sessionFactory");
        this.entityClass = builder.entityClass;
        this.ownedTable = ownedTable;
        SessionFactoryImplementor implementation = factory.unwrap(SessionFactoryImplementor.class);
        if (builder.databaseKind != null && builder.databaseKind != DatabaseKind.POSTGRESQL
                || DatabaseKind.determineDatabaseKind(implementation.getJdbcServices().getDialect())
                        != DatabaseKind.POSTGRESQL) {
            throw new IllegalArgumentException("The Hibernate 5 embedding store requires PostgreSQL");
        }

        this.persister = implementation.getMetamodel().entityPersister(entityClass);
        if (persister.getIdentifierPropertyName() == null || persister.getIdentifierType().isComponentType()) {
            throw new IllegalArgumentException("A single scalar Hibernate entity ID is required");
        }
        EntityType<E> entity = factory.getMetamodel().entity(entityClass);
        String annotatedVector = builder.vector == null ? annotated(entity, EmbeddingVector.class) : null;
        String legacyVector = builder.vector == null
                ? annotated(entity, dev.langchain4j.store.embedding.hibernate.Embedding.class) : null;
        if (annotatedVector != null && legacyVector != null) {
            throw new IllegalArgumentException("Multiple embedding attributes on " + entityClass.getName());
        }
        this.vectorProperty = requiredProperty(entity, builder.vector != null ? builder.vector
                : annotatedVector != null ? annotatedVector : legacyVector, float[].class, "embedding vector");
        String text = builder.text != null ? builder.text : annotated(entity, EmbeddedText.class);
        this.textProperty = text == null ? null : requiredProperty(entity, text, String.class, "embedded text");
        String metadata = builder.metadata != null ? builder.metadata : annotated(entity, UnmappedMetadata.class);
        if (metadata == null) {
            throw new IllegalArgumentException("An unmapped metadata attribute is required");
        }
        Class<?> metadataType = entity.getSingularAttribute(metadata).getJavaType();
        if (metadataType != String.class && !Map.class.isAssignableFrom(metadataType)) {
            throw new IllegalArgumentException("Unmapped metadata must be a JSON string or map");
        }
        this.metadataProperty = metadata;

        Map<String, String> mapped = new LinkedHashMap<>();
        if (builder.metadataAttributes != null) {
            for (String name : builder.metadataAttributes) {
                entity.getSingularAttribute(name);
                mapped.put(name, name);
            }
        } else {
            for (SingularAttribute<?, ?> attribute : entity.getSingularAttributes()) {
                if (hasAnnotation(attribute, MetadataAttribute.class)) {
                    if (attribute.getPersistentAttributeType() != javax.persistence.metamodel.Attribute.PersistentAttributeType.BASIC) {
                        throw new IllegalArgumentException("Metadata attribute must be a scalar: " + attribute.getName());
                    }
                    mapped.put(attribute.getName(), attribute.getName());
                }
            }
        }
        this.mappedMetadata = Collections.unmodifiableMap(mapped);
        this.filters = new PostgresMetadataFilter(persister, mappedMetadata, metadataProperty);
        DistanceFunction distance = builder.distance;
        if (distance == null && annotatedVector != null) {
            AnnotatedElement field = (AnnotatedElement) entity.getSingularAttribute(annotatedVector).getJavaMember();
            distance = field.getAnnotation(EmbeddingVector.class).distance();
        }
        this.metric = toMetric(distance == null ? DistanceFunction.COSINE : distance);
        this.nearest = new HibernatePgVectorSearch<>(entityClass, vectorProperty);
        this.hints = builder.hints == null ? Collections.emptyMap()
                : Collections.unmodifiableMap(new LinkedHashMap<>(builder.hints));
    }

    public static <E> Builder<E> builder(Class<E> entityClass) {
        return new Builder<>(entityClass);
    }

    public static DynamicBuilder dynamicBuilder() {
        return new DynamicBuilder();
    }

    public static DynamicDatasourceBuilder dynamicDatasourceBuilder() {
        return new DynamicDatasourceBuilder();
    }

    @Override
    public void close() {
        if (ownedTable != null) {
            factory.close();
        }
    }

    @Override
    public String add(Embedding embedding) {
        return add(embedding, (TextSegment) null);
    }

    @Override
    public void add(String id, Embedding embedding) {
        addAll(Collections.singletonList(id), Collections.singletonList(embedding), null);
    }

    @Override
    public String add(Embedding embedding, TextSegment text) {
        return addAll(Collections.singletonList(embedding), Collections.singletonList(text)).get(0);
    }

    @Override
    public List<String> addAll(List<Embedding> embeddings) {
        return addAll(embeddings, null);
    }

    @Override
    public List<String> addAll(List<Embedding> embeddings, List<TextSegment> segments) {
        checkSizes(null, embeddings, segments);
        if (embeddings.isEmpty()) {
            return Collections.emptyList();
        }
        return transaction(session -> {
            List<String> ids = new ArrayList<>(embeddings.size());
            for (int i = 0; i < embeddings.size(); i++) {
                String id = isUuidId() ? UUID.randomUUID().toString() : null;
                ids.add(write(session, id, embeddings.get(i), segment(segments, i)));
            }
            return ids;
        });
    }

    @Override
    public void addAll(List<String> ids, List<Embedding> embeddings, List<TextSegment> segments) {
        checkSizes(ensureNotNull(ids, "ids"), embeddings, segments);
        if (!ids.isEmpty()) {
            transaction(session -> {
                for (int i = 0; i < ids.size(); i++) {
                    write(session, ensureNotBlank(ids.get(i), "id"), embeddings.get(i), segment(segments, i));
                }
                return null;
            });
        }
    }

    private String write(Session session, String id, Embedding value, TextSegment segment) {
        float[] vector = ensureNotNull(value, "embedding").vector();
        String text = segment == null ? null : segment.text();
        Map<String, Object> metadata = segment == null
                ? new LinkedHashMap<>() : new LinkedHashMap<>(segment.metadata().toMap());
        if (ownedTable != null) {
            // Dynamic tables have assigned UUIDs, so PostgreSQL can upsert without a lookup or a write race.
            session.doWork(connection -> {
                try (PreparedStatement sql = connection.prepareStatement("insert into " + ownedTable
                        + " (id, embedding, text, metadata) values (?, ?, ?, ?) on conflict (id) do update"
                        + " set embedding=excluded.embedding, text=excluded.text, metadata=excluded.metadata")) {
                    sql.setObject(1, UUID.fromString(id));
                    sql.setObject(2, new PGvector(vector));
                    sql.setString(3, text);
                    sql.setObject(4, JsonbMetadataType.postgresValue(Json.toJson(metadata)));
                    sql.executeUpdate();
                }
            });
            return id;
        }
        Serializable typedId = id == null ? null : toId(id);
        E existing = typedId == null ? null : session.get(entityClass, typedId);
        E entity = existing == null ? entityClass.cast(persister.instantiate(typedId, context(session))) : existing;
        set(entity, vectorProperty, vector.clone());
        if (textProperty != null) {
            set(entity, textProperty, text);
        }
        mappedMetadata.forEach((key, property) -> {
            Object metadataValue = metadata.remove(key);
            set(entity, property, PostgresMetadataFilter.coerce(persister.getPropertyType(property), metadataValue));
        });
        set(entity, metadataProperty, persister.getPropertyType(metadataProperty).getReturnedClass() == String.class
                ? Json.toJson(metadata) : metadata);
        if (existing != null) {
            return id;
        }
        if (typedId == null) {
            return session.save(entity).toString();
        }
        // Explicit IDs use the persister insert path to honor the supplied identifier even with a generator.
        session.flush();
        persister.insert(typedId, persister.getPropertyValues(entity), entity, context(session));
        return id;
    }

    @Override
    public void removeAll(Collection<String> ids) {
        ensureNotNull(ids, "ids");
        if (!ids.isEmpty()) {
            List<Serializable> converted = ids.stream().map(this::toId).collect(Collectors.toList());
            transaction(session -> {
                for (int i = 0; i < converted.size(); i += 500) {
                    List<Serializable> batch = converted.subList(i, Math.min(i + 500, converted.size()));
                    delete(session, (root, criteria) -> root.get(persister.getIdentifierPropertyName()).in(batch));
                }
                return null;
            });
        }
    }

    @Override
    public void removeAll(Filter filter) {
        ensureNotNull(filter, "filter");
        transaction(session -> {
            delete(session, (root, criteria) -> filters.predicate(root, criteria, filter));
            return null;
        });
    }

    @Override
    public void removeAll() {
        transaction(session -> {
            delete(session, null);
            return null;
        });
    }

    private void delete(Session session, BiFunction<Root<E>, CriteriaBuilder, Predicate> predicate) {
        CriteriaBuilder cb = session.getCriteriaBuilder();
        CriteriaDelete<E> delete = cb.createCriteriaDelete(entityClass);
        Root<E> root = delete.from(entityClass);
        if (predicate != null) {
            delete.where(predicate.apply(root, cb));
        }
        session.createQuery(delete).executeUpdate();
    }

    @Override
    public EmbeddingSearchResult<TextSegment> search(EmbeddingSearchRequest request) {
        Filter filter = request.filter();
        return search(request.queryEmbedding(), request.minScore(),
                filter == null ? null : (root, criteria) -> filters.predicate(root, criteria, filter),
                request.maxResults());
    }

    public List<E> query(Embedding embedding, BiFunction<Root<E>, CriteriaBuilder, Predicate> restriction) {
        return query(embedding, 0, restriction, Integer.MAX_VALUE);
    }

    public List<E> query(Embedding embedding, double minScore, BiFunction<Root<E>, CriteriaBuilder, Predicate> restriction) {
        return query(embedding, minScore, restriction, Integer.MAX_VALUE);
    }

    public List<E> query(Embedding embedding, double minScore, BiFunction<Root<E>, CriteriaBuilder, Predicate> restriction,
            int maxResults) {
        try (Session session = factory.openSession()) {
            return nearest.search(session, embedding, metric, minScore, maxResults, restriction, hints)
                    .stream().map(Match::entity).collect(Collectors.toList());
        }
    }

    public EmbeddingSearchResult<TextSegment> search(
            Embedding embedding, BiFunction<Root<E>, CriteriaBuilder, Predicate> restriction) {
        return search(embedding, 0, restriction, Integer.MAX_VALUE);
    }

    public EmbeddingSearchResult<TextSegment> search(
            Embedding embedding, double minScore, BiFunction<Root<E>, CriteriaBuilder, Predicate> restriction) {
        return search(embedding, minScore, restriction, Integer.MAX_VALUE);
    }

    public EmbeddingSearchResult<TextSegment> search(Embedding embedding, double minScore,
            BiFunction<Root<E>, CriteriaBuilder, Predicate> restriction, int maxResults) {
        try (Session session = factory.openSession()) {
            List<EmbeddingMatch<TextSegment>> results = new ArrayList<>();
            for (Match<E> match : nearest.search(session, embedding, metric, minScore, maxResults, restriction, hints)) {
                E entity = match.entity();
                results.add(new EmbeddingMatch<>(match.score(), persister.getIdentifier(entity, context(session)).toString(),
                        Embedding.from((float[]) persister.getPropertyValue(entity, vectorProperty)), segment(entity)));
            }
            return new EmbeddingSearchResult<>(results);
        }
    }

    public void addAllEntities(List<?> entities) {
        if (entities != null && !entities.isEmpty()) {
            transaction(session -> {
                for (Object entity : entities) {
                    session.persist(entityClass.cast(entity));
                }
                return null;
            });
        }
    }

    public void applyEmbeddings(List<? extends E> entities, EmbeddingModel model) {
        List<Embedding> embeddings = model.embedAll(createTextSegments(entities)).content();
        if (embeddings.size() != entities.size()) {
            throw new IllegalArgumentException("Embedding model returned the wrong number of vectors");
        }
        for (int i = 0; i < entities.size(); i++) {
            set(entities.get(i), vectorProperty, embeddings.get(i).vector());
        }
    }

    public List<TextSegment> createTextSegments(List<? extends E> entities) {
        if (textProperty == null) {
            throw new IllegalStateException("The embedding entity has no text property");
        }
        return entities.stream().map(this::segment).collect(Collectors.toList());
    }

    public List<String> generateIds(int count) {
        if (count < 0) {
            throw new IllegalArgumentException("count must not be negative");
        }
        return transaction(session -> {
            List<String> result = new ArrayList<>(count);
            for (int i = 0; i < count; i++) {
                if (isUuidId()) {
                    result.add(UUID.randomUUID().toString());
                } else {
                    if (persister.getIdentifierGenerator() instanceof Assigned) {
                        throw new IllegalStateException("The entity requires an explicitly assigned ID");
                    }
                    Object value = persister.getIdentifierGenerator().generate(context(session),
                            persister.instantiate(null, context(session)));
                    if (value == null || value == IdentifierGeneratorHelper.POST_INSERT_INDICATOR) {
                        throw new IllegalStateException("The ID is generated only when an entity is persisted");
                    }
                    result.add(value.toString());
                }
            }
            return result;
        });
    }

    private <T> T transaction(Function<Session, T> work) {
        try (Session session = factory.openSession()) {
            Transaction tx = session.beginTransaction();
            try {
                T result = work.apply(session);
                tx.commit();
                return result;
            } catch (RuntimeException | Error e) {
                if (tx.isActive()) {
                    try {
                        tx.rollback();
                    } catch (RuntimeException rollback) {
                        e.addSuppressed(rollback);
                    }
                }
                throw e;
            }
        }
    }

    private SharedSessionContractImplementor context(Session session) {
        return session.unwrap(SharedSessionContractImplementor.class);
    }

    private Serializable toId(String id) {
        return (Serializable) PostgresMetadataFilter.coerce(persister.getIdentifierType(), ensureNotBlank(id, "id"));
    }

    private boolean isUuidId() {
        Class<?> type = persister.getIdentifierType().getReturnedClass();
        return type == String.class || type == UUID.class;
    }

    private void set(E entity, String property, Object value) {
        persister.setPropertyValue(entity, persister.getEntityMetamodel().getPropertyIndex(property), value);
    }

    private TextSegment segment(E entity) {
        String text = textProperty == null ? null : (String) persister.getPropertyValue(entity, textProperty);
        if (text == null || text.trim().isEmpty()) {
            return null;
        }
        Object unmapped = persister.getPropertyValue(entity, metadataProperty);
        Map<String, Object> metadata = new LinkedHashMap<>();
        Map<?, ?> values = unmapped instanceof String ? Json.fromJson((String) unmapped, Map.class) : (Map<?, ?>) unmapped;
        if (values != null) {
            values.forEach((key, value) -> metadata.put((String) key, value));
        }
        mappedMetadata.forEach((key, name) -> {
            Object value = persister.getPropertyValue(entity, name);
            if (value != null) {
                if (value instanceof Byte || value instanceof Short) {
                    value = ((Number) value).intValue();
                } else if (!(value instanceof Number) && !(value instanceof String) && !(value instanceof UUID)) {
                    value = value.toString();
                }
                metadata.put(key, value);
            }
        });
        return TextSegment.from(text, Metadata.from(metadata));
    }

    private static TextSegment segment(List<TextSegment> segments, int index) {
        return segments == null ? null : segments.get(index);
    }

    private static void checkSizes(List<String> ids, List<Embedding> embeddings, List<TextSegment> segments) {
        int size = ensureNotNull(embeddings, "embeddings").size();
        if (ids != null && ids.size() != size || segments != null && segments.size() != size) {
            throw new IllegalArgumentException("IDs, embeddings and text segments must have the same length");
        }
    }

    static Metric toMetric(DistanceFunction distance) {
        switch (distance) {
            case COSINE: return Metric.COSINE;
            case EUCLIDEAN: return Metric.EUCLIDEAN;
            case EUCLIDEAN_SQUARED: return Metric.EUCLIDEAN_SQUARED;
            case MANHATTAN: return Metric.MANHATTAN;
            case INNER_PRODUCT:
            case NEGATIVE_INNER_PRODUCT: return Metric.INNER_PRODUCT;
            default: throw new IllegalArgumentException("Unsupported dense PostgreSQL distance: " + distance);
        }
    }

    private static String requiredProperty(EntityType<?> entity, String name, Class<?> type, String role) {
        if (name == null || !type.equals(entity.getSingularAttribute(name).getJavaType())) {
            throw new IllegalArgumentException("A " + type.getSimpleName() + " " + role + " attribute is required");
        }
        return name;
    }

    private static String annotated(EntityType<?> entity, Class<? extends Annotation> annotation) {
        String result = null;
        for (SingularAttribute<?, ?> attribute : entity.getSingularAttributes()) {
            if (hasAnnotation(attribute, annotation)) {
                if (result != null) {
                    throw new IllegalArgumentException("Multiple attributes annotated with " + annotation.getSimpleName());
                }
                result = attribute.getName();
            }
        }
        return result;
    }

    private static boolean hasAnnotation(SingularAttribute<?, ?> attribute, Class<? extends Annotation> annotation) {
        Member member = attribute.getJavaMember();
        return member instanceof AnnotatedElement && ((AnnotatedElement) member).isAnnotationPresent(annotation);
    }

    /**
     * Configures an existing Hibernate 5 entity mapping.
     *
     * @author 芋道源码
     */
    public static class Builder<E> {
        private final Class<E> entityClass;
        private SessionFactory factory;
        private DatabaseKind databaseKind;
        private String vector;
        private String text;
        private String metadata;
        private String[] metadataAttributes;
        private DistanceFunction distance;
        private Map<String, Object> hints;

        Builder(Class<E> entityClass) {
            this.entityClass = ensureNotNull(entityClass, "entityClass");
        }

        public Builder<E> sessionFactory(SessionFactory factory) {
            this.factory = factory;
            return this;
        }

        public Builder<E> databaseKind(DatabaseKind databaseKind) {
            this.databaseKind = databaseKind;
            return this;
        }

        public Builder<E> embeddingAttributeName(String name) {
            this.vector = name;
            return this;
        }

        public Builder<E> embeddedTextAttributeName(String name) {
            this.text = name;
            return this;
        }

        public Builder<E> unmappedMetadataAttributeName(String name) {
            this.metadata = name;
            return this;
        }

        public Builder<E> metadataAttributeNames(String... names) {
            this.metadataAttributes = names == null ? null : names.clone();
            return this;
        }

        public Builder<E> distanceFunction(DistanceFunction distance) {
            this.distance = distance;
            return this;
        }

        public Builder<E> queryHints(Map<String, Object> hints) {
            this.hints = hints;
            return this;
        }

        public HibernateEmbeddingStore<E> build() {
            return new HibernateEmbeddingStore<>(this, null);
        }
    }

    /**
     * Shared configuration for dynamically mapped PostgreSQL tables.
     *
     * @author 芋道源码
     */
    public abstract static class BaseBuilder<B extends BaseBuilder<B>> {
        protected DatabaseKind databaseKind;
        protected String table;
        protected Integer dimension;
        protected Boolean createIndex;
        protected String indexType;
        protected String indexOptions;
        protected Boolean createTable;
        protected Boolean dropTableFirst;
        protected DistanceFunction distanceFunction = DistanceFunction.COSINE;
        protected Map<String, Object> queryHints;

        protected abstract B self();

        public B databaseKind(DatabaseKind value) {
            databaseKind = value;
            return self();
        }

        public B table(String value) {
            table = value;
            return self();
        }

        public B dimension(Integer value) {
            dimension = value;
            return self();
        }

        public B createIndex(Boolean value) {
            createIndex = value;
            return self();
        }

        public B indexType(String value) {
            indexType = value;
            return self();
        }

        public B indexOptions(String value) {
            indexOptions = value;
            return self();
        }

        public B createTable(Boolean value) {
            createTable = value;
            return self();
        }

        public B dropTableFirst(Boolean value) {
            dropTableFirst = value;
            return self();
        }

        public B distanceFunction(DistanceFunction value) {
            distanceFunction = value;
            return self();
        }

        public B queryHints(Map<String, Object> value) {
            queryHints = value;
            return self();
        }

        protected HibernateEmbeddingStore<EmbeddingEntity> build(DataSource dataSource) {
            if (databaseKind != null && databaseKind != DatabaseKind.POSTGRESQL) {
                throw new IllegalArgumentException("The Hibernate 5 embedding store requires PostgreSQL");
            }
            toMetric(ensureNotNull(distanceFunction, "distanceFunction"));
            SessionFactory factory = PostgresStoreSchema.build(ensureNotNull(dataSource, "dataSource"),
                    ensureNotBlank(table, "table"), ensureNotNull(dimension, "dimension"), Boolean.TRUE.equals(createTable),
                    Boolean.TRUE.equals(dropTableFirst), Boolean.TRUE.equals(createIndex), distanceFunction, indexType, indexOptions);
            try {
                Builder<EmbeddingEntity> builder = builder(EmbeddingEntity.class).sessionFactory(factory)
                        .embeddingAttributeName("embedding").embeddedTextAttributeName("text")
                        .unmappedMetadataAttributeName("metadata").distanceFunction(distanceFunction).queryHints(queryHints);
                return new HibernateEmbeddingStore<>(builder, table);
            } catch (RuntimeException e) {
                factory.close();
                throw e;
            }
        }
    }

    /**
     * JDBC URL or host based configuration for a dynamically mapped store.
     *
     * @author 芋道源码
     */
    public static class DynamicBuilder extends BaseBuilder<DynamicBuilder> {
        private String host;
        private int port = 5432;
        private String database;
        private String jdbcUrl;
        private String user;
        private String password;

        @Override
        protected DynamicBuilder self() {
            return this;
        }

        public DynamicBuilder host(String value) {
            host = value;
            return this;
        }

        public DynamicBuilder port(int value) {
            port = value;
            return this;
        }

        public DynamicBuilder database(String value) {
            database = value;
            return this;
        }

        public DynamicBuilder jdbcUrl(String value) {
            jdbcUrl = value;
            return this;
        }

        public DynamicBuilder user(String value) {
            user = value;
            return this;
        }

        public DynamicBuilder password(String value) {
            password = value;
            return this;
        }

        public HibernateEmbeddingStore<EmbeddingEntity> build() {
            String url = jdbcUrl == null ? DatabaseKind.POSTGRESQL.createJdbcUrl(ensureNotBlank(host, "host"), port,
                    ensureNotBlank(database, "database")) : jdbcUrl;
            if (!DatabaseKind.POSTGRESQL.isJdbcUrl(url)) {
                throw new IllegalArgumentException("A PostgreSQL JDBC URL is required");
            }
            PGSimpleDataSource source = new PGSimpleDataSource();
            source.setUrl(url);
            if (user != null) {
                source.setUser(user);
            }
            if (password != null) {
                source.setPassword(password);
            }
            return build(source);
        }
    }

    /**
     * DataSource based configuration for a dynamically mapped store.
     *
     * @author 芋道源码
     */
    public static class DynamicDatasourceBuilder extends BaseBuilder<DynamicDatasourceBuilder> {
        private DataSource source;

        @Override
        protected DynamicDatasourceBuilder self() {
            return this;
        }

        public DynamicDatasourceBuilder dataSource(DataSource dataSource) {
            source = dataSource;
            return this;
        }

        public HibernateEmbeddingStore<EmbeddingEntity> build() {
            return build(source);
        }
    }
}
