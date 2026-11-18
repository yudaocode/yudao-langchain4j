package dev.langchain4j.rag.content.retriever.hibernate;

import dev.langchain4j.internal.Json;
import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import javax.persistence.Tuple;
import javax.persistence.metamodel.Attribute;
import javax.persistence.metamodel.EntityType;
import javax.persistence.metamodel.IdentifiableType;
import javax.persistence.metamodel.ManagedType;
import javax.persistence.metamodel.MapAttribute;
import javax.persistence.metamodel.Metamodel;
import javax.persistence.metamodel.PluralAttribute;
import javax.persistence.metamodel.Type;
import org.hibernate.Hibernate;
import org.hibernate.MappingException;
import org.hibernate.SessionFactory;
import org.hibernate.engine.spi.SessionFactoryImplementor;
import org.hibernate.persister.entity.EntityPersister;
import org.hibernate.proxy.HibernateProxy;

/**
 * Serializes the Hibernate 5 metamodel and query projections without traversing lazy associations.
 *
 * @author 芋道源码
 */
final class Hibernate5Json {

    private Hibernate5Json() {}

    static String describeMetamodel(Metamodel metamodel) {
        Map<String, List<Map<String, Object>>> description = new LinkedHashMap<>();
        description.put("entities", new ArrayList<>());
        description.put("mappedSuperclasses", new ArrayList<>());
        description.put("embeddables", new ArrayList<>());
        metamodel.getManagedTypes().stream()
                .sorted(Comparator.comparing(type -> type.getJavaType().getName()))
                .forEach(type -> description.get(category(type)).add(describeType(type)));
        return Json.toJson(description);
    }

    private static String category(ManagedType<?> type) {
        switch (type.getPersistenceType()) {
            case ENTITY:
                return "entities";
            case MAPPED_SUPERCLASS:
                return "mappedSuperclasses";
            case EMBEDDABLE:
                return "embeddables";
            default:
                throw new IllegalArgumentException("Unsupported persistence type " + type.getPersistenceType());
        }
    }

    private static Map<String, Object> describeType(ManagedType<?> type) {
        Class<?> javaType = type.getJavaType();
        Map<String, Object> description = new LinkedHashMap<>();
        description.put("name", type instanceof EntityType ? ((EntityType<?>) type).getName() : javaType.getSimpleName());
        description.put("class", javaType.getName());
        if (type instanceof IdentifiableType) {
            IdentifiableType<?> identifiable = (IdentifiableType<?>) type;
            if (identifiable.getSupertype() != null) {
                description.put("superType", identifiable.getSupertype().getJavaType().getName());
            }
            Type<?> idType = identifiable.getIdType();
            if (idType != null && identifiable.hasSingleIdAttribute()) {
                description.put("identifierAttribute", identifiable.getId(idType.getJavaType()).getName());
            }
        }
        description.put("attributes", type.getAttributes().stream()
                .sorted(Comparator.comparing(Attribute::getName))
                .map(Hibernate5Json::describeAttribute)
                .collect(Collectors.toList()));
        return description;
    }

    private static Map<String, String> describeAttribute(Attribute<?, ?> attribute) {
        String type = attribute.getJavaType().getTypeName();
        if (attribute instanceof PluralAttribute) {
            PluralAttribute<?, ?, ?> plural = (PluralAttribute<?, ?, ?>) attribute;
            String key = plural instanceof MapAttribute ? ((MapAttribute<?, ?, ?>) plural).getKeyJavaType().getTypeName() + "," : "";
            type += "<" + key + plural.getElementType().getJavaType().getTypeName() + ">";
        }
        Map<String, String> description = new LinkedHashMap<>();
        description.put("type", type);
        description.put("name", attribute.getName());
        return description;
    }

    static String serializeResults(SessionFactory factory, List<?> results) {
        SessionFactoryImplementor factoryImplementor = factory.unwrap(SessionFactoryImplementor.class);
        return Json.toJson(results.stream()
                .map(value -> resultValue(factoryImplementor, value, true))
                .collect(Collectors.toList()));
    }

    private static Object resultValue(SessionFactoryImplementor factory, Object value, boolean expandEntity) {
        if (value == null || value instanceof Number || value instanceof Boolean || value instanceof String) {
            return value;
        }
        if (value instanceof Tuple) {
            Tuple tuple = (Tuple) value;
            return resultValue(factory, tuple.toArray(), expandEntity);
        }
        if (value.getClass().isArray()) {
            List<Object> values = new ArrayList<>();
            for (int index = 0; index < Array.getLength(value); index++) {
                values.add(resultValue(factory, Array.get(value, index), expandEntity));
            }
            return values;
        }
        if (!Hibernate.isInitialized(value)) {
            return value instanceof HibernateProxy
                    ? ((HibernateProxy) value).getHibernateLazyInitializer().getIdentifier() : null;
        }
        if (value instanceof Iterable) {
            List<Object> values = new ArrayList<>();
            ((Iterable<?>) value).forEach(element -> values.add(resultValue(factory, element, false)));
            return values;
        }
        EntityPersister persister;
        try {
            Class<?> type = value instanceof HibernateProxy
                    ? ((HibernateProxy) value).getHibernateLazyInitializer().getPersistentClass() : value.getClass();
            persister = factory.getMetamodel().entityPersister(type);
        } catch (MappingException ignored) {
            return String.valueOf(value);
        }
        if (!expandEntity) {
            return factory.getPersistenceUnitUtil().getIdentifier(value);
        }

        Map<String, Object> entity = new LinkedHashMap<>();
        String identifier = persister.getIdentifierPropertyName();
        if (identifier != null) {
            entity.put(identifier, factory.getPersistenceUnitUtil().getIdentifier(value));
        }
        for (String property : persister.getPropertyNames()) {
            if (Hibernate.isPropertyInitialized(value, property)) {
                entity.put(property, resultValue(factory, persister.getPropertyValue(value, property), false));
            }
        }
        return entity;
    }
}
