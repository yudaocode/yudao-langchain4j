package dev.langchain4j.store.embedding.hibernate.pgvector;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.store.embedding.hibernate.pgvector.HibernatePgVectorSearch.Metric;
import java.io.Serializable;
import javax.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/**
 * @author 芋道源码
 */
class HibernatePgVectorSearchTest {

    @ParameterizedTest
    @EnumSource(Metric.class)
    void distanceThresholdAndSimilarityAgree(Metric metric) {
        double distance = metric.maximumDistance(0.8);
        assertEquals(0.8, metric.score(distance), 1e-12);
        assertEquals(1.0, metric.score(metric.maximumDistance(1.0)), 1e-12);
    }

    @Test
    void innerProductScoreClampsForNonNormalizedEmbeddings() {
        assertEquals(1.0, Metric.INNER_PRODUCT.score(-2), 1e-12);
        assertEquals(0.0, Metric.INNER_PRODUCT.score(2), 1e-12);
    }

    @Test
    void rejectsInvalidSearchBeforeTouchingHibernate() {
        EntityManager entityManager = mock(EntityManager.class);
        HibernatePgVectorSearch<Object> search = new HibernatePgVectorSearch<>(Object.class, "vector");
        Embedding vector = Embedding.from(new float[] {1, 0, 0});
        assertThrows(IllegalArgumentException.class,
                () -> search.search(entityManager, vector, Metric.COSINE, Double.NaN, 1, null));
        assertThrows(IllegalArgumentException.class,
                () -> search.search(entityManager, vector, Metric.COSINE, 0.0, 0, null));
        assertThrows(IllegalArgumentException.class,
                () -> search.search(entityManager, Embedding.from(new float[] {0, 0, 0}), Metric.COSINE, 0, 1, null));
        assertThrows(IllegalArgumentException.class,
                () -> search.search(entityManager, Embedding.from(new float[] {Float.POSITIVE_INFINITY}),
                        Metric.EUCLIDEAN, 0, 1, null));
        verifyNoInteractions(entityManager);
    }

    @Test
    void snapshotsMutableVectorsForHibernateCache() {
        PgVectorType type = new PgVectorType();
        float[] original = new float[] {1, 2, 3};
        Serializable assembled = type.disassemble(original);
        float[] restored = (float[]) type.assemble(assembled, null);
        assertNotSame(original, assembled);
        assertNotSame(assembled, restored);
        original[0] = -5;
        restored[1] = -7;
        assertArrayEquals(new float[] {1, 2, 3}, (float[]) assembled);
    }
}
