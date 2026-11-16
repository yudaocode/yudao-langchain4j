package dev.langchain4j.mcp.client.transport.websocket.compat;

import java.io.IOException;
import java.net.URI;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.net.ssl.SSLContext;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.drafts.Draft_6455;
import org.java_websocket.handshake.ServerHandshake;

/**
 * Narrow Java 8 WebSocket API used by MCP. Java-WebSocket limits both individual
 * frames and accumulated fragmented messages before allocating an unbounded body.
 *
 * @author 芋道源码
 */
public final class WebSocket {
    public static final int NORMAL_CLOSURE = 1000;
    private static final java.util.concurrent.ScheduledThreadPoolExecutor OPENING_TIMEOUTS = openingTimeouts();

    private static java.util.concurrent.ScheduledThreadPoolExecutor openingTimeouts() {
        java.util.concurrent.ScheduledThreadPoolExecutor timer = new java.util.concurrent.ScheduledThreadPoolExecutor(1, task -> {
            Thread thread = new Thread(task, "langchain4j-websocket-opening-timeout");
            thread.setDaemon(true);
            return thread;
        });
        timer.setRemoveOnCancelPolicy(true);
        return timer;
    }
    private final Client owner;
    private final Listener listener;
    private final CompletableFuture<WebSocket> opened = new CompletableFuture<>();
    private final Object demandLock = new Object();
    private final CompletableFuture<Void> stopped = new CompletableFuture<>();
    private final AtomicBoolean terminal = new AtomicBoolean();
    private long demand;
    private final WebSocketClient delegate;

    /** @author 芋道源码 */
    public interface Listener {
        default void onOpen(WebSocket socket) { socket.request(1); }
        default CompletionStage<?> onText(WebSocket socket, CharSequence data, boolean last) { socket.request(1); return null; }
        default CompletionStage<?> onBinary(WebSocket socket, ByteBuffer data, boolean last) { socket.request(1); return null; }
        default CompletionStage<?> onClose(WebSocket socket, int status, String reason) { return null; }
        default void onError(WebSocket socket, Throwable error) {}
    }

    /** @author 芋道源码 */
    public static final class Client implements AutoCloseable {
        private final Duration timeout;
        private final SSLContext sslContext;
        private final Executor executor;
        private final int maxMessageSize;
        private final AtomicBoolean closed = new AtomicBoolean();
        private final Set<WebSocket> sockets = ConcurrentHashMap.newKeySet();

        public Client(Duration timeout, SSLContext sslContext, Executor executor, int maxMessageSize) {
            if (timeout.isNegative() || timeout.isZero()) throw new IllegalArgumentException("Timeout must be positive");
            if (maxMessageSize <= 0) throw new IllegalArgumentException("Message size must be positive");
            this.timeout = timeout;
            this.sslContext = sslContext;
            this.executor = executor;
            this.maxMessageSize = maxMessageSize;
        }
        public Builder newWebSocketBuilder() {
            if (closed.get()) throw new IllegalStateException("WebSocket client is closed");
            return new Builder(this);
        }
        @Override public void close() {
            closed.set(true);
            for (WebSocket socket : sockets) socket.abort();
            sockets.clear();
        }
    }

    /** @author 芋道源码 */
    public static final class Builder {
        private final Client owner;
        private Duration timeout;
        private final Map<String, String> headers = new LinkedHashMap<>();
        private Builder(Client owner) { this.owner = owner; this.timeout = owner.timeout; }
        public Builder header(String name, String value) {
            if (name.isEmpty() || name.indexOf('\r') >= 0 || name.indexOf('\n') >= 0
                    || value.indexOf('\r') >= 0 || value.indexOf('\n') >= 0) throw new IllegalArgumentException("Invalid header");
            headers.merge(name, value, (first, second) -> first + ", " + second);
            return this;
        }
        public Builder connectTimeout(Duration timeout) {
            if (timeout.isNegative() || timeout.isZero()) throw new IllegalArgumentException("Timeout must be positive");
            this.timeout = timeout;
            return this;
        }
        public CompletableFuture<WebSocket> buildAsync(URI uri, Listener listener) {
            WebSocket socket = new WebSocket(owner, uri, headers, timeout, listener);
            owner.sockets.add(socket);
            java.util.concurrent.ScheduledFuture<?> deadline = OPENING_TIMEOUTS.schedule(() -> {
                if (socket.opened.completeExceptionally(new org.springframework.ai.compat.jdkhttp.HttpTimeoutException(
                        "WebSocket opening handshake timed out"))) socket.abort();
            }, Math.max(1L, timeout.toMillis()), java.util.concurrent.TimeUnit.MILLISECONDS);
            socket.opened.whenComplete((value, error) -> deadline.cancel(false));
            if (owner.closed.get()) socket.abort();
            else socket.delegate.connect();
            socket.opened.whenComplete((value, error) -> { if (socket.opened.isCancelled()) socket.abort(); });
            return socket.opened;
        }
    }

