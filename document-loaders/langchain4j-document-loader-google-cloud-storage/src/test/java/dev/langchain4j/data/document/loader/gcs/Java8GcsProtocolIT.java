package dev.langchain4j.data.document.loader.gcs;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.google.cloud.NoCredentials;
import com.google.cloud.storage.Blob;
import com.google.cloud.storage.Storage;
import com.google.cloud.storage.StorageOptions;
import com.google.common.hash.Hashing;
import com.google.common.io.FileBackedOutputStream;
import com.sun.net.httpserver.HttpServer;
import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.DocumentLoader;
import dev.langchain4j.data.document.parser.TextDocumentParser;
import dev.langchain4j.data.document.source.gcs.GcsSource;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/**
 * Uses the actual SDK against a loopback protocol fixture on the target VM.
 *
 * @author 芋道源码
 */
@Timeout(30)
class Java8GcsProtocolIT {
    @Test
    void actualSdkAndSourcePipelineLoadUnicodeWithoutCloudCredentials() throws Exception {
        assertEquals("1.8", System.getProperty("java.specification.version"));
        GoogleCloudStorageDocumentLoader.builder()
                .project("native8-fixture")
                .credentials(NoCredentials.getInstance())
                .build();
        byte[] bytes = "预算 café 你好".getBytes(StandardCharsets.UTF_8);
        String crc = Base64.getEncoder()
                .encodeToString(ByteBuffer.allocate(4)
                        .putInt(Hashing.crc32c().hashBytes(bytes).asInt())
                        .array());
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            try {
                boolean media =
                        String.valueOf(exchange.getRequestURI().getQuery()).contains("alt=media");
                String metadata = "{\"name\":\"doc.txt\",\"bucket\":\"test-bucket\",\"size\":\"" + bytes.length
                        + "\",\"contentType\":\"text/plain\",\"generation\":\"1\",\"crc32c\":\"" + crc
                        + "\",\"timeCreated\":\"2026-11-17T00:00:00.000Z\",\"updated\":\"2026-11-17T00:00:00.000Z\"}";
                byte[] payload = media ? bytes : metadata.getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders()
                        .set("Content-Type", media ? "text/plain; charset=UTF-8" : "application/json; charset=UTF-8");
                exchange.sendResponseHeaders(200, payload.length);
                exchange.getResponseBody().write(payload);
            } finally {
                exchange.close();
            }
        });
        server.start();
        try (Storage storage = StorageOptions.newBuilder()
                .setProjectId("native8-fixture")
                .setCredentials(NoCredentials.getInstance())
                .setHost("http://127.0.0.1:" + server.getAddress().getPort())
                .build()
                .getService()) {
            Blob blob = storage.get("test-bucket", "doc.txt");
            Document document = DocumentLoader.load(new GcsSource(blob), new TextDocumentParser());
            assertEquals("预算 café 你好", document.text());
            assertEquals("gs://test-bucket/doc.txt", document.metadata().getString("source"));
        } finally {
            server.stop(0);
        }
        FileBackedOutputStream spill = new FileBackedOutputStream(1);
        try {
            spill.write(bytes);
            assertEquals("预算 café 你好", new String(spill.asByteSource().read(), StandardCharsets.UTF_8));
        } finally {
            spill.close();
            spill.reset();
        }
    }
}
