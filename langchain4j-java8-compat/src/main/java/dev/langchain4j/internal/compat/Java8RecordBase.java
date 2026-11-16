package dev.langchain4j.internal.compat;

/**
 * Common immutable-record parent for Java 8, preserving record subtype queries.
 *
 * @author 芋道源码
 */
public abstract class Java8RecordBase {
    protected Java8RecordBase() {}
    @Override public abstract boolean equals(Object other);
    @Override public abstract int hashCode();
    @Override public abstract String toString();
}
