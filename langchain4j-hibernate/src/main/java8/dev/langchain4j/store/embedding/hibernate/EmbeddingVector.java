package dev.langchain4j.store.embedding.hibernate;

import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

/**
 * Marks the persistent attribute of an {@code @Entity} that represents
 * the embedding vector.
 *
 */
@Target({METHOD, FIELD})
@Retention(RUNTIME)
public @interface EmbeddingVector {
    /**
     * Defines the distance function to use for vector search on the annotated attribute.
     */
    DistanceFunction distance() default DistanceFunction.COSINE;
}
