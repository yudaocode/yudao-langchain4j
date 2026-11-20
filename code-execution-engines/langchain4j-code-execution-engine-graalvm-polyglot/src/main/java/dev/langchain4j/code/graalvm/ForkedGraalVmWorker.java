package dev.langchain4j.code.graalvm;

import dev.langchain4j.code.CodeExecutionEngine;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

/**
 * Entry point for a one-request GraalVM worker.
 *
 * @author 芋道源码
 */
public final class ForkedGraalVmWorker {

    private static final int PROTOCOL = 0x47524131;
    private static final int MAX_SOURCE_BYTES = 64 * 1024;
    private static final int MAX_REPLY_BYTES = 1024 * 1024;

    private ForkedGraalVmWorker() {}

    public static void main(String[] args) throws IOException {
        if (args.length != 1) {
            throw new IllegalArgumentException("Expected a single worker language");
        }

        try (DataOutputStream reply = new DataOutputStream(System.out);
                DataInputStream request = new DataInputStream(System.in)) {
            System.setOut(new PrintStream(OutputStream.nullOutputStream()));
            if (request.readInt() != PROTOCOL) {
                throw new IOException("Unsupported GraalVM worker protocol");
            }
            int length = request.readInt();
            if (length < 0 || length > MAX_SOURCE_BYTES) {
                send(reply, false, "Source exceeds worker limit");
                return;
            }
            byte[] source = new byte[length];
            request.readFully(source);
            try {
                CodeExecutionEngine engine = switch (args[0]) {
                    case "JAVASCRIPT" -> new GraalVmJavaScriptExecutionEngine();
                    case "PYTHON" -> new GraalVmPythonExecutionEngine();
                    default -> throw new IllegalArgumentException("Unsupported worker language");
                };
                send(reply, true, engine.execute(new String(source, StandardCharsets.UTF_8)));
            } catch (RuntimeException failure) {
                send(reply, false, failure.getClass().getSimpleName() + ": " + failure.getMessage());
            }
        }
    }

    private static void send(DataOutputStream reply, boolean success, String text) throws IOException {
        byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
        if (bytes.length > MAX_REPLY_BYTES) {
            success = false;
            bytes = "Result exceeds worker limit".getBytes(StandardCharsets.UTF_8);
        }
        reply.writeInt(PROTOCOL);
        reply.writeBoolean(success);
        reply.writeInt(bytes.length);
        reply.write(bytes);
        reply.flush();
    }
}
