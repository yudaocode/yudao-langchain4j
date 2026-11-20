package dev.langchain4j.code.graalvm.process;

import dev.langchain4j.code.CodeExecutionEngine;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Runs each invocation in a separate JVM with the modern GraalVM worker and its dependencies.
 *
 * @author 芋道源码
 */
public final class ForkedGraalVmExecutionEngine implements CodeExecutionEngine {

    /**
     * Supported guest languages.
     *
     * @author 芋道源码
     */
    public enum Language {
        JAVASCRIPT,
        PYTHON
    }

    private static final String WORKER_CLASS = "dev.langchain4j.code.graalvm.ForkedGraalVmWorker";
    private static final int PROTOCOL = 0x47524131;
    private static final int MAX_SOURCE_BYTES = 64 * 1024;
    private static final int MAX_REPLY_BYTES = 1024 * 1024;

    private final Path javaExecutable;
    private final String workerClasspath;
    private final Language language;
    private final Path workerCache;
    private final long timeoutMillis;

    public ForkedGraalVmExecutionEngine(
            Path javaExecutable, Path workerJar, Path workerLibraries, Language language, Duration timeout) {
        this(javaExecutable, workerJar, workerLibraries, language, timeout,
                Paths.get(System.getProperty("user.home"), ".cache", "langchain4j-graalvm"));
    }

    public ForkedGraalVmExecutionEngine(
            Path javaExecutable, Path workerJar, Path workerLibraries, Language language, Duration timeout,
            Path workerCache) {
        this.workerCache = Objects.requireNonNull(workerCache, "workerCache").toAbsolutePath();
        this.javaExecutable = Objects.requireNonNull(javaExecutable, "javaExecutable").toAbsolutePath();
        this.workerClasspath = Objects.requireNonNull(workerJar, "workerJar").toAbsolutePath()
                + File.pathSeparator
                + Objects.requireNonNull(workerLibraries, "workerLibraries").toAbsolutePath() + File.separator + "*";
        this.language = Objects.requireNonNull(language, "language");
        this.timeoutMillis = Objects.requireNonNull(timeout, "timeout").toMillis();
        if (timeoutMillis <= 0) {
            throw new IllegalArgumentException("timeout must be at least one millisecond");
        }
    }

    @Override
    public String execute(String code) {
        Objects.requireNonNull(code, "code");
        if (code.length() > MAX_SOURCE_BYTES) {
            throw new IllegalArgumentException("Source exceeds worker limit");
        }
        byte[] source = code.getBytes(StandardCharsets.UTF_8);
        if (source.length > MAX_SOURCE_BYTES) {
            throw new IllegalArgumentException("Source exceeds worker limit");
        }
        if (Thread.currentThread().isInterrupted()) {
            throw new IllegalStateException("GraalVM execution interrupted");
        }

        Process process = startWorker();
        FutureTask<String> reply = new FutureTask<>(() -> exchange(process, source));
        Thread transfer = new Thread(reply, "langchain4j-graalvm-process-io");
        transfer.setDaemon(true);
        try {
            transfer.start();
            return reply.get(timeoutMillis, TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            throw new IllegalStateException("GraalVM execution timed out", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("GraalVM execution interrupted", e);
        } catch (ExecutionException e) {
            Throwable failure = e.getCause();
            if (failure instanceof IllegalStateException) {
                throw (IllegalStateException) failure;
            }
            throw new IllegalStateException("GraalVM worker did not return a valid reply", failure);
        } finally {
            if (process.isAlive()) {
                process.destroyForcibly();
            }
            reply.cancel(true);
        }
    }

    private Process startWorker() {
        File stderr = new File(File.separatorChar == '\\' ? "NUL" : "/dev/null");
        ProcessBuilder builder = new ProcessBuilder(
                javaExecutable.toString(), "-Xmx512m", "-Dfile.encoding=UTF-8",
                "-Dpolyglot.engine.userResourceCache=" + workerCache,
                "-cp", workerClasspath, WORKER_CLASS, language.name());
        builder.environment().clear();
        builder.environment().put("LC_ALL", "C.UTF-8");
        builder.redirectError(ProcessBuilder.Redirect.to(stderr));
        try {
            return builder.start();
        } catch (IOException e) {
            throw new IllegalStateException("Could not start the GraalVM worker", e);
        }
    }

    private static String exchange(Process process, byte[] source) throws IOException {
        try (DataOutputStream request = new DataOutputStream(process.getOutputStream())) {
            request.writeInt(PROTOCOL);
            request.writeInt(source.length);
            request.write(source);
        }
        try (DataInputStream reply = new DataInputStream(process.getInputStream())) {
            if (reply.readInt() != PROTOCOL) {
                throw new IOException("Unsupported GraalVM worker protocol");
            }
            boolean success = reply.readBoolean();
            int size = reply.readInt();
            if (size < 0 || size > MAX_REPLY_BYTES) {
                throw new IOException("Invalid GraalVM reply size");
            }
            byte[] bytes = new byte[size];
            reply.readFully(bytes);
            String value = new String(bytes, StandardCharsets.UTF_8);
            if (!success) {
                throw new IllegalStateException("GraalVM worker rejected execution: " + value);
            }
            return value;
        }
    }
}
