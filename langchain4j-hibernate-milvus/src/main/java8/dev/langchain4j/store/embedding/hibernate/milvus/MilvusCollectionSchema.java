package dev.langchain4j.store.embedding.hibernate.milvus;

import io.milvus.v2.client.MilvusClientV2;
import io.milvus.v2.common.IndexParam;
import io.milvus.v2.exception.MilvusClientException;
import io.milvus.v2.service.collection.request.CreateCollectionReq;
import io.milvus.v2.service.collection.request.CreateCollectionReq.FieldSchema;
import io.milvus.v2.service.collection.request.DescribeCollectionReq;
import io.milvus.v2.service.collection.request.HasCollectionReq;
import io.milvus.v2.service.collection.request.LoadCollectionReq;
import io.milvus.v2.service.collection.response.DescribeCollectionResp;
import io.milvus.v2.service.index.request.CreateIndexReq;
import io.milvus.v2.service.index.request.DescribeIndexReq;
import io.milvus.v2.service.index.response.DescribeIndexResp.IndexDesc;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Creates and verifies the Milvus schema without altering existing collections.
 *
 * @author 芋道源码
 */
final class MilvusCollectionSchema {

    // Modern Milvus status code for IndexNotFound.
    private static final int INDEX_NOT_FOUND = 700;

    private final MilvusClientV2 client;
    private final String collection;
    private final MilvusEntityMapping<?> mapping;

    MilvusCollectionSchema(MilvusClientV2 client, String collection, MilvusEntityMapping<?> mapping) {
        this.client = client;
        this.collection = collection;
        this.mapping = mapping;
    }

    void initialize(boolean waitForSchema) {
        HasCollectionReq exists = HasCollectionReq.builder().collectionName(collection).build();
        List<FieldSchema> expected = mapping.schema();
        if (!client.hasCollection(exists)) {
            try {
                client.createCollection(CreateCollectionReq.builder()
                        .collectionName(collection)
                        .collectionSchema(CreateCollectionReq.CollectionSchema.builder()
                                .enableDynamicField(false).fieldSchemaList(expected).build())
                        .build());
            } catch (MilvusClientException e) {
                // A concurrent initializer may have created the collection.
                if (!client.hasCollection(exists)) throw e;
            }
        }
        validateFields(expected);
        String vector = mapping.vectorColumn();
        IndexDesc index = describeIndex(vector);
        if (index == null) {
            IndexParam param = IndexParam.builder().fieldName(vector).indexName(vector)
                    .indexType(IndexParam.IndexType.FLAT).metricType(IndexParam.MetricType.COSINE).build();
            try {
                client.createIndex(CreateIndexReq.builder().collectionName(collection).sync(waitForSchema)
                        .indexParams(Collections.singletonList(param)).build());
            } catch (MilvusClientException e) {
                // If another initializer installed an index, validate what actually exists.
                index = describeIndex(vector);
                if (index == null) throw e;
            }
            if (index == null) index = describeIndex(vector);
        }
        if (index == null || index.getMetricType() != IndexParam.MetricType.COSINE) {
            throw new IllegalStateException("Milvus collection " + collection + " requires a COSINE index on " + vector);
        }
        client.loadCollection(LoadCollectionReq.builder().collectionName(collection).sync(waitForSchema).build());
    }

    private void validateFields(List<FieldSchema> expected) {
        DescribeCollectionResp description = client.describeCollection(
                DescribeCollectionReq.builder().collectionName(collection).build());
        if (!mapping.idColumn().equals(description.getPrimaryFieldName())
                || Boolean.TRUE.equals(description.getAutoID()) || Boolean.TRUE.equals(description.getEnableDynamicField())) {
            throw new IllegalStateException("Incompatible Milvus primary key or dynamic fields in " + collection);
        }
        Map<String, FieldSchema> actual = new HashMap<>();
        for (FieldSchema field : description.getCollectionSchema().getFieldSchemaList()) {
            actual.put(field.getName(), field);
        }
        if (actual.size() != expected.size()) {
            throw new IllegalStateException("Incompatible Milvus field count in " + collection);
        }
        for (FieldSchema want : expected) {
            FieldSchema field = actual.get(want.getName());
            if (field == null || field.getDataType() != want.getDataType()
                    || Boolean.TRUE.equals(field.getIsNullable())
                    || Boolean.TRUE.equals(field.getAutoID())
                    || !Objects.equals(want.getDimension(), field.getDimension())
                    || !Objects.equals(want.getMaxLength(), field.getMaxLength())) {
                throw new IllegalStateException("Incompatible Milvus field " + collection + "." + want.getName());
            }
        }
    }

    private IndexDesc describeIndex(String vector) {
        // Query by field name to also discover indexes provisioned with application-specific names.
        try {
            return client.describeIndex(DescribeIndexReq.builder().collectionName(collection).fieldName(vector).build())
                    .getIndexDescByFieldName(vector);
        } catch (MilvusClientException e) {
            if (e.getServerErrCode() == INDEX_NOT_FOUND
                    || e.getLegacyServerCode() == io.milvus.grpc.ErrorCode.IndexNotExist_VALUE) {
                return null;
            }
            throw e;
        }
    }
}
