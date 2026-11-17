package cn.iocoder.langchain4j.compatibility;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.common.hash.Hashing;
import com.google.common.primitives.UnsignedBytes;
import com.google.common.util.concurrent.ClosingFuture;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.common.util.concurrent.SettableFuture;
import io.grpc.netty.shaded.io.netty.buffer.ByteBuf;
import io.grpc.netty.shaded.io.netty.buffer.Unpooled;
import java.util.Collections;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.event.Level;

/**
 * Exercises guarded SDK paths under a real Boot 2 consumer classpath on Java 8.
 *
 * @author 芋道源码
 */
class Native8DependencyFallbackTest {

    @Test
    void boot2ContextUsesDocumentedRuntimeVersions() {
        new org.springframework.boot.test.context.runner.ApplicationContextRunner().run(context -> {
            assertTrue(context.isRunning());
            assertEquals("2.7.18", org.springframework.boot.SpringBootVersion.getVersion());
            assertTrue(org.springframework.core.SpringVersion.getVersion().startsWith("5.3."));
            assertEquals("4.13.0", new org.openqa.selenium.BuildInfo().getReleaseLabel());
            assertEquals(
                    "4.1.140.Final",
                    io.netty.util.Version.identify().get("netty-common").artifactVersion());
            assertEquals(
                    "2.21.4",
                    new com.fasterxml.jackson.databind.ObjectMapper().version().toString());
            assertEquals(
                    "3.7.19", reactor.core.publisher.Flux.class.getPackage().getImplementationVersion());
        });
    }

    @Test
    void guavaAndShadedNettyUseNative8Fallbacks() throws Exception {
        assertEquals("1.8", System.getProperty("java.specification.version"));
        assertEquals(
                0xe3069283,
                Hashing.crc32c().hashBytes("123456789".getBytes("US-ASCII")).asInt());
        assertTrue(UnsignedBytes.lexicographicalComparator().compare(new byte[] {0}, new byte[] {(byte) 255}) < 0);
        SettableFuture<String> input = SettableFuture.create();
        ClosingFuture<String> closing = ClosingFuture.from(input);
        java.util.concurrent.Future<String> output = closing.transform(
                        (closer, value) -> value + "-closed", MoreExecutors.directExecutor())
                .finishToFuture();
        input.set("native8");
        assertEquals("native8-closed", output.get(5, TimeUnit.SECONDS));
        ByteBuf buffer = Unpooled.directBuffer(8);
        try {
            buffer.writeLong(123456789L);
            assertEquals(123456789L, buffer.readLong());
            buffer.retain();
            assertEquals(2, buffer.refCnt());
            buffer.release();
        } finally {
            assertTrue(buffer.release());
        }
    }

    @Test
    void googleLoggingAdaptersUseSlf4j17MdcWithoutLoadingSlf4j2() throws Exception {
        assertEquals("1.8", System.getProperty("java.specification.version"));
        Logger logger = LoggerFactory.getLogger(Native8DependencyFallbackTest.class);
        for (String name :
                new String[] {"com.google.api.gax.logging.Slf4jUtils", "com.google.auth.oauth2.Slf4jUtils"}) {
            Class<?> type = Class.forName(name);
            java.lang.reflect.Field flag = type.getDeclaredField("isSLF4J2x");
            flag.setAccessible(true);
            assertEquals(false, flag.get(null));
            java.lang.reflect.Method log =
                    type.getDeclaredMethod("log", Logger.class, Level.class, java.util.Map.class, String.class);
            log.setAccessible(true);
            log.invoke(
                    null,
                    logger,
                    Level.INFO,
                    Collections.singletonMap("fixture", "native8"),
                    "native8 logging fallback");
        }
    }
}
