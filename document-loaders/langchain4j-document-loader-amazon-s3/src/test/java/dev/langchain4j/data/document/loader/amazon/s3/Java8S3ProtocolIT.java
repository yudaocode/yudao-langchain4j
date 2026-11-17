package dev.langchain4j.data.document.loader.amazon.s3;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sun.net.httpserver.HttpServer;
import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.parser.TextDocumentParser;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;

/**
 * Uses the actual SDK against a loopback protocol fixture on the target VM.
 *
 * @author 芋道源码
 */
@Timeout(30)
class Java8S3ProtocolIT {
    @Test
    void signedSdkRequestLoadsUnicodeDocumentOnNative8() throws Exception {
        assertEquals("1.8", System.getProperty("java.specification.version"));
        AtomicReference<String> auth = new AtomicReference<>();
        AtomicReference<String> path = new AtomicReference<>();
        byte[] bytes = "预算 café 你好".getBytes(StandardCharsets.UTF_8);
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            try {
                auth.set(exchange.getRequestHeaders().getFirst("Authorization"));
                path.set(exchange.getRequestURI().getPath());
                exchange.getResponseHeaders().set("Content-Type", "text/plain; charset=UTF-8");
                exchange.sendResponseHeaders(200, bytes.length);
                exchange.getResponseBody().write(bytes);
            } finally {
                exchange.close();
            }
        });
        server.start();
        try (S3Client sdk = S3Client.builder()
                .region(Region.US_EAST_1)
                .endpointOverride(
                        URI.create("http://127.0.0.1:" + server.getAddress().getPort()))
                .credentialsProvider(
                        StaticCredentialsProvider.create(AwsBasicCredentials.create("native8-test", "dummy-secret")))
                .serviceConfiguration(
                        S3Configuration.builder().pathStyleAccessEnabled(true).build())
                .build()) {
            Document doc =
                    new AmazonS3DocumentLoader(sdk).loadDocument("test-bucket", "doc.txt", new TextDocumentParser());
            assertEquals("预算 café 你好", doc.text());
            assertEquals("s3://test-bucket/doc.txt", doc.metadata().getString("source"));
            assertEquals("/test-bucket/doc.txt", path.get());
            assertTrue(auth.get().startsWith("AWS4-HMAC-SHA256 Credential=native8-test/"));
        } finally {
            server.stop(0);
        }
    }
}