    private WebSocket(Client owner, URI uri, Map<String, String> headers, Duration timeout, Listener listener) {
        this.owner = owner;
        this.listener = java.util.Objects.requireNonNull(listener);
        int millis = (int) Math.min(Integer.MAX_VALUE, Math.max(1L, timeout.toMillis()));
        this.delegate = new WebSocketClient(uri, new Draft_6455(Collections.emptyList(), owner.maxMessageSize),
                new LinkedHashMap<>(headers), millis) {
            @Override public void onOpen(ServerHandshake handshake) {
                if (terminal.get() || owner.closed.get()) { abort(); return; }
                dispatch(() -> {
                    try { listener.onOpen(WebSocket.this); opened.complete(WebSocket.this); }
                    catch (Throwable failure) { opened.completeExceptionally(failure); abort(); listener.onError(WebSocket.this, failure); }
                });
            }
            @Override public void onMessage(String message) { receive(() -> listener.onText(WebSocket.this, message, true)); }
            @Override public void onMessage(ByteBuffer message) { receive(() -> listener.onBinary(WebSocket.this, message, true)); }
            @Override public void onClose(int code, String reason, boolean remote) {
                terminate();
                opened.completeExceptionally(new IOException("WebSocket closed: " + code + " " + reason));
                dispatch(() -> listener.onClose(WebSocket.this, code, reason));
            }
            @Override public void onError(Exception error) {
                opened.completeExceptionally(error);
                abort();
                dispatch(() -> listener.onError(WebSocket.this, error));
            }
        };
        delegate.setDaemon(true);
        try {
            java.net.Socket socket = "wss".equalsIgnoreCase(uri.getScheme())
                    ? (owner.sslContext == null ? SSLContext.getDefault() : owner.sslContext).getSocketFactory().createSocket()
                    : new java.net.Socket();
            delegate.setSocket(socket); // even cancellation before connect can close this socket
        } catch (IOException | java.security.NoSuchAlgorithmException failure) { throw new IllegalStateException(failure); }
    }

    /**
     * Enqueues a complete MCP JSON message. Completion confirms queue acceptance, not a socket flush.
     * Transport failures are reported through the listener; this is a narrow MCP bridge, not the full JDK API.
     */
    public CompletableFuture<WebSocket> sendText(CharSequence text, boolean last) {
        CompletableFuture<WebSocket> sent = new CompletableFuture<>();
        try {
            if (!last) throw new IllegalArgumentException("The MCP bridge sends complete JSON messages");
            delegate.send(text.toString());
            sent.complete(this);
        } catch (RuntimeException failure) { sent.completeExceptionally(failure); }
        return sent;
    }

    public CompletableFuture<WebSocket> sendClose(int status, String reason) {
        CompletableFuture<WebSocket> sent = new CompletableFuture<>();
        try { delegate.close(status, reason); sent.complete(this); }
        catch (RuntimeException failure) { sent.completeExceptionally(failure); }
        return sent;
    }

    public void request(long count) {
        if (count <= 0) throw new IllegalArgumentException("Demand must be positive");
        synchronized (demandLock) {
            demand = Long.MAX_VALUE - demand < count ? Long.MAX_VALUE : demand + count;
            demandLock.notifyAll();
        }
    }

    public void abort() {
        terminate();
        opened.completeExceptionally(new IOException("WebSocket aborted"));
        try {
            if (delegate.getSocket() != null) delegate.getSocket().close();
        } catch (IOException ignored) { /* closeConnection still releases the protocol state */ }
        delegate.closeConnection(1006, "Aborted");
    }

    private void terminate() {
        terminal.set(true);
        stopped.complete(null);
        owner.sockets.remove(this);
        synchronized (demandLock) { demandLock.notifyAll(); }
    }

    private void receive(java.util.function.Supplier<CompletionStage<?>> callback) {
        try {
            synchronized (demandLock) {
                while (demand == 0 && !terminal.get()) demandLock.wait();
                if (terminal.get()) return;
                if (demand != Long.MAX_VALUE) demand--;
            }
            CompletableFuture<Void> delivered = new CompletableFuture<>();
            dispatch(() -> {
                try {
                    if (terminal.get()) { delivered.complete(null); return; }
                    CompletionStage<?> stage = callback.get();
                    if (stage == null) delivered.complete(null);
                    else stage.whenComplete((value, error) -> {
                        if (error == null) delivered.complete(null); else delivered.completeExceptionally(error);
                    });
                } catch (Throwable error) { delivered.completeExceptionally(error); }
            });
            CompletableFuture.anyOf(delivered, stopped).join();
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            abort();
        } catch (RuntimeException failure) {
            abort();
            dispatch(() -> listener.onError(this, failure));
        }
    }

    private void dispatch(Runnable task) {
        if (owner.executor == null) task.run();
        else owner.executor.execute(task);
    }
}
