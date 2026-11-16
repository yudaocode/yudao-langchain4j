/*
 * Copyright 2023-present the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package dev.langchain4j.internal.compat;

import java.io.IOException;
import java.nio.charset.Charset;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * Platform operations needed by generated Java 8 sources.
 *
 * @author 芋道源码
 * @since 2.0.0
 */
public final class Java8Platform {

    private Java8Platform() {}

    public static java.nio.file.Path writeString(
            java.nio.file.Path path, CharSequence text, java.nio.file.OpenOption... options)
            throws IOException {
        return writeString(path, text, java.nio.charset.StandardCharsets.UTF_8, options);
    }

    public static java.nio.file.Path writeString(
            java.nio.file.Path path,
            CharSequence text,
            Charset charset,
            java.nio.file.OpenOption... options)
            throws IOException {
        java.nio.ByteBuffer encoded = charset.newEncoder().encode(java.nio.CharBuffer.wrap(text));
        byte[] bytes = new byte[encoded.remaining()];
        encoded.get(bytes);
        return java.nio.file.Files.write(path, bytes, options);
    }

    public static String readString(java.nio.file.Path path) throws IOException {
        return readString(path, java.nio.charset.StandardCharsets.UTF_8);
    }

    public static String readString(java.nio.file.Path path, Charset charset) throws IOException {
        // Files.readAllBytes uses an interruptible FileChannel on Java 8. Stream I/O
        // keeps a cancelled call's interrupt from corrupting the next state read.
        try (java.io.InputStream input =
                path.getFileSystem() == java.nio.file.FileSystems.getDefault()
                        ? new java.io.FileInputStream(path.toFile())
                        : java.nio.file.Files.newInputStream(path)) {
            return charset.newDecoder()
                    .decode(java.nio.ByteBuffer.wrap(readAllBytes(input)))
                    .toString();
        }
    }

    public static String strip(String value) {
        return stripTrailing(stripLeading(value));
    }

    public static String stripLeading(String value) {
        int start = 0;
        while (start < value.length()) {
            int codePoint = value.codePointAt(start);
            if (!Character.isWhitespace(codePoint)) {
                break;
            }
            start += Character.charCount(codePoint);
        }
        return value.substring(start);
    }

    public static String stripTrailing(String value) {
        int end = value.length();
        while (end > 0) {
            int codePoint = value.codePointBefore(end);
            if (!Character.isWhitespace(codePoint)) {
                break;
            }
            end -= Character.charCount(codePoint);
        }
        return value.substring(0, end);
    }

    public static String repeat(String value, int count) {
        if (count < 0) {
            throw new IllegalArgumentException("count must not be negative");
        }
        if (count == 0 || value.isEmpty()) {
            return "";
        }
        if ((long) value.length() * count > Integer.MAX_VALUE) {
            throw new OutOfMemoryError("Repeated string is too long");
        }
        StringBuilder result = new StringBuilder(value.length() * count);
        for (int i = 0; i < count; i++) {
            result.append(value);
        }
        return result.toString();
    }

    public static <T> java.util.concurrent.CompletableFuture<T> failedFuture(Throwable error) {
        java.util.concurrent.CompletableFuture<T> future =
                new java.util.concurrent.CompletableFuture<>();
        future.completeExceptionally(Objects.requireNonNull(error));
        return future;
    }

    public static <T> java.util.function.Predicate<T> not(
            java.util.function.Predicate<? super T> predicate) {
        Objects.requireNonNull(predicate);
        return value -> !predicate.test(value);
    }

    public static <T> java.util.stream.Stream<T> optionalStream(java.util.Optional<T> optional) {
        return optional.map(java.util.stream.Stream::of).orElseGet(java.util.stream.Stream::empty);
    }

    public static byte[] readAllBytes(java.io.InputStream input) throws IOException {
        java.io.ByteArrayOutputStream output = new java.io.ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int count;
        while ((count = input.read(buffer)) != -1) {
            output.write(buffer, 0, count);
        }
        return output.toByteArray();
    }

