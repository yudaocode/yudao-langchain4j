package dev.langchain4j.data.document.loader.tencent.cos;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.qcloud.cos.COSClient;
import com.qcloud.cos.ClientConfig;
import com.qcloud.cos.auth.BasicCOSCredentials;
import com.qcloud.cos.endpoint.EndpointBuilder;
import com.qcloud.cos.http.HttpProtocol;
import com.qcloud.cos.region.Region;
import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.parser.TextDocumentParser;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import okio.Buffer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/**
 * Uses the actual SDK against a loopback protocol fixture on the target VM.
 *
 * @author 芋道源码
 */
@Timeout(30)
class Java8CosProtocolIT {

    @Test
    void signedSdkRequestLoadsUnicodeDocumentOnNative8() throws Exception {
        assertEquals("1.8", System.getProperty("java.specification.version"));
        byte[] bytes = "预算 café 你好".getBytes(StandardCharsets.UTF_8);
        try (MockWebServer server = new MockWebServer()) {
            // COS 5.6.283 stores metadata headers in a case-sensitive map. Use the
            // canonical Content-Length emitted by COS, rather than HttpServer's
            // normalized Content-length, and keep the SDK's length validation.
            server.enqueue(new MockResponse()
                    .setHeader("Content-Type", "text/plain; charset=UTF-8")
                    .setHeader("Last-Modified", "Tue, 17 Nov 2026 00:00:00 GMT")
                    .setBody(new Buffer().write(bytes)));
            server.start(InetAddress.getByName("127.0.0.1"), 0);
            String endpoint = "127.0.0.1:" + server.getPort();
            ClientConfig config = new ClientConfig(new Region("ap-shanghai"));
            config.setHttpProtocol(HttpProtocol.http);
            config.setEndpointBuilder(new EndpointBuilder() {
                @Override
                public String buildGeneralApiEndpoint(String bucket) {
                    return endpoint;
                }

                @Override
                public String buildGetServiceApiEndpoint() {
                    return endpoint;
                }
            });
            COSClient sdk = new COSClient(new BasicCOSCredentials("native8-test", "dummy-secret"), config);
            try {
                Document doc = new TencentCosDocumentLoader(sdk)
                        .loadDocument("test-bucket-1250000000", "doc.txt", new TextDocumentParser());
                assertEquals("预算 café 你好", doc.text());
                assertEquals(
                        "cos://test-bucket-1250000000/doc.txt", doc.metadata().getString("source"));
                RecordedRequest request = server.takeRequest(5, TimeUnit.SECONDS);
                assertEquals("GET", request.getMethod());
                assertEquals("/doc.txt", request.getPath());
                assertTrue(request.getHeader("Authorization").contains("q-sign-algorithm=sha1"));
            } finally {
                sdk.shutdown();
            }
        }
    }
}
