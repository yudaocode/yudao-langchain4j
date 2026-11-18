package dev.langchain4j.store.embedding.hibernate;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * @author 芋道源码
 */
class HibernateEmbeddingStoreTest {

    @ParameterizedTest
    @ValueSource(strings = {"", "x;drop table y", "a.b.c", "a.", "UpperCase", "x-y"})
    void rejectsUnqualifiedInputBeforeConnecting(String table) {
        DataSource source = mock(DataSource.class);
        assertThrows(IllegalArgumentException.class, () -> HibernateEmbeddingStore.dynamicDatasourceBuilder()
                .dataSource(source).table(table).dimension(3).build());
        verifyNoInteractions(source);
    }

    @Test
    void rejectsUnportedDatabasesBeforeConnecting() {
        DataSource source = mock(DataSource.class);
        assertThrows(IllegalArgumentException.class, () -> HibernateEmbeddingStore.dynamicDatasourceBuilder()
                .dataSource(source).databaseKind(DatabaseKind.ORACLE).table("embeddings").dimension(3).build());
        verifyNoInteractions(source);
    }

    @Test
    void rejectsUnsafeIndexOptionsBeforeConnecting() {
        DataSource source = mock(DataSource.class);
        assertThrows(IllegalArgumentException.class, () -> HibernateEmbeddingStore.dynamicDatasourceBuilder()
                .dataSource(source).table("embeddings").dimension(3).createIndex(true)
                .indexOptions("lists=1); select 1").build());
        verifyNoInteractions(source);
    }

    @Test
    void rejectsInvalidDimensionsBeforeConnecting() {
        DataSource source = mock(DataSource.class);
        assertThrows(IllegalArgumentException.class, () -> HibernateEmbeddingStore.dynamicDatasourceBuilder()
                .dataSource(source).table("embeddings").dimension(0).build());
        verifyNoInteractions(source);
    }
}