    public static boolean isBlank(String value) {
        return value.codePoints().allMatch(Character::isWhitespace);
    }

    public static <T> T requireNonNullElse(T value, T fallback) {
        return value != null ? value : Objects.requireNonNull(fallback);
    }

    public static <T> T requireNonNullElseGet(T value, Supplier<? extends T> supplier) {
        return value != null
                ? value
                : Objects.requireNonNull(Objects.requireNonNull(supplier).get());
    }

    @SuppressWarnings("unchecked")
    public static <T> java.util.Optional<T> optionalOr(
            java.util.Optional<T> value,
            java.util.function.Supplier<? extends java.util.Optional<? extends T>> fallback) {
        java.util.Objects.requireNonNull(fallback);
        if (value.isPresent()) {
            return value;
        }
        return (java.util.Optional<T>) java.util.Objects.requireNonNull(fallback.get());
    }

    public static byte[] readNBytes(java.io.InputStream input, int length)
            throws java.io.IOException {
        java.util.Objects.requireNonNull(input);
        if (length < 0) {
            throw new IllegalArgumentException("Length must not be negative");
        }
        java.io.ByteArrayOutputStream output =
                new java.io.ByteArrayOutputStream(Math.min(length, 8192));
        byte[] bytes = new byte[Math.min(length, 8192)];
        int remaining = length;
        while (remaining > 0) {
            int count = input.read(bytes, 0, Math.min(bytes.length, remaining));
            if (count < 0) {
                break;
            }
            if (count == 0) {
                int value = input.read();
                if (value < 0) {
                    break;
                }
                output.write(value);
                remaining--;
            } else {
                output.write(bytes, 0, count);
                remaining -= count;
            }
        }
        return output.toByteArray();
    }

    public static java.util.stream.Stream<String> stringLines(String value) {
        java.io.BufferedReader reader = new java.io.BufferedReader(new java.io.StringReader(value));
        return reader.lines()
                .onClose(
                        () -> {
                            try {
                                reader.close();
                            } catch (java.io.IOException error) {
                                throw new java.io.UncheckedIOException(error);
                            }
                        });
    }

    public static String hexString(byte[] bytes) {
        char[] alphabet = "0123456789abcdef".toCharArray();
        char[] hex = new char[bytes.length * 2];
        for (int i = 0; i < bytes.length; i++) {
            int value = bytes[i] & 255;
            hex[i * 2] = alphabet[value >>> 4];
            hex[i * 2 + 1] = alphabet[value & 15];
        }
        return new String(hex);
    }

    public static String urlEncode(String value, Charset charset) {
        try {
            return java.net.URLEncoder.encode(value, charset.name());
        } catch (java.io.UnsupportedEncodingException impossible) {
            throw new IllegalStateException("Charset implementation is unavailable", impossible);
        }
    }

    public static String urlDecode(String value, Charset charset) {
        try {
            return java.net.URLDecoder.decode(value, charset.name());
        } catch (java.io.UnsupportedEncodingException impossible) {
            throw new IllegalStateException("Charset implementation is unavailable", impossible);
        }
    }

    public static String outputString(java.io.ByteArrayOutputStream output, Charset charset) {
        return new String(output.toByteArray(), charset);
    }

    public static String moduleOrigin(Class<?> type) {
        try {
            Object module = Class.class.getMethod("getModule").invoke(type);
            if (Boolean.TRUE.equals(module.getClass().getMethod("isNamed").invoke(module))) {
                return "module " + module.getClass().getMethod("getName").invoke(module);
            }
        } catch (ReflectiveOperationException unavailableOnJava8) {
            // Java8 has no JPMS modules; the original unnamed-module origin is classpath.
        }
        return "classpath";
    }

