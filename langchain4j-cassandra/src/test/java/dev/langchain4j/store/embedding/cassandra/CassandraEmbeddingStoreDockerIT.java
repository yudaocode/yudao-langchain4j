package dev.langchain4j.store.embedding.cassandra;

import com.datastax.oss.driver.api.core.CqlSession;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingStore;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.net.InetSocketAddress;
import java.util.Collections;

import static com.dtsx.astra.sdk.cassio.CassandraSimilarityMetric.COSINE;

/**
 * Work with Cassandra Embedding Store.
 */
@Testcontainers
class CassandraEmbeddingStoreDockerIT extends CassandraEmbeddingStoreIT {

    static final String CASSANDRA_IMAGE = "cassandra:5.0";
    static final String DATACENTER = "datacenter1";
    static final String CLUSTER = "langchain4j";
    static GenericContainer<?> cassandraContainer;

    /**
     * Check Docker is installed and running on host
     */
    @BeforeAll
    static void ensureDockerIsRunning() {
        DockerClientFactory.instance().client();
        if (cassandraContainer == null) {
            cassandraContainer = new GenericContainer<>(
                    DockerImageName.parse(CASSANDRA_IMAGE))
                    .withEnv("CASSANDRA_CLUSTER_NAME", CLUSTER)
                    .withEnv("CASSANDRA_DC", DATACENTER)
                    .withEnv("MAX_HEAP_SIZE", "768M")
                    .withEnv("HEAP_NEWSIZE", "128M")
                    .withExposedPorts(9042)
                    .waitingFor(Wait.forLogMessage(".*Starting listening for CQL clients.*\n", 1)
                            .withStartupTimeout(java.time.Duration.ofMinutes(3)));
            cassandraContainer.start();

            // Part of Database Creation, creating keyspace
            final InetSocketAddress contactPoint = new InetSocketAddress(cassandraContainer.getHost(), cassandraContainer.getMappedPort(9042));
            CqlSession.builder()
                    .addContactPoint(contactPoint)
                    .withLocalDatacenter(DATACENTER)
                    .build().execute(
                            "CREATE KEYSPACE IF NOT EXISTS " + KEYSPACE +
                                    " WITH replication = {'class':'SimpleStrategy', 'replication_factor':'1'};");
        }
    }

    /**
     * Stop Cassandra Node
     */
    @AfterAll
    static void afterTests() {
        cassandraContainer.stop();
    }

    @Override
    protected EmbeddingStore<TextSegment> embeddingStore() {
        final InetSocketAddress contactPoint = new InetSocketAddress(cassandraContainer.getHost(), cassandraContainer.getMappedPort(9042));
        if (embeddingStore == null) {
            embeddingStore = CassandraEmbeddingStore.builder()
                    .contactPoints(Collections.singletonList(contactPoint.getHostName()))
                    .port(contactPoint.getPort())
                    .localDataCenter(DATACENTER)
                    .keyspace(KEYSPACE)
                    .table(TEST_INDEX)
                    .dimension(embeddingModel().dimension())
                    .metric(COSINE)
                    .build();
        }
        return embeddingStore;
    }
}
