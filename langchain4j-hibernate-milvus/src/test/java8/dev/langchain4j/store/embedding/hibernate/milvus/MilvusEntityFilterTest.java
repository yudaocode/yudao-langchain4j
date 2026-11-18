package dev.langchain4j.store.embedding.hibernate.milvus;

import static dev.langchain4j.store.embedding.filter.MetadataFilterBuilder.metadataKey;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.langchain4j.store.embedding.filter.Filter;
import dev.langchain4j.store.embedding.filter.logical.Not;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * @author 芋道源码
 */
class MilvusEntityFilterTest {

    @Test
    void translatesAndGroupsWithHibernateColumnNames() {
        MilvusEntityFilter compiler = new MilvusEntityFilter(key -> key.equals("locale")
                ? new MilvusEntityMapping.Field(key, "doc_locale", String.class, false)
                : new MilvusEntityMapping.Field(key, "doc_rank", Integer.class, false));
        Filter filter = metadataKey("locale").isEqualTo("zh")
                .or(metadataKey("locale").isEqualTo("en"))
                .and(new Not(metadataKey("rank").isLessThan(7)));
        assertEquals("((doc_locale == \"zh\" or doc_locale == \"en\") and not (doc_rank < 7))",
                compiler.expression(filter));
    }

    @Test
    void quotesStringsWithoutCreatingAnotherExpression() {
        MilvusEntityFilter compiler = new MilvusEntityFilter(key ->
                new MilvusEntityMapping.Field(key, "name", String.class, false));
        assertEquals("name == \"芋道\\\" or true\\\\n\"",
                compiler.expression(metadataKey("name").isEqualTo("芋道\" or true\\n")));
    }

    @Test
    void supportsMembershipAndExactIntegerCoercion() {
        MilvusEntityFilter compiler = new MilvusEntityFilter(key ->
                new MilvusEntityMapping.Field(key, "position", Integer.class, false));
        assertEquals("position in [7]", compiler.expression(metadataKey("rank").isIn(7L)));
        assertEquals("position not in [7]", compiler.expression(metadataKey("rank").isNotIn(7L)));
        assertEquals("position >= 7", compiler.expression(metadataKey("rank").isGreaterThanOrEqualTo(7L)));
        assertThrows(IllegalArgumentException.class,
                () -> compiler.expression(metadataKey("rank").isEqualTo(7.25)));
        assertThrows(IllegalArgumentException.class,
                () -> compiler.expression(metadataKey("rank").isEqualTo(2147483648L)));
    }

    @Test
    void mapsUuidAndRejectsUnsupportedTypesAndIdentifiers() {
        MilvusEntityMapping.Field id = new MilvusEntityMapping.Field("id", "doc_id", UUID.class, true);
        UUID value = UUID.fromString("68a0229e-0153-4d62-a0c1-5f08c799b1ab");
        assertEquals(value.toString(), id.value(value));
        assertEquals(value, id.read(value.toString()));
        assertEquals("article_2", MilvusEntityMapping.identifier("article_2"));
        assertThrows(IllegalArgumentException.class, () -> MilvusEntityMapping.identifier("doc.field"));
        assertThrows(IllegalArgumentException.class, () -> MilvusEntityMapping.identifier("doc; DROP TABLE"));
        assertThrows(IllegalArgumentException.class,
                () -> new MilvusEntityMapping.Field("chars", "chars", char[].class, false));
        assertTrue(System.getProperty("java.version").startsWith("1.8."));
    }
}
