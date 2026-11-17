package dev.langchain4j.internal;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.DatabindContext;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.cfg.MapperConfig;
import com.fasterxml.jackson.databind.jsontype.NamedType;
import com.fasterxml.jackson.databind.jsontype.PolymorphicTypeValidator;
import com.fasterxml.jackson.databind.jsontype.TypeIdResolver;
import com.fasterxml.jackson.databind.jsontype.impl.ClassNameIdResolver;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;

/**
 * Reads known immutable JDK collection type IDs from modern-JDK state snapshots.
 *
 * @author 芋道源码
 */
final class Java8StateTypeResolver extends ObjectMapper.DefaultTypeResolverBuilder {

    private static final long serialVersionUID = 1L;

    Java8StateTypeResolver(PolymorphicTypeValidator validator) {
        super(ObjectMapper.DefaultTyping.OBJECT_AND_NON_CONCRETE, validator);
        init(JsonTypeInfo.Id.CLASS, null);
        inclusion(JsonTypeInfo.As.WRAPPER_ARRAY);
    }

    @Override
    protected TypeIdResolver idResolver(
            MapperConfig<?> config,
            JavaType baseType,
            PolymorphicTypeValidator validator,
            Collection<NamedType> subtypes,
            boolean forSerialization,
            boolean forDeserialization) {
        if (forDeserialization) {
            return new CollectionAliasResolver(baseType, config, validator);
        }
        return super.idResolver(config, baseType, validator, subtypes, forSerialization, forDeserialization);
    }

    /**
     * Resolves only the fixed JDK collection aliases; the same validator checks the result.
     *
     * @author 芋道源码
     */
    private static final class CollectionAliasResolver extends ClassNameIdResolver {

        private static final long serialVersionUID = 1L;

        CollectionAliasResolver(JavaType baseType, MapperConfig<?> config, PolymorphicTypeValidator validator) {
            super(baseType, config.getTypeFactory(), validator);
        }

        @Override
        public JavaType typeFromId(DatabindContext context, String id) throws IOException {
            switch (id) {
                case "java.util.ImmutableCollections$Map0":
                case "java.util.ImmutableCollections$Map1":
                case "java.util.ImmutableCollections$MapN":
                    id = Collections.unmodifiableMap(new LinkedHashMap<>())
                            .getClass()
                            .getName();
                    break;
                case "java.util.ImmutableCollections$List0":
                case "java.util.ImmutableCollections$List1":
                case "java.util.ImmutableCollections$List2":
                case "java.util.ImmutableCollections$List12":
                case "java.util.ImmutableCollections$ListN":
                    id = Collections.unmodifiableList(new ArrayList<>())
                            .getClass()
                            .getName();
                    break;
                case "java.util.ImmutableCollections$Set0":
                case "java.util.ImmutableCollections$Set1":
                case "java.util.ImmutableCollections$Set2":
                case "java.util.ImmutableCollections$Set12":
                case "java.util.ImmutableCollections$SetN":
                    id = Collections.unmodifiableSet(new LinkedHashSet<>())
                            .getClass()
                            .getName();
                    break;
                default:
                    break;
            }
            return super.typeFromId(context, id);
        }
    }
}
