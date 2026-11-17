package dev.langchain4j.store.embedding.opensearch;

import java.time.Duration;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;

/**
 * Runs the upstream store contracts on a local OpenSearch engine without the Java11-only helper.
 *
 * @author 芋道源码
 */
final class Java8OpenSearchContainer extends GenericContainer<Java8OpenSearchContainer> {

    Java8OpenSearchContainer() {
        super(DockerImageName.parse(System.getProperty("opensearch.test.image", "opensearchproject/opensearch:2.19.3")));
        withExposedPorts(9200);
        withEnv("discovery.type", "single-node");
        withEnv("DISABLE_SECURITY_PLUGIN", "true");
        withEnv("DISABLE_INSTALL_DEMO_CONFIG", "true");
        withEnv("OPENSEARCH_JAVA_OPTS", "-Xms1g -Xmx1g");
        withEnv("node.store.allow_mmap", "false");
        withEnv("cluster.routing.allocation.disk.threshold_enabled", "false");
        waitingFor(Wait.forHttp("/").forStatusCode(200).withStartupTimeout(Duration.ofMinutes(3)));
    }

    String getHttpHostAddress() {
        return "http://" + getHost() + ":" + getMappedPort(9200);
    }
}