    public static java.net.ProxySelector proxySelectorOf(java.net.InetSocketAddress address) {
        Objects.requireNonNull(address);
        return new java.net.ProxySelector() {
            @Override
            public java.util.List<java.net.Proxy> select(java.net.URI uri) {
                if (uri == null) throw new IllegalArgumentException("URI must not be null");
                return java.util.Collections.singletonList(
                        new java.net.Proxy(java.net.Proxy.Type.HTTP, address));
            }

            @Override
            public void connectFailed(
                    java.net.URI uri, java.net.SocketAddress address, IOException failure) {
                if (uri == null || address == null || failure == null)
                    throw new IllegalArgumentException("Proxy failure context must not be null");
            }
        };
    }

    /** @author 芋道源码 */
    private static final class FutureTimeouts {
        private static final java.util.concurrent.ScheduledThreadPoolExecutor TIMER;

        static {
            TIMER =
                    new java.util.concurrent.ScheduledThreadPoolExecutor(
                            1,
                            task -> {
                                Thread thread = new Thread(task, "langchain4j-future-timeout");
                                thread.setDaemon(true);
                                return thread;
                            });
            TIMER.setRemoveOnCancelPolicy(true);
        }
    }

    public static <T> java.util.concurrent.CompletableFuture<T> orTimeout(
            java.util.concurrent.CompletableFuture<T> future,
            long timeout,
            java.util.concurrent.TimeUnit unit) {
        Objects.requireNonNull(unit);
        if (!future.isDone()) {
            java.util.concurrent.ScheduledFuture<?> task =
                    FutureTimeouts.TIMER.schedule(
                            () ->
                                    future.completeExceptionally(
                                            new java.util.concurrent.TimeoutException()),
                            timeout,
                            unit);
            future.whenComplete((value, error) -> task.cancel(false));
        }
        return future;
    }

    public static <T> java.util.concurrent.CompletableFuture<T> exceptionallyCompose(
            java.util.concurrent.CompletableFuture<T> future,
            java.util.function.Function<Throwable, ? extends java.util.concurrent.CompletionStage<T>> recovery) {
        return future.handle((value, error) -> error == null
                ? java.util.concurrent.CompletableFuture.completedFuture(value) : recovery.apply(error))
                .thenCompose(stage -> stage);
    }

    public static java.util.concurrent.Executor delayedExecutor(long delay,
            java.util.concurrent.TimeUnit unit, java.util.concurrent.Executor executor) {
        java.util.Objects.requireNonNull(unit);
        java.util.Objects.requireNonNull(executor);
        return task -> FutureTimeouts.TIMER.schedule(() -> executor.execute(task), delay, unit);
    }

    public static <T> java.util.Optional<T> firstService(java.util.ServiceLoader<T> loader) {
        java.util.Iterator<T> iterator = loader.iterator();
        return iterator.hasNext() ? java.util.Optional.of(iterator.next()) : java.util.Optional.empty();
    }

    public static Class<?> arrayType(Class<?> component) {
        return java.lang.reflect.Array.newInstance(component, 0).getClass();
    }

    public static boolean isSealed(Class<?> type) {
        if (type.isAnnotationPresent(Java8Sealed.class)) return true;
        try { return (Boolean) Class.class.getMethod("isSealed").invoke(type); }
        catch (NoSuchMethodException e) { return false; }
        catch (ReflectiveOperationException e) { throw new IllegalStateException(e); }
    }

    public static Class<?>[] permittedSubclasses(Class<?> type) {
        Java8Sealed marker = type.getAnnotation(Java8Sealed.class);
        if (marker != null) return marker.value().clone();
        try { return (Class<?>[]) Class.class.getMethod("getPermittedSubclasses").invoke(type); }
        catch (NoSuchMethodException e) { return new Class<?>[0]; }
        catch (ReflectiveOperationException e) { throw new IllegalStateException(e); }
    }

    public static java.util.concurrent.Executor delayedExecutor(long delay, java.util.concurrent.TimeUnit unit) {
        return delayedExecutor(delay, unit, java.util.concurrent.ForkJoinPool.commonPool());
    }

