package dev.langchain4j.store.memory.chat.cassandra;

import com.datastax.oss.driver.api.core.CqlSession;
import java.net.InetSocketAddress;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/**
 * Test Cassandra Chat Memory Store with a Saas DB.
 */
@Testcontainers
class CassandraChatMemoryStoreDockerIT extends CassandraChatMemoryStoreTestSupport {
    static final String DATACENTER = "datacenter1";
    static final DockerImageName CASSANDRA_IMAGE = DockerImageName.parse("cassandra:5.0");
    static GenericContainer<?> cassandraContainer;

    @BeforeAll
    static void ensureDockerIsRunning() {
        DockerClientFactory.instance().client();
    }

    @Override
    @SuppressWarnings("resource")
    void createDatabase() {
        cassandraContainer = new GenericContainer<>(CASSANDRA_IMAGE)
                .withEnv("CASSANDRA_CLUSTER_NAME", "langchain4j")
                .withEnv("CASSANDRA_DC", DATACENTER)
                .withEnv("MAX_HEAP_SIZE", "768M")
                .withEnv("HEAP_NEWSIZE", "128M")
                .withExposedPorts(9042)
                .waitingFor(Wait.forLogMessage(".*Starting listening for CQL clients.*\n", 1)
                        .withStartupTimeout(java.time.Duration.ofMinutes(3)));
        cassandraContainer.start();
    }

    @Override
    @SuppressWarnings("resource")
    CassandraChatMemoryStore createChatMemoryStore() {
        final InetSocketAddress contactPoint = new InetSocketAddress(cassandraContainer.getHost(), cassandraContainer.getMappedPort(9042));
        CqlSession.builder()
                .addContactPoint(contactPoint)
                .withLocalDatacenter(DATACENTER)
                .build()
                .execute("CREATE KEYSPACE IF NOT EXISTS " + KEYSPACE
                        + " WITH replication = {'class':'SimpleStrategy', 'replication_factor':'1'};");
        return new CassandraChatMemoryStore(CqlSession.builder()
                .addContactPoint(contactPoint)
                .withLocalDatacenter(DATACENTER)
                .withKeyspace(KEYSPACE)
                .build());
    }

    @AfterAll
    static void afterTests() {
        cassandraContainer.stop();
    }
}
