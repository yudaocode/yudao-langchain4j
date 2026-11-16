package dev.langchain4j.mcp.client.transport.websocket.compat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URI;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.ai.compat.jdkhttp.HttpTimeoutException;

/** @author 芋道源码 */
class Java8WebSocketTimeoutTest {
    @Test
    @Timeout(10)
    void openingTimeoutClosesTheSocketWhenTheServerNeverFinishesTheHandshake() throws Exception {
        try (ServerSocket server = new ServerSocket(0);
                WebSocket.Client client = new WebSocket.Client(Duration.ofMillis(500), null, null, 1024)) {
            server.setSoTimeout(3000);
            CompletableFuture<WebSocket> connection = client.newWebSocketBuilder().buildAsync(
                    URI.create("ws://127.0.0.1:" + server.getLocalPort() + "/mcp"), new WebSocket.Listener() {});
            try (Socket peer = server.accept()) {
                peer.setSoTimeout(3000);
                assertThatThrownBy(() -> connection.get(3, TimeUnit.SECONDS)).hasCauseInstanceOf(HttpTimeoutException.class);
                int received = 0;
                while (peer.getInputStream().read() != -1) received++;
                assertThat(received).as("HTTP upgrade request is written before the opening timeout closes the socket").isPositive();
            }
        }
    }
}
