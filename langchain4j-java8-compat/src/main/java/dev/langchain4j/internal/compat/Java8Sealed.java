package dev.langchain4j.internal.compat;

/**
 * Retains the declared subtype metadata of a sealed hierarchy on Java 8.
 *
 * @author 芋道源码
 */
@java.lang.annotation.Retention(java.lang.annotation.RetentionPolicy.RUNTIME)
@java.lang.annotation.Target(java.lang.annotation.ElementType.TYPE)
public @interface Java8Sealed {
    Class<?>[] value();
}
