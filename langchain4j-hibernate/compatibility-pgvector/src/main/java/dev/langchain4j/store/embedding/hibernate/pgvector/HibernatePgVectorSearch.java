package dev.langchain4j.store.embedding.hibernate.pgvector;

import com.pgvector.PGvector;
import dev.langchain4j.data.embedding.Embedding;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.BiFunction;
import javax.persistence.EntityManager;
import javax.persistence.Tuple;
import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.CriteriaQuery;
import javax.persistence.criteria.Expression;
import javax.persistence.criteria.ParameterExpression;
import javax.persistence.criteria.Path;
import javax.persistence.criteria.Predicate;
import javax.persistence.criteria.Root;
import javax.persistence.metamodel.SingularAttribute;

/**
 * Searches a Hibernate 5 entity with a pgvector-mapped attribute in the caller's persistence context.
 *
 * @param <E> the entity type
 * @author 芋道源码
 */
public final class HibernatePgVectorSearch<E> {

    private final Class<E> entityClass;
    private final String vectorAttribute;

    /**
     * @param entityClass the mapped JPA entity class
     * @param vectorAttribute the name of its {@code float[]} attribute mapped with {@link PgVectorType}
     */
    public HibernatePgVectorSearch(Class<E> entityClass, String vectorAttribute) {
        this.entityClass = Objects.requireNonNull(entityClass, "entityClass");
        this.vectorAttribute = Objects.requireNonNull(vectorAttribute, "vectorAttribute");
    }

    /**
     * Finds nearest entities using a database-side restriction, minimum score and result limit.
     *
     * @param entityManager the caller's open entity manager
     * @param reference the query embedding
     * @param metric the pgvector distance metric
     * @param minScore the minimum inclusive score, from zero to one
     * @param limit the maximum number of matches
     * @param restriction optional JPA criteria restriction, or {@code null}
     * @return entities in order of increasing distance
     */
    public List<Match<E>> search(
            EntityManager entityManager,
            Embedding reference,
            Metric metric,
            double minScore,
            int limit,
            BiFunction<Root<E>, CriteriaBuilder, Predicate> restriction) {
        Objects.requireNonNull(entityManager, "entityManager");
        Objects.requireNonNull(metric, "metric");
        float[] vector = Objects.requireNonNull(reference, "reference").vector();
        if (!(minScore >= 0 && minScore <= 1)) {
            throw new IllegalArgumentException("minScore must be between 0 and 1");
        }
        if (limit <= 0) {
            throw new IllegalArgumentException("limit must be positive");
        }
        boolean nonzero = false;
        for (float value : vector) {
            if (!Float.isFinite(value)) {
                throw new IllegalArgumentException("query vector must contain finite values");
            }
            nonzero |= value != 0f;
        }
        if (metric == Metric.COSINE && !nonzero) {
            throw new IllegalArgumentException("cosine search requires a nonzero query vector");
        }

        CriteriaBuilder criteria = entityManager.getCriteriaBuilder();
        CriteriaQuery<Tuple> query = criteria.createTupleQuery();
        Root<E> root = query.from(entityClass);
        SingularAttribute<? super E, float[]> attribute =
                root.getModel().getSingularAttribute(vectorAttribute, float[].class);
        Path<float[]> column = root.get(attribute);
        ParameterExpression<String> vectorParameter = criteria.parameter(String.class);
        Expression<Double> distance = criteria.function(metric.function, Double.class, column, vectorParameter);

        List<Predicate> predicates = new ArrayList<>();
        predicates.add(criteria.isNotNull(column));
        if (minScore > 0 || metric == Metric.COSINE) {
            // Cosine distance is undefined for stored zero vectors. pgvector orders NaN above finite values.
            predicates.add(criteria.le(distance, metric.maximumDistance(minScore)));
        }
        if (restriction != null) {
            predicates.add(Objects.requireNonNull(restriction.apply(root, criteria), "restriction predicate"));
        }
        query.multiselect(root, distance)
                .where(predicates.toArray(new Predicate[0]))
                .orderBy(criteria.asc(distance));
        List<Tuple> rows = entityManager.createQuery(query)
                .setParameter(vectorParameter, new PGvector(vector).getValue())
                .setMaxResults(limit)
                .getResultList();
        List<Match<E>> matches = new ArrayList<>(rows.size());
        for (Tuple row : rows) {
            matches.add(new Match<>(row.get(0, entityClass), metric.score(row.get(1, Double.class))));
        }
        return matches;
    }

    /**
     * Distance metrics for dense float vectors.
     *
     * @author 芋道源码
     */
    public enum Metric {
        COSINE("lc_cosine"),
        EUCLIDEAN("lc_l2"),
        EUCLIDEAN_SQUARED("lc_l2"),
        MANHATTAN("lc_l1"),
        INNER_PRODUCT("lc_negative_inner_product");

        private final String function;

        Metric(String function) {
            this.function = function;
        }

        double score(double distance) {
            switch (this) {
                case COSINE:
                    return 1 - distance / 2;
                case INNER_PRODUCT:
                    return Math.max(0, Math.min(1, (1 - distance) / 2));
                case EUCLIDEAN_SQUARED:
                    return 1 / (1 + distance * distance);
                default:
                    return 1 / (1 + distance);
            }
        }

        double maximumDistance(double minimumScore) {
            switch (this) {
                case COSINE:
                    return 2 * (1 - minimumScore);
                case INNER_PRODUCT:
                    return 1 - 2 * minimumScore;
                case EUCLIDEAN_SQUARED:
                    return Math.sqrt(1 / minimumScore - 1);
                default:
                    return 1 / minimumScore - 1;
            }
        }
    }

    /**
     * A managed entity paired with its similarity score.
     *
     * @param <E> the entity type
     * @author 芋道源码
     */
    public static final class Match<E> {
        private final E entity;
        private final double score;

        private Match(E entity, double score) {
            this.entity = entity;
            this.score = score;
        }

        /** @return the matched managed entity */
        public E entity() {
            return entity;
        }

        /** @return the normalized similarity score */
        public double score() {
            return score;
        }
    }
}
