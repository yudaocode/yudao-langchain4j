package dev.langchain4j.internal.compat;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.math.BigInteger;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

/**
 * Verifies Java 9 time conversion and reflection semantics on the Java 8 runtime.
 *
 * @author 芋道源码
 */
class Java8PlatformTemporalTest {

    @Test
    void durationConversionMatchesExactArithmeticAndSaturatesAtBothLimits() {
        Duration[] durations = {
            Duration.ZERO,
            Duration.ofNanos(1),
            Duration.ofNanos(-1),
            Duration.ofMillis(-500),
            Duration.ofMillis(1500),
            Duration.ofSeconds(-9223372037L, 145224200),
            Duration.ofSeconds(9223372036L, 854775807),
            Duration.ofSeconds(9223372036L, 854775808),
            Duration.ofSeconds(Long.MAX_VALUE, 999999999),
            Duration.ofSeconds(Long.MIN_VALUE)
        };
        for (TimeUnit unit : TimeUnit.values()) {
            for (Duration duration : durations) {
                BigInteger exactNanos = BigInteger.valueOf(duration.getSeconds())
                        .multiply(BigInteger.valueOf(1_000_000_000L))
                        .add(BigInteger.valueOf(duration.getNano()));
                BigInteger expected = exactNanos.divide(BigInteger.valueOf(unit.toNanos(1)));
                expected = expected.min(BigInteger.valueOf(Long.MAX_VALUE)).max(BigInteger.valueOf(Long.MIN_VALUE));
                assertEquals(
                        expected.longValue(), Java8Platform.convertDuration(unit, duration), unit + ": " + duration);
            }
        }
    }

    @Test
    void packageNamesHandlePrimitiveAndArrayClasses() {
        assertEquals("java.lang", Java8Platform.packageName(int.class));
        assertEquals("java.lang", Java8Platform.packageName(void.class));
        assertEquals("java.lang", Java8Platform.packageName(int[][].class));
        assertEquals("java.lang", Java8Platform.packageName(String[].class));
        assertEquals("dev.langchain4j.internal.compat", Java8Platform.packageName(Java8PlatformTemporalTest.class));
    }
}