    public static java.util.stream.Stream<java.util.regex.MatchResult> results(java.util.regex.Matcher matcher) {
        java.util.Iterator<java.util.regex.MatchResult> iterator = new java.util.Iterator<java.util.regex.MatchResult>() {
            private boolean ready;
            private boolean hasNext;
            public boolean hasNext() {
                if (!ready) { hasNext = matcher.find(); ready = true; }
                return hasNext;
            }
            public java.util.regex.MatchResult next() {
                if (!hasNext()) throw new java.util.NoSuchElementException();
                ready = false;
                return matcher.toMatchResult();
            }
        };
        return java.util.stream.StreamSupport.stream(java.util.Spliterators.spliteratorUnknownSize(
                iterator, java.util.Spliterator.ORDERED | java.util.Spliterator.NONNULL), false);
    }

    public static String stripIndent(String value) {
        if (value.isEmpty()) return value;
        String[] lines = value.split("\\R", -1);
        boolean optOut = value.endsWith("\n") || value.endsWith("\r");
        int indent = Integer.MAX_VALUE;
        if (!optOut) {
            for (int i = 0; i < lines.length; i++) {
                if (!isBlank(lines[i]) || i == lines.length - 1) {
                    int n = 0;
                    while (n < lines[i].length() && Character.isWhitespace(lines[i].charAt(n))) n++;
                    indent = Math.min(indent, n);
                }
            }
        } else indent = 0;
        if (indent == Integer.MAX_VALUE) indent = 0;
        for (int i = 0; i < lines.length; i++) {
            lines[i] = isBlank(lines[i]) ? "" : stripTrailing(lines[i].substring(Math.min(indent, lines[i].length())));
        }
        return String.join("\n", lines);
    }

    public static Thread newThreadWithoutInheritedLocals(Runnable task, String name) {
        try {
            return Thread.class.getConstructor(ThreadGroup.class, Runnable.class, String.class, long.class, boolean.class)
                    .newInstance(null, task, name, 0L, false);
        } catch (NoSuchMethodException java8) {
            Thread thread = new Thread(null, task, name, 0L);
            try {
                java.lang.reflect.Field field = Thread.class.getDeclaredField("inheritableThreadLocals");
                field.setAccessible(true);
                field.set(thread, null);
                return thread;
            } catch (ReflectiveOperationException e) { throw new IllegalStateException(e); }
        } catch (ReflectiveOperationException e) { throw new IllegalStateException(e); }
    }

    public static boolean isRecord(Class<?> type) {
        if (type.isAnnotationPresent(Java8Record.class)) return true;
        try { return (Boolean) Class.class.getMethod("isRecord").invoke(type); }
        catch (NoSuchMethodException e) { return false; }
        catch (ReflectiveOperationException e) { throw new IllegalStateException(e); }
    }

    public static RecordComponent[] recordComponents(Class<?> type) {
        if (!isRecord(type)) return null;
        Java8Record record = type.getAnnotation(Java8Record.class);
        java.util.List<String> names = new java.util.ArrayList<>();
        if (record != null) java.util.Collections.addAll(names, record.value());
        else {
            try {
                Object[] components = (Object[]) Class.class.getMethod("getRecordComponents").invoke(type);
                for (Object component : components) names.add((String) component.getClass().getMethod("getName").invoke(component));
            } catch (ReflectiveOperationException e) { throw new IllegalStateException(e); }
        }
        RecordComponent[] components = new RecordComponent[names.size()];
        for (int j = 0; j < components.length; j++) {
            try { components[j] = new RecordComponent(type.getDeclaredField(names.get(j))); }
            catch (NoSuchFieldException e) { throw new IllegalStateException(e); }
        }
        return components;
    }

