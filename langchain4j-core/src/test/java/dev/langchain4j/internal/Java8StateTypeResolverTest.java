package dev.langchain4j.internal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.langchain4j.exception.JsonTypeNotAllowedException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * Verifies modern state type IDs while retaining the existing allowlist boundary.
 *
 * @author 芋道源码
 */
class Java8StateTypeResolverTest {

    private final Json.JsonCodec codec = new JacksonStateJsonCodec(new TypeAllowlist(), null);

    @Test
    void readsImmutableMapAndPreservesItsReadOnlyContract() {
        Map<String, Object> result = (Map<String, Object>) codec.fromJson(
                "[\"java.util.ImmutableCollections$Map1\",{\"literal\":\"java.util.ImmutableCollections$List12\"}]",
                Object.class);
        assertEquals("java.util.ImmutableCollections$List12", result.get("literal"));
        assertThrows(UnsupportedOperationException.class, () -> result.put("new", "value"));
    }

    @Test
    void readsImmutableListAndSetWithoutMakingThemMutable() {
        List<?> list =
                (List<?>) codec.fromJson("[\"java.util.ImmutableCollections$List12\",[\"one\",\"two\"]]", Object.class);
        Set<?> set =
                (Set<?>) codec.fromJson("[\"java.util.ImmutableCollections$Set12\",[\"one\",\"two\"]]", Object.class);
        assertEquals(2, list.size());
        assertEquals(2, set.size());
        assertThrows(UnsupportedOperationException.class, list::clear);
        assertThrows(UnsupportedOperationException.class, set::clear);
    }

    @Test
    void rejectsUnknownImplementationNamesAndDisallowedUserTypes() {
        assertThrows(
                JsonTypeNotAllowedException.class,
                () -> codec.fromJson("[\"java.util.ImmutableCollections$Unknown\",{}]", Object.class));
        assertThrows(
                JsonTypeNotAllowedException.class,
                () -> codec.fromJson("[\"java.lang.ProcessBuilder\",{}]", Object.class));
    }
}
