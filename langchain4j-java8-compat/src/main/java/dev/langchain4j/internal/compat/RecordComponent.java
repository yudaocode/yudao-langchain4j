package dev.langchain4j.internal.compat;

import java.lang.reflect.Field;
import java.lang.reflect.Type;

/**
 * Record component metadata retained alongside Java 8 immutable classes.
 *
 * @author 芋道源码
 */
public final class RecordComponent {
    private final Field field;
    public RecordComponent(Field field) { this.field = field; }
    public String getName() { return field.getName(); }
    public Class<?> getType() { return field.getType(); }
    public Type getGenericType() { return field.getGenericType(); }
}