    public static Object invokeDefault(Object proxy, java.lang.reflect.Method method, Object[] arguments) throws Throwable {
        if (!method.isDefault()) throw new IllegalArgumentException("Method must be an interface default: " + method);
        Object[] args = arguments == null ? new Object[0] : arguments;
        try {
            java.lang.reflect.Method nativeInvoke = java.lang.reflect.InvocationHandler.class.getMethod(
                    "invokeDefault", Object.class, java.lang.reflect.Method.class, Object[].class);
            try { return nativeInvoke.invoke(null, proxy, method, args); }
            catch (java.lang.reflect.InvocationTargetException e) { throw e.getCause(); }
        } catch (NoSuchMethodException java8) {
            Class<?> declaring = method.getDeclaringClass();
            java.lang.reflect.Constructor<java.lang.invoke.MethodHandles.Lookup> constructor =
                    java.lang.invoke.MethodHandles.Lookup.class.getDeclaredConstructor(Class.class, int.class);
            constructor.setAccessible(true);
            java.lang.invoke.MethodHandles.Lookup lookup = constructor.newInstance(declaring,
                    java.lang.invoke.MethodHandles.Lookup.PUBLIC | java.lang.invoke.MethodHandles.Lookup.PRIVATE
                    | java.lang.invoke.MethodHandles.Lookup.PROTECTED | java.lang.invoke.MethodHandles.Lookup.PACKAGE);
            return lookup.unreflectSpecial(method, declaring).bindTo(proxy).invokeWithArguments(args);
        }
    }

    public static java.io.PrintStream printStream(java.io.OutputStream output, boolean autoFlush, Charset charset) {
        try { return new java.io.PrintStream(output, autoFlush, charset.name()); }
        catch (java.io.UnsupportedEncodingException impossible) { throw new IllegalArgumentException(impossible); }
    }

    public static String indent(String value, int spaces) {
        if (value.isEmpty()) return value;
        final String prefix = spaces > 0 ? repeat(" ", spaces) : "";
        return stringLines(value).map(line -> {
            if (spaces >= 0) return prefix + line;
            int removed = 0;
            while (removed < line.length() && removed < -(long) spaces
                    && Character.isWhitespace(line.charAt(removed))) removed++;
            return line.substring(removed);
        }).collect(java.util.stream.Collectors.joining("\n", "", "\n"));
    }

    public static <T> T compareAndExchange(java.util.concurrent.atomic.AtomicReference<T> reference, T expected, T updated) {
        for (;;) {
            T observed = reference.get();
            if (observed != expected || reference.compareAndSet(expected, updated)) return observed;
        }
    }

    public static long pidForLogging(Process process) {
        try { return ((Number) Process.class.getMethod("pid").invoke(process)).longValue(); }
        catch (ReflectiveOperationException java8) {
            try {
                java.lang.reflect.Field field = process.getClass().getDeclaredField("pid");
                field.setAccessible(true);
                return ((Number) field.get(process)).longValue();
            } catch (ReflectiveOperationException unavailable) { return -1L; }
        }
    }

    public static java.util.concurrent.CompletableFuture<Process> onExit(Process process) {
        java.util.concurrent.CompletableFuture<Process> result = new java.util.concurrent.CompletableFuture<>();
        Thread watcher = new Thread(() -> {
            try { process.waitFor(); result.complete(process); }
            catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); result.completeExceptionally(interrupted); }
        }, "langchain4j-process-exit");
        watcher.setDaemon(true);
        result.whenComplete((value, error) -> { if (result.isCancelled()) watcher.interrupt(); });
        watcher.start();
        return result;
    }

    public static boolean isProcessAlive(long pid) {
        if (pid <= 0) return false;
        try {
            if (System.getProperty("os.name").toLowerCase(java.util.Locale.ROOT).contains("windows")) {
                Process probe = new ProcessBuilder("tasklist", "/FI", "PID eq " + pid, "/NH", "/FO", "CSV").start();
                String output;
                try (java.io.InputStream input = probe.getInputStream()) { output = new String(readAllBytes(input), java.nio.charset.StandardCharsets.UTF_8); }
                probe.waitFor();
                return output.contains("\"" + pid + "\"");
            }
            Process probe = new ProcessBuilder("kill", "-0", Long.toString(pid)).start();
            try { return probe.waitFor() == 0; }
            finally { probe.getInputStream().close(); probe.getErrorStream().close(); probe.getOutputStream().close(); }
        } catch (IOException e) { throw new IllegalStateException("Cannot inspect process " + pid, e); }
        catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); throw new IllegalStateException(interrupted); }
    }
}
