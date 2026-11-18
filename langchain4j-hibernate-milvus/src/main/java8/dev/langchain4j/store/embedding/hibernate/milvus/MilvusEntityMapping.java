package dev.langchain4j.store.embedding.hibernate.milvus;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import io.milvus.v2.common.DataType;
import io.milvus.v2.service.collection.request.CreateCollectionReq.FieldSchema;
import java.io.Serializable;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.hibernate.engine.spi.SharedSessionContractImplementor;
import org.hibernate.persister.entity.AbstractEntityPersister;
import org.hibernate.persister.entity.EntityPersister;

/**
 * Maps one flat Hibernate entity to a Milvus collection.
 *
 * @author 芋道源码
 */
final class MilvusEntityMapping<E> {

    static final Gson JSON = new GsonBuilder().disableHtmlEscaping().create();

    private final Class<E> entityType;
    private final AbstractEntityPersister persister;
    private final Field id;
    private final Field vector;
    private final List<Field> properties;
    private final Map<String, Field> fields;
    private final int dimension;

    MilvusEntityMapping(Class<E> entityType, EntityPersister persister, String vectorProperty, int dimension) {
        if (!(persister instanceof AbstractEntityPersister)) {
            throw new IllegalArgumentException("Milvus requires a Hibernate entity with single-table metadata");
        }
        this.entityType = entityType;
        this.persister = (AbstractEntityPersister) persister;
        if (this.persister.getTableSpan() != 1 || this.persister.isInherited() || this.persister.hasSubclasses()) {
            throw new IllegalArgumentException("Milvus requires a single table without entity inheritance");
        }
        if (dimension < 2 || dimension > 32768) {
            throw new IllegalArgumentException("Milvus dense vector dimension must be between 2 and 32768");
        }
        this.dimension = dimension;
        String idProperty = persister.getIdentifierPropertyName();
        Class<?> idType = persister.getIdentifierType().getReturnedClass();
        if (idProperty == null || (idType != String.class && idType != UUID.class && idType != Long.class
                && idType != long.class)) {
            throw new IllegalArgumentException("Milvus requires a single assigned String, UUID or Long identifier");
        }
        id = new Field(idProperty, singleColumn(this.persister.getIdentifierColumnNames()), idType, true);
        Map<String, Field> mapped = new LinkedHashMap<>();
        mapped.put(id.property, id);
        Set<String> columnNames = new HashSet<>();
        columnNames.add(id.column);
        properties = new ArrayList<>();
        String[] names = persister.getPropertyNames();
        for (int i = 0; i < names.length; i++) {
            Class<?> type = persister.getPropertyTypes()[i].getReturnedClass();
            if (persister.getPropertyTypes()[i].isAssociationType() || persister.getPropertyTypes()[i].isComponentType()
                    || ((type == float[].class) != names[i].equals(vectorProperty))) {
                throw new IllegalArgumentException("Unsupported Milvus property mapping: " + names[i]);
            }
            Field field = new Field(names[i], singleColumn(this.persister.getPropertyColumnNames(i)), type, false);
            if (!columnNames.add(field.column)) {
                throw new IllegalArgumentException("Duplicate Milvus column: " + field.column);
            }
            mapped.put(names[i], field);
            properties.add(field);
        }
        vector = mapped.remove(vectorProperty);
        if (vector == null || vector.type != DataType.FloatVector) {
            throw new IllegalArgumentException("Milvus vector property must be a mapped float[]: " + vectorProperty);
        }
        fields = Collections.unmodifiableMap(mapped);
    }

    private static String singleColumn(String[] names) {
        if (names == null || names.length != 1) {
            throw new IllegalArgumentException("Milvus entity properties require a single named column");
        }
        return identifier(names[0]);
    }

    static String identifier(String name) {
        if (name == null || !name.matches("[A-Za-z_][A-Za-z0-9_]{0,254}")) {
            throw new IllegalArgumentException("Invalid Milvus identifier: " + name);
        }
        return name;
    }

    Field scalar(String property) {
        Field field = fields.get(property);
        if (field == null) {
            throw new IllegalArgumentException("Unknown Milvus scalar property: " + property);
        }
        return field;
    }

    String idColumn() {
        return id.column;
    }

    String vectorColumn() {
        return vector.column;
    }

    List<FieldSchema> schema() {
        List<FieldSchema> schema = new ArrayList<>();
        schema.add(id.schema(dimension));
        for (Field field : properties) {
            schema.add(field.schema(dimension));
        }
        return schema;
    }

    List<String> outputColumns() {
        List<String> columns = new ArrayList<>();
        for (Field field : properties) {
            columns.add(field.column);
        }
        return columns;
    }

