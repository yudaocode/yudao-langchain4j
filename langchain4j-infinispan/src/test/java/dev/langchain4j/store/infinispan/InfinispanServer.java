package dev.langchain4j.store.infinispan;

import java.time.Duration;
import org.infinispan.client.hotrod.configuration.ClientIntelligence;
import org.infinispan.client.hotrod.configuration.ConfigurationBuilder;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;

/**
 * Runs the production server line against the Java 8 Hot Rod client.
 *
 * @author 芋道源码
 */
public final class InfinispanServer extends GenericContainer<InfinispanServer> {
    private static final String USERNAME = "compatibility";
    private static final String PASSWORD = "fixture-password";

    public InfinispanServer() {
        super(DockerImageName.parse(System.getProperty("infinispan.image", "quay.io/infinispan/server:16.3.1")));
        withEnv("USER", USERNAME);
        withEnv("PASS", PASSWORD);
        withExposedPorts(11222);
        waitingFor(Wait.forHttp("/rest/v2/cache-managers/default/health/status")
                .forPort(11222)
                .withBasicCredentials(USERNAME, PASSWORD)
                .forResponsePredicate(body -> "HEALTHY".equals(body.trim()))
                .withStartupTimeout(Duration.ofMinutes(2)));
    }

    public void configure(ConfigurationBuilder builder) {
        builder.clientIntelligence(ClientIntelligence.BASIC);
        builder.addServer().host(getHost()).port(getMappedPort(11222));
        builder.security().authentication()
                .username(USERNAME)
                .password(PASSWORD)
                .saslMechanism("SCRAM-SHA-512");
    }
}
