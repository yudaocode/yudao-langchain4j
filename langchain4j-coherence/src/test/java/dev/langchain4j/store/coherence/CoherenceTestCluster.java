package dev.langchain4j.store.coherence;

import com.tangosol.net.CacheFactory;
import com.tangosol.net.Session;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

/**
 * Provides a real loopback Coherence member with the Java 8 cache API.
 *
 * @author 芋道源码
 */
public final class CoherenceTestCluster implements BeforeAllCallback, AfterAllCallback {

    private final Map<String, String> previousProperties = new HashMap<>();
    private Session session;

    @Override
    public void beforeAll(ExtensionContext context) throws Exception {
        int port;
        try (DatagramSocket socket = new DatagramSocket(0, InetAddress.getByName("127.0.0.1"))) {
            port = socket.getLocalPort();
        }
        set("coherence.cluster", context.getRequiredTestClass().getSimpleName() + "-" + port);
        set("coherence.clusterport", Integer.toString(port));
        set("coherence.wka", "127.0.0.1");
        set("coherence.localhost", "127.0.0.1");
        set("coherence.serializer", "pof");
        set("coherence.pof.config", "langchain4j-coherence-java8-pof-config.xml");
        set("coherence.distributed.localstorage", "true");
        session = Session.create();
    }

    public Session session() {
        return session;
    }

    @Override
    public void afterAll(ExtensionContext context) throws Exception {
        try {
            if (session != null) {
                session.close();
            }
        } finally {
            try {
                CacheFactory.shutdown();
            } finally {
                previousProperties.forEach((key, value) -> {
                    if (value == null) {
                        System.clearProperty(key);
                    } else {
                        System.setProperty(key, value);
                    }
                });
                previousProperties.clear();
            }
        }
    }

    private void set(String name, String value) {
        previousProperties.put(name, System.getProperty(name));
        System.setProperty(name, value);
    }
}
