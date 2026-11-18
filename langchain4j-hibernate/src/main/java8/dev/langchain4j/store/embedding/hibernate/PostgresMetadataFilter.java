package dev.langchain4j.store.embedding.hibernate;

import dev.langchain4j.store.embedding.filter.Filter;
import dev.langchain4j.store.embedding.filter.comparison.ContainsString;
import dev.langchain4j.store.embedding.filter.comparison.IsEqualTo;
import dev.langchain4j.store.embedding.filter.comparison.IsGreaterThan;
import dev.langchain4j.store.embedding.filter.comparison.IsGreaterThanOrEqualTo;
import dev.langchain4j.store.embedding.filter.comparison.IsIn;
import dev.langchain4j.store.embedding.filter.comparison.IsLessThan;
import dev.langchain4j.store.embedding.filter.comparison.IsLessThanOrEqualTo;
import dev.langchain4j.store.embedding.filter.comparison.IsNotEqualTo;
import dev.langchain4j.store.embedding.filter.comparison.IsNotIn;
import dev.langchain4j.store.embedding.filter.logical.And;
import dev.langchain4j.store.embedding.filter.logical.Not;
import dev.langchain4j.store.embedding.filter.logical.Or;
import java.math.BigDecimal;
import java.util.Collection;
import java.util.Map;
import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.Expression;
import javax.persistence.criteria.Path;
import javax.persistence.criteria.Predicate;
import javax.persistence.criteria.Root;
import org.hibernate.persister.entity.EntityPersister;
import org.hibernate.type.AbstractStandardBasicType;
import org.hibernate.type.Type;

/**
 * Translates LangChain4j metadata filters to PostgreSQL Criteria expressions.
 *
 * @author 芋道源码
 */
final class PostgresMetadataFilter {

    private final EntityPersister persister;
    private final Map<String, String> mapped;
    private final String unmapped;

    PostgresMetadataFilter(EntityPersister persister, Map<String, String> mapped, String unmapped) {
        this.persister = persister;
        this.mapped = mapped;
        this.unmapped = unmapped;
    }

    Predicate predicate(Root<?> root, CriteriaBuilder cb, Filter filter) {
        if (filter instanceof And) {
            And and = (And) filter;
            return cb.and(predicate(root, cb, and.left()), predicate(root, cb, and.right()));
        }
        if (filter instanceof Or) {
            Or or = (Or) filter;
            return cb.or(predicate(root, cb, or.left()), predicate(root, cb, or.right()));
        }
        if (filter instanceof Not) {
            return cb.not(predicate(root, cb, ((Not) filter).expression()));
        }
        if (filter instanceof IsEqualTo) {
            IsEqualTo eq = (IsEqualTo) filter;
            return comparison(root, cb, eq.key(), eq.comparisonValue(), Comparison.EQUAL);
        }
        if (filter instanceof IsNotEqualTo) {
            IsNotEqualTo neq = (IsNotEqualTo) filter;
            return cb.not(comparison(root, cb, neq.key(), neq.comparisonValue(), Comparison.EQUAL));
        }
        if (filter instanceof IsGreaterThan) {
            IsGreaterThan value = (IsGreaterThan) filter;
            return comparison(root, cb, value.key(), value.comparisonValue(), Comparison.GREATER);
        }
        if (filter instanceof IsGreaterThanOrEqualTo) {
            IsGreaterThanOrEqualTo value = (IsGreaterThanOrEqualTo) filter;
            return comparison(root, cb, value.key(), value.comparisonValue(), Comparison.AT_LEAST);
        }
        if (filter instanceof IsLessThan) {
            IsLessThan value = (IsLessThan) filter;
            return comparison(root, cb, value.key(), value.comparisonValue(), Comparison.LESS);
        }
        if (filter instanceof IsLessThanOrEqualTo) {
            IsLessThanOrEqualTo value = (IsLessThanOrEqualTo) filter;
            return comparison(root, cb, value.key(), value.comparisonValue(), Comparison.AT_MOST);
        }
        if (filter instanceof IsIn) {
            IsIn in = (IsIn) filter;
            return inclusion(root, cb, in.key(), in.comparisonValues());
        }
        if (filter instanceof IsNotIn) {
            IsNotIn notIn = (IsNotIn) filter;
            return cb.not(inclusion(root, cb, notIn.key(), notIn.comparisonValues()));
        }
        if (filter instanceof ContainsString) {
            ContainsString contains = (ContainsString) filter;
            Expression<String> value = mapped.containsKey(contains.key())
                    ? path(root, mapped.get(contains.key())).as(String.class)
                    : jsonText(root, cb, contains.key());
            return cb.and(cb.isNotNull(value), cb.gt(cb.locate(value, contains.comparisonValue()), 0));
        }
        throw new IllegalArgumentException("Unsupported PostgreSQL metadata filter: " + filter.getClass().getName());
    }

    private Predicate inclusion(Root<?> root, CriteriaBuilder cb, String key, Collection<?> values) {
        Predicate result = cb.disjunction();
        for (Object value : values) {
            result = cb.or(result, comparison(root, cb, key, value, Comparison.EQUAL));
        }
        return result;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private Predicate comparison(Root<?> root, CriteriaBuilder cb, String key, Object value, Comparison operation) {
        Expression<?> field;
        Object expected;
        String attribute = mapped.get(key);
        if (attribute != null) {
            field = path(root, attribute);
            expected = coerce(persister.getPropertyType(attribute), value);
        } else if (value instanceof Number) {
            field = cb.function("lc_pg_metadata_number", BigDecimal.class, root.get(unmapped), cb.literal(key));
            expected = new BigDecimal(value.toString());
        } else {
            field = jsonText(root, cb, key);
            expected = value.toString();
        }
        Predicate predicate;
        if (operation == Comparison.EQUAL) {
            predicate = cb.equal(field, expected);
        } else {
            Expression comparableField = field;
            Comparable comparableValue = (Comparable) expected;
            switch (operation) {
                case GREATER: predicate = cb.greaterThan(comparableField, comparableValue); break;
                case AT_LEAST: predicate = cb.greaterThanOrEqualTo(comparableField, comparableValue); break;
                case LESS: predicate = cb.lessThan(comparableField, comparableValue); break;
                case AT_MOST: predicate = cb.lessThanOrEqualTo(comparableField, comparableValue); break;
                default: throw new AssertionError(operation);
            }
        }
        return cb.and(cb.isNotNull(field), predicate);
    }

    private Expression<String> jsonText(Root<?> root, CriteriaBuilder cb, String key) {
        return cb.function("lc_pg_metadata_text", String.class, root.get(unmapped), cb.literal(key));
    }

    static Path<?> path(Root<?> root, String property) {
        Path<?> path = root;
        for (String component : property.split("\\.")) {
            path = path.get(component);
        }
        return path;
    }

    static Object coerce(Type type, Object value) {
        if (value == null || type.getReturnedClass().isInstance(value)) {
            return value;
        }
        if (type instanceof AbstractStandardBasicType<?>) {
            return ((AbstractStandardBasicType<?>) type).getJavaTypeDescriptor().fromString(value.toString());
        }
        Class<?> target = type.getReturnedClass();
        if (target.isEnum()) {
            for (Object constant : target.getEnumConstants()) {
                if (((Enum<?>) constant).name().equals(value.toString())) {
                    return constant;
                }
            }
        }
        throw new IllegalArgumentException("Can't convert metadata value to " + target.getName());
    }

    /** @author 芋道源码 */
    private enum Comparison { EQUAL, GREATER, AT_LEAST, LESS, AT_MOST }
}