    JsonObject row(E entity) {
        JsonObject row = new JsonObject();
        row.add(id.column, JSON.toJsonTree(identifierValue(persister.getIdentifier(entity))));
        Object[] values = persister.getPropertyValues(entity);
        for (int i = 0; i < values.length; i++) {
            Field field = properties.get(i);
            if (field == vector) {
                JsonArray array = new JsonArray();
                for (float entry : validateVector((float[]) values[i])) {
                    array.add(entry);
                }
                row.add(field.column, array);
            } else {
                row.add(field.column, JSON.toJsonTree(field.value(values[i])));
            }
        }
        return row;
    }

    Object identifierValue(Object value) {
        Object key = id.value(value);
        if (key instanceof String && ((String) key).trim().isEmpty()) {
            throw new IllegalArgumentException("Milvus identifier cannot be blank");
        }
        return key;
    }

    float[] validateVector(float[] input) {
        if (input == null || input.length != dimension) {
            throw new IllegalArgumentException("Milvus vector dimension must be " + dimension);
        }
        double length = 0;
        for (float value : input) {
            if (!Float.isFinite(value)) {
                throw new IllegalArgumentException("Milvus vector must contain only finite values");
            }
            length += (double) value * value;
        }
        if (length == 0) {
            throw new IllegalArgumentException("Milvus cosine search requires a non-zero vector");
        }
        return input;
    }

    float[] readVector(Map<String, Object> row) {
        Object value = row.get(vector.column);
        if (value instanceof float[]) {
            return ((float[]) value).clone();
        }
        List<?> numbers = (List<?>) value;
        float[] result = new float[numbers.size()];
        for (int i = 0; i < result.length; i++) {
            result[i] = ((Number) numbers.get(i)).floatValue();
        }
        return result;
    }

    E hydrate(SharedSessionContractImplementor session, Object key, Map<String, Object> row, float[] embedding) {
        E entity = entityType.cast(persister.instantiate((Serializable) id.read(key), session));
        for (int i = 0; i < properties.size(); i++) {
            Field field = properties.get(i);
            Object value = field == vector ? embedding.clone() : field.read(row.get(field.column));
            persister.setPropertyValue(entity, i, value);
        }
        return entity;
    }

    /** @author 芋道源码 */
    static final class Field {

        final String property;
        final String column;
        final DataType type;
        private final Class<?> javaType;
        private final boolean primary;

        Field(String property, String column, Class<?> javaType, boolean primary) {
            this.property = property;
            this.column = column;
            this.javaType = javaType;
            this.primary = primary;
            this.type = dataType(javaType);
        }

        private static DataType dataType(Class<?> type) {
            if (type == String.class || type == UUID.class) return DataType.VarChar;
            if (type == Long.class || type == long.class) return DataType.Int64;
            if (type == Integer.class || type == int.class) return DataType.Int32;
            if (type == Double.class || type == double.class) return DataType.Double;
            if (type == Float.class || type == float.class) return DataType.Float;
            if (type == Boolean.class || type == boolean.class) return DataType.Bool;
            if (type == float[].class) return DataType.FloatVector;
            throw new IllegalArgumentException("Unsupported Milvus property type: " + type.getName());
        }

        FieldSchema schema(int dimension) {
            FieldSchema.FieldSchemaBuilder builder = FieldSchema.builder()
                    .name(column).dataType(type).isPrimaryKey(primary).isNullable(false);
            if (type == DataType.VarChar) builder.maxLength(maxLength());
            if (type == DataType.FloatVector) builder.dimension(dimension);
            return builder.build();
        }

        private int maxLength() {
            if (javaType == UUID.class) return 36;
            return primary ? 512 : 65535;
        }

        Object value(Object input) {
            if (javaType == UUID.class && (input instanceof UUID || input instanceof String)) {
                return UUID.fromString(input.toString()).toString();
            }
            if (type == DataType.VarChar && input instanceof String
                    && ((String) input).getBytes(StandardCharsets.UTF_8).length <= maxLength()) {
                return input;
            }
            if (type == DataType.Bool && input instanceof Boolean) return input;
            if (input instanceof Number) {
                Number number = (Number) input;
                if (Double.isFinite(number.doubleValue())) {
                    if (type == DataType.Float && Float.isFinite(number.floatValue())) return number.floatValue();
                    if (type == DataType.Double) return number.doubleValue();
                    try {
                        if (type == DataType.Int32) return new BigDecimal(number.toString()).intValueExact();
                        if (type == DataType.Int64) return new BigDecimal(number.toString()).longValueExact();
                    } catch (ArithmeticException e) {
                        throw new IllegalArgumentException("Milvus property is outside its integer range: " + property, e);
                    }
                }
            }
            throw new IllegalArgumentException("Invalid Milvus " + type + " value for " + property);
        }

        Object read(Object input) {
            Object value = value(input);
            return javaType == UUID.class ? UUID.fromString((String) value) : value;
        }
    }
}
