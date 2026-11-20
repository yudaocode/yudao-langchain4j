package cn.iocoder.langchain4j.compatibility;

import static dev.langchain4j.code.graalvm.process.ForkedGraalVmExecutionEngine.Language.JAVASCRIPT;
import static dev.langchain4j.code.graalvm.process.ForkedGraalVmExecutionEngine.Language.PYTHON;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.langchain4j.code.graalvm.process.ForkedGraalVmExecutionEngine;
import dev.langchain4j.code.graalvm.process.ForkedGraalVmExecutionEngine.Language;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.Arrays;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.SpringBootVersion;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Configuration;

/**
 * Exercises the installed client against real, short-lived GraalVM worker JVMs.
 *
 * @author 芋道源码
 */
@Timeout(40)
class GraalVmProcessTest {

    private static ConfigurableApplicationContext boot;

    @TempDir Path temporary;

    @TempDir static Path workerCache;

    @BeforeAll
    static void startBoot() {
        assertEquals(required("graal.boot"), SpringBootVersion.getVersion());
        assertEquals(required("graal.jdk"), System.getProperty("java.specification.version"));
        boot = new SpringApplicationBuilder(Fixture.class)
                .web(WebApplicationType.NONE)
                .properties("spring.main.banner-mode=off", "logging.level.root=warn")
                .run();
    }

    @AfterAll
    static void closeBoot() {
        if (boot != null) {
            boot.close();
        }
    }

    @Test
    void javascriptReturnsUnicodeAndStartsFreshEachTime() {
        ForkedGraalVmExecutionEngine engine = engine(JAVASCRIPT);
        assertEquals("Output:\n你好\nResult:\n42",
                engine.execute("globalThis.fixture = 42; console.log('你好'); fixture"));
        assertEquals("undefined", engine.execute("typeof fixture"));
    }

    @Test
    void pythonReturnsUnicodeAndACalculatedValue() {
        assertEquals("Output:\n芋道\nResult:\n42",
                engine(PYTHON).execute("print('芋道')\n6 * 7"));
    }

    @Test
    void propagatesGuestErrorsAndRejectsOversizedRequests() {
        IllegalStateException failure = assertThrows(IllegalStateException.class,
                () -> engine(JAVASCRIPT).execute("throw new Error('fixture failure')"));
        assertTrue(failure.getMessage().contains("fixture failure"), failure.getMessage());
        char[] source = new char[64 * 1024 + 1];
        Arrays.fill(source, ' ');
        assertThrows(IllegalArgumentException.class, () -> engine(JAVASCRIPT).execute(new String(source)));
        IllegalStateException oversize = assertThrows(IllegalStateException.class,
                () -> engine(JAVASCRIPT).execute("'a'.repeat(1048577)"));
        assertTrue(oversize.getMessage().contains("Result exceeds worker limit"), oversize.getMessage());
    }

    @Test
    void preservesHostClassAndFileRestrictions() throws Exception {
        Path sentinel = temporary.resolve("fixture-data.txt");
        Files.write(sentinel, "fixture-only".getBytes(StandardCharsets.UTF_8));
        String quoted = sentinel.toString().replace("\\", "\\\\").replace("'", "\\'");
        IllegalStateException python = assertThrows(IllegalStateException.class,
                () -> engine(PYTHON).execute("open('" + quoted + "').read()"));
        IllegalStateException javascript = assertThrows(IllegalStateException.class,
                () -> engine(JAVASCRIPT).execute("Java.type('java.lang.String')"));
        for (IllegalStateException failure : Arrays.asList(python, javascript)) {
            assertTrue(failure.getMessage().startsWith("GraalVM worker rejected execution: PolyglotException"),
                    failure.getMessage());
        }
    }

    @Test
    void terminatesTheWorkerOnTimeout() throws Exception {
        Path record = temporary.resolve("worker.pid");
        ForkedGraalVmExecutionEngine engine =
                engine(JAVASCRIPT, tracingJava(record), Duration.ofSeconds(2));
        IllegalStateException failure = assertThrows(IllegalStateException.class,
                () -> engine.execute("while (true) {}"));
        assertTrue(failure.getMessage().contains("timed out"), failure.getMessage());
        assertExited(readPid(record));
    }

    @Test
    void interruptsTheCallerAndTerminatesTheWorker() throws Exception {
        Path record = temporary.resolve("worker.pid");
        ForkedGraalVmExecutionEngine engine =
                engine(JAVASCRIPT, tracingJava(record), Duration.ofSeconds(30));
        AtomicReference<Throwable> result = new AtomicReference<>();
        AtomicBoolean interrupted = new AtomicBoolean();
        Thread owner = new Thread(() -> {
            try {
                engine.execute("while (true) {}");
            } catch (Throwable failure) {
                result.set(failure);
                interrupted.set(Thread.currentThread().isInterrupted());
            }
        });
        owner.start();
        String pid;
        try {
            pid = readPid(record);
        } finally {
            owner.interrupt();
            owner.join(TimeUnit.SECONDS.toMillis(7));
        }
        assertFalse(owner.isAlive());
        assertTrue(result.get() instanceof IllegalStateException);
        assertTrue(interrupted.get());
        assertExited(pid);
    }

    private Path tracingJava(Path record) throws Exception {
        Path launcher = temporary.resolve("java");
        String content = "#!/bin/sh\nprintf '%s\\n' \"$$\" > " + shellQuoted(record.toString())
                + "\nexec " + shellQuoted(required("graal.worker.java")) + " \"$@\"\n";
        Files.write(launcher, content.getBytes(StandardCharsets.UTF_8));
        assertTrue(launcher.toFile().setExecutable(true, true));
        return launcher;
    }

    private static String shellQuoted(String value) {
        return "'" + value.replace("'", "'\\''") + "'";
    }

    private static String readPid(Path record) throws Exception {
        long end = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (System.nanoTime() < end) {
            if (Files.isRegularFile(record)) {
                String pid = new String(Files.readAllBytes(record), StandardCharsets.UTF_8).trim();
                if (pid.matches("[0-9]+")) {
                    return pid;
                }
            }
            Thread.sleep(25);
        }
        throw new AssertionError("Missing worker PID");
    }

    private static void assertExited(String pid) throws Exception {
        long end = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (System.nanoTime() < end) {
            if (new ProcessBuilder("/bin/sh", "-c", "kill -0 \"$1\"", "sh", pid).redirectErrorStream(true).start().waitFor() != 0) {
                return;
            }
            Thread.sleep(25);
        }
        throw new AssertionError("Worker is still alive: " + pid);
    }

    private static ForkedGraalVmExecutionEngine engine(Language language) {
        return engine(language, Paths.get(required("graal.worker.java")), Duration.ofSeconds(25));
    }

    private static ForkedGraalVmExecutionEngine engine(Language language, Path java, Duration timeout) {
        return new ForkedGraalVmExecutionEngine(
                java, Paths.get(required("graal.worker.jar")), Paths.get(required("graal.worker.libs")),
                language, timeout, workerCache);
    }

    private static String required(String key) {
        String value = System.getProperty(key);
        if (value == null || value.isEmpty()) {
            throw new IllegalStateException("Missing " + key);
        }
        return value;
    }

    /** @author 芋道源码 */
    @Configuration(proxyBeanMethods = false)
    static class Fixture {}
}
