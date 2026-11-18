/**
 * This package contains a set of simple operator classes that can be useful when working with
 * {@link org.springframework.ai.compat.jdkhttp.Flow.Publisher} streams.
 * <p>
 * Each operator class is a {@link org.springframework.ai.compat.jdkhttp.Flow.Publisher} that accepts an upstream
 * {@link org.springframework.ai.compat.jdkhttp.Flow.Publisher} and parameters.
 * <p>
 * These operators are not designed to rival the performance of those found in a more reactive programming library
 * like Smallrye Mutiny. They cover common patterns that might be useful when exposing
 * {@link org.springframework.ai.compat.jdkhttp.Flow.Publisher} types.
 */
package mutiny.zero.operators;