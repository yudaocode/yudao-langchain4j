# Native Boot2 OpenSearch consumer

Install the matching Java8 Spring AI compatibility artifacts and LangChain4j Java8 reactor first.
With Maven/compiler on JDK17:

```sh
./mvnw -f compatibility-tests/boot2-opensearch/pom.xml -Dcompat.test.jvm=/absolute/path/to/jdk8/bin/java test
```

The native Boot2.7.18 parent imports the LangChain4j compatibility BOM.
Two native8 cases verify unique SDK3.11 provisioning, pinned Jackson2, actual local HTTP queries, Chinese document/metadata mapping and legacy/current engine score multipliers.
The POM explicitly overrides the old HTTP and Jakarta-namespace JSON versions managed by Boot2's parent.
The standalone test consumes the installed jar; it cannot be masked by provided original SDK classes from the producer reactor.
The HTTP response and embedding vector here are deterministic fixtures.
Separate OpenSearch engine tests use a real local engine and MiniLM model; no AWS account or paid provider is used.
