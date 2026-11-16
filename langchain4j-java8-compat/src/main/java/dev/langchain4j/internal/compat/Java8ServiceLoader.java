package dev.langchain4j.internal.compat;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.ServiceConfigurationError;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Java 8 service metadata lookup, without constructing an unaccepted provider.
 *
 * @author 芋道源码
 */
public final class Java8ServiceLoader {
    private Java8ServiceLoader() {}

    public static final class Provider<S> {
        private final Class<S> service;
        private final ClassLoader loader;
        private final String name;

        private Provider(Class<S> service, ClassLoader loader, String name) {
            this.service = service;
            this.loader = loader;
            this.name = name;
        }

        public Class<? extends S> type() {
            try {
                return Class.forName(name, false, loader).asSubclass(service);
            } catch (ClassNotFoundException | ClassCastException e) {
                throw new ServiceConfigurationError(service.getName() + ": " + name, e);
            }
        }

        public S get() {
            try {
                return type().getConstructor().newInstance();
            } catch (ReflectiveOperationException e) {
                throw new ServiceConfigurationError(service.getName() + ": " + name, e);
            }
        }
    }

    public static <S> Stream<Provider<S>> load(Class<S> service) {
        ClassLoader loader = Thread.currentThread().getContextClassLoader();
        return load(service, loader == null ? ClassLoader.getSystemClassLoader() : loader);
    }

    public static <S> Stream<Provider<S>> load(Class<S> service, ClassLoader loader) {
        List<Provider<S>> providers = new ArrayList<>();
        Set<String> names = new LinkedHashSet<>();
        try {
            Enumeration<URL> resources = loader.getResources("META-INF/services/" + service.getName());
            while (resources.hasMoreElements()) {
                URL resource = resources.nextElement();
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                        resource.openStream(), StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        int comment = line.indexOf('#');
                        String name = (comment >= 0 ? line.substring(0, comment) : line).trim();
                        if (!name.isEmpty() && names.add(name)) {
                            providers.add(new Provider<>(service, loader, name));
                        }
                    }
                }
            }
        } catch (java.io.IOException e) {
            throw new ServiceConfigurationError("Cannot read providers of " + service.getName(), e);
        }
        return providers.stream();
    }
}
