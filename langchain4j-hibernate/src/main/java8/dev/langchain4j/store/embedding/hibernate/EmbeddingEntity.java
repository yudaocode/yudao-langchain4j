package dev.langchain4j.store.embedding.hibernate;

import java.util.UUID;

/**
 * Entity for the dynamically configured PostgreSQL store.
 *
 * @author 芋道源码
 */
public class EmbeddingEntity {
    private UUID id;

    @EmbeddedText
    private String text;

    @EmbeddingVector
    private float[] embedding;

    @UnmappedMetadata
    private String metadata;
}
