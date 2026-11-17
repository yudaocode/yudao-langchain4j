# OpenSearch on Java8

The compatibility BOM manages `cn.iocoder.langchain4j:langchain4j-opensearch:1.24.0-beta34-jdk8-SNAPSHOT`.
Install the matching Spring AI Java8 source distribution first: this module shares its standalone `spring-ai-opensearch-client-compat:2.1.0-jdk8-SNAPSHOT`, which supplies SDK3.11.0 without adding a Spring Boot runtime.
Do not add the original `org.opensearch.client:opensearch-java` jar alongside it; both contain the same SDK packages.
The same artifact can be used with the Java8 Spring AI OpenSearch store without duplicate SDK implementations.
These fork coordinates have not been deployed to Maven Central.

```java
OpenSearchEmbeddingStore store = OpenSearchEmbeddingStore.builder()
    .serverUrl("http://localhost:9200")
    .indexName("documents")
    .scoreBoost(1.0f) // OpenSearch2.19 and newer
    .build();
```

OpenSearch2.19 changed the cosine script's score normalization.
The legacy constructors and builder retain the upstream0.5 multiplier for older engines; select1.0 explicitly for2.19+ to avoid halving scores and changing minimum-score filtering.
This avoids adding an automatic cluster-info request or requiring another permission on restricted AWS deployments.
See [the upstream report](https://github.com/spring-projects/spring-ai/issues/4765) and [the vector-space definitions](https://docs.opensearch.org/latest/mappings/supported-field-types/knn-spaces/).
Java packages and existing constructors remain unchanged.
HTTP5/Core5 align at5.6.5/5.4.4, Jackson remains2.21.4, Reactor3.7.19 and SLF4J1.7 are retained for the shared Java8/Boot2 route.

A native Boot2.7.18 parent needs the following inherited version properties aligned:

```xml
<httpclient5.version>5.6.5</httpclient5.version>
<httpcore5.version>5.4.4</httpcore5.version>
<jackson-bom.version>2.21.4</jackson-bom.version>
<jakarta-json.version>2.1.3</jakarta-json.version>
<jakarta-json-bind.version>2.0.0</jakarta-json-bind.version>
<yasson.version>2.0.2</yasson.version>
```

See [the standalone Boot2 consumer](../compatibility-tests/boot2-opensearch) for the complete BOM/POM.
It checks one installed SDK provider, native8 execution, real local HTTP query/response mapping and both score multipliers, without relying on reactor classes or a paid model.

## Local engine integration tests

With Maven/compiler on JDK17 and Docker available:

```sh
./mvnw -pl langchain4j-opensearch verify \
  -Dcompat.test.jvm=/absolute/path/to/jdk8/bin/java \
  -Dcompat.test.log.level=warn \
  -Dit.test=OpenSearchEmbeddingStoreLocalIT,OpenSearchEmbeddingStoreRemovalIT
```

The isolated engine defaults toOpenSearch2.19.3,1GB heap, disabled security and test-only disk-watermark checks.
Override `opensearch.test.image` and `opensearch.test.score-boost` together when selecting an older engine.
The modern Java11-only opensearch-testcontainers helper is replaced by a small fixture on Testcontainers1.21.4; the production SDK is not downgraded.
Test clients/transports are explicitly closed; assertions remain unchanged.

Native8 gates:9 contract units,119 actual engine integration cases (zero failures/errors,2 unsupported contains-filter capability skips), plus2 standalone Boot2 cases.
The initial real-engine run exposed the score-normalization change and a test-container disk block; neither is hidden as an ignored test.
The final119-case run passed after explicit score configuration and isolated fixture settings.
Embedding integration uses the actual local MiniLM ONNX model, not a fake external model response.
AWS/LocalStack and real AWS account acceptance remain separate from this local-engine gate.
The default reactor now contains61 modules; this component gate does not assert a new one-shot full61-module unit run.
