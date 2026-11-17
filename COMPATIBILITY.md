# Java 8 compatibility

## Source baselines

- LangChain4j upstream main: `886c258b91090d41c941e69ab58edf6aa020a9c9` (1.24.0 / beta34 development sources).
- Selected official Spring starter sources: `langchain4j/langchain4j-spring` at `e00111bdccae9f091f0cc3a2fc4b56be0c273f99`; see `spring-boot2/UPSTREAM.md`.
- Original sources, packages, authors and licenses remain. Newly written compatibility classes use `@author 芋道源码`.
- Version `1.24.0-jdk8-SNAPSHOT`, group `cn.iocoder.langchain4j`; no Maven Central or remote SNAPSHOT publication yet.

## Supported build scope

The default reactor and BOM select core/AI Services, OpenAI, Anthropic, Google AI Gemini, Ollama, Mistral, Cohere, LocalAI, Hugging Face, Workers AI, Voyage AI, MCP, Skills, Shell Skills, reactive streaming, three HTTP clients, Apache Tika, embeddings/MiniLM quantized, compatibility helpers and two Boot 2 starters. Other upstream module sources remain for subsequent ports; **the default BOM does not promise those modules**. Jackson 3, official Boot 3/4 starters, provider/Docker integration tests and unlisted vendor SDK modules are not covered by this initial Java 8 build.

Build tools run on **JDK 17**, emitted classes target **Java 8**, and tests can fork an actual JDK 8 VM. The source generator lowers records, sealed types and newer platform APIs into `target/`; original modern sources remain mergeable. Kotlin targets 1.8. The generator and metadata tooling are build-only dependencies.

## Build and consume

Two small artifacts from `yudao-spring-ai`'s `main-jdk8` branch must first be installed into the same local Maven repository: `cn.iocoder.spring-ai:spring-ai-jspecify-compat:2.1.0-jdk8-SNAPSHOT` and `cn.iocoder.spring-ai:spring-ai-jdk-http-compat:2.1.0-jdk8-SNAPSHOT`. The HTTP bridge source must include commit `7d6683ae81a37a1bee081131e1e3817e97477ee5` or later. Neither Spring AI core nor a Spring Boot 3/4 starter is required by standalone LangChain4j.

```sh
# JAVA_HOME points to JDK 17. JAVA8_HOME points to an actual JDK 8 installation.
./mvnw -B -ntp install -DskipITs \
  -Dcompat.test.jvm="$JAVA8_HOME/bin/java" \
  -Dmaven.javadoc.skip=true

./mvnw -B -ntp -f compatibility-tests/boot2-consumer/pom.xml verify \
  -Dcompat.test.jvm="$JAVA8_HOME/bin/java"
```

Keep the same `-Dmaven.repo.local=...` on both commands when using a private cache. Default unit tests include local model/network fixtures; first execution may download DJL tokenizer native libraries. Set `DJL_CACHE_DIR` to a dedicated cache if concurrent builds exist. No external LLM credentials are needed for the local acceptance tests. Full provider `*IT` tests require their documented external services and are not asserted by `-DskipITs`.

OpenAI, Anthropic and Gemini's upstream mock suites use the build JDK 17 (`compat.fixture.test.jvm`) because Mokksy itself requires Java 17. Those results are kept separate from native Java 8 results. The independent `compatibility-tests/java8-provider-runtime` and standalone Boot 2 consumer execute on the target VM and cover actual local HTTP/SSE, tools, RAG and real local embedding execution. They do not assert successful calls to real model vendors.

## Dependency and API choices

- Spring Boot 2.7.18 / Spring 5.3, SLF4J 1.7.36, Logback 1.2.13, Jackson 2.21.4, Mockito 4/JUnit 5 for Java 8 tests.
- Newer compatible Reactor 3.7.19 and Apache HttpClient 5.6.5 / HttpCore 5.4.4 are explicitly aligned so an older Boot BOM cannot silently replace the bridge's required runtime APIs. Import the compatibility BOM as shown in README. When inheriting the official Boot 2 parent, also set `httpclient5.version=5.6.5`, `httpcore5.version=5.4.4`, and `logback.version=1.2.13`: Maven gives inherited direct management precedence over imported BOM entries. The standalone consumer uses exactly this documented setup and asserts the loaded runtime versions. For import-only Boot BOM setups, put the compatibility BOM first and inspect the resolved dependency tree.
- OpenNLP 1.9.5 uses an Apache sentence model trained with 1.9.3; provenance and hashes are in the model resource directory. CommonMark 0.21.0 is retained because newer releases require Java 11. DJL 0.40.0 and ONNX Runtime 1.23.2 are retained and exercised locally.
- Mutiny Zero 1.3.1 sources are narrowly repackaged for the Java 8 Flow bridge; see its `UPSTREAM.md`. The production runtime does not depend on a Java 11 Mutiny jar.
- The first new Maven group/major has no published API baseline. Revapi comparison is explicitly skipped by default (`revapi.skip=true`), not reported as passing; enable and pin a same-group baseline after publication. Java 8 API/classpath/bytecode checks and actual VM tests are separate gates.

## Deliberate platform limits

- The HTTP bridge supports HTTP/2 preference and HTTPS negotiation. Plain HTTP does not implement the native JDK h2c Upgrade; it uses HTTP/1.1. MCP explicitly preserves its upstream HTTP/2 preference. The bridge's own default remains HTTP/1.1.
- The MCP WebSocket bridge is a narrow adapter used by MCP, not a full `java.net.http.WebSocket` replacement. Complete-message `sendText` futures acknowledge queue acceptance, not completed socket flush; listener errors report later transport failures. Message/frame sizes are bounded, demand is tracked and retired connections are closed.
- Shell descendant cleanup is tested on Unix. Java 8 Windows PID access differs by VM; complete Windows process-tree cleanup is not verified. Existing Windows-only unit tests remain conditional and their skips are reported.
- Reactive Streams TCK suites keep upstream capacity limitations: some optional/untested rules and demand-overflow rule 317 skip. Additional real HTTP publisher overflow tests cover both bounded and direct modes; they do not claim to execute skipped TCK rules.

## Verification status

The 34-module default `verify` checkpoint passed: 7427 test executions, zero failures/errors, 111 skips. Native Java 8 accounted for 6245 executions / 81 skips; OpenAI/Anthropic/Gemini modern-fixture suites used Java 17 for 1182 / 30 skips. The two test-helper executions intentionally repeat cases. External integration tests, Javadoc and initial-major Revapi comparison were skipped and are not claimed as passing.

After the opening-timeout fix, the complete MCP native8 suite passed again: 470 tests, no failures/errors/skips. The independently inherited Boot 2 consumer passed with documented version properties, assertions on actual loaded versions, tools/RAG/default methods and SSE. Protocol responses are local fixtures; MiniLM embedding inference is real and local.

Final production-runtime scan, including the standalone consumer: 178 jars / 46221 effective base classes, no class above Java 8 major 52. Static member-reference audit retained 14 reviewed findings solely in third-party optional JDK9 Flow adapters and SLF4J legacy binder fallback probes; no self-owned direct newer-JDK call was found. This is not proof that every optional third-party path works on Java 8. A successful package/install with tests skipped is not a unit-test pass. The README's official suffix is preserved byte-for-byte from the pinned upstream commit.

## Additional Java8 modules

The default reactor has expanded from34 to47 modules: PDFBox/POI/Markdown/YAML parsers, Jsoup transformer, Judge0, GitHub loader, Jina/Nomic/OVH and Google Custom/SearchApi/Tavily search engines. The13 new modules passed151 native8 test executions with no failure/error/skip. Markdown pins CommonMark0.21.0. JitLLM's JDK21/22 FFM backend remains outside the Java8 scope. The preceding34-module counts are a historical checkpoint; new-coordinate dependency/consumer checks are separate.

## Current default scope and reproducible local MCP integration

The 47-module new-coordinate unit install passed 7587 executions, zero failures/errors and 111 skips. Of these, 6405 executions use native Java 8; 1182 use Java 17 for upstream modern mock-server fixtures (OpenAI/Anthropic/Gemini). The unit gate uses `-DskipITs`; it does not certify all external integrations. Tavily cancellation repeats ten times to cover cancellation before connection attachment. Runtime bytecode scan: 239 jars / 61892 effective base classes, none above major52. The static API audit is a separate report: optional third-party modern-JDK adapters and logging binders still need path-specific judgment.

JBang-based MCP tests run the client on Java8 and the upstream Quarkus server in a separate JDK17+ process. Install JBang, then select the server JDK explicitly:

```sh
export COMPAT_MCP_SERVER_JAVA_HOME="$JAVA17_HOME"
export COMPAT_JBANG_COMMAND="$(command -v jbang)"
./mvnw -pl langchain4j-mcp -Dcompat.test.jvm="$JAVA8_HOME/bin/java" \
  -Djbang.command="$PWD/compatibility/java8/jbang-server.sh" \
  -Dit.test=McpToolsStdioLegacyIT -Dmaven.javadoc.skip=true \
  failsafe:integration-test failsafe:verify
```

Compile test sources first when running this standalone Failsafe command. JBang resolves/builds an external server; the selected legacy stdio setup allows 180 seconds for cold startup, while tool-operation deadlines remain unchanged. This server fixture is not a Java8 runtime dependency. Javadoc generation against upstream modern-syntax source is still excluded from the Java8 verification command.

The subsequent local MCP transport gate passed165 executions (zero failures/errors,5 skips): modern/legacy stdio, Apache HTTP, modern/legacy WebSocket; the client JVM was Java8 and external Quarkus servers Java17.

## Agentic and typed APIs on Java8

The default reactor and BOM now contain **52 modules**. Five additional runtime modules are `langchain4j-typesafe`, `langchain4j-agentic`, `langchain4j-agentic-patterns`, `langchain4j-guardrails` and `langchain4j-embedding-store-filter-parser-sql`. Import the BOM and add only the required artifact under `cn.iocoder.langchain4j`; preserve the artifact-specific stable/beta version selected by the BOM. Java packages and public AgenticServices builders follow upstream. Java8 consumers declare ordinary interfaces/classes rather than Java records.

New module tests on actual Java8: TypeSafe58, Agentic660, Patterns193, Guardrails99, SQL parser192; zero failures/errors/skips. The full reactor's current XML reports contain8390 unique cases:7209 on Java8 and1181 on Java17 modern provider fixtures,102 skips total. The two helper-suite executions repeat tests, so execution-log totals can be higher. The first clean install exposed a mixed-clock precision bug; after correcting the common monotonic timeline, `clean install` resumed from Agentic and all remaining modules passed. The unchanged preceding modules had passed the initial clean build. Separate Boot2 consumer `verify` passed on actual Java8, including local HTTP/SSE/tools/RAG and loaded-version assertions.

Compatibility fixes preserve private-interface implementation behavior through package-private helpers, managed invocation context restoration, user-message multimodal contents, record accessor serialization and Java8 proxy metadata dispatch. Package-name and TimeUnit/Duration conversions are bridged with negative-duration/overflow tests. Persisted modern immutable-collection type IDs are mapped only from a fixed list to Java8 read-only wrappers; the existing deserialization allowlist still rejects unknown and disallowed types. No general class-name rewrite or broader polymorphic type permission is introduced.

Production bytecode scan:246jars /62906 effective base classes, none above major52. Static API audit has15 findings limited to third-party optional Java9 Flow adapters, SLF4J compatibility probes, and reflection-filtered Unsafe access with Guava fallbacks; no self-owned direct newer-JDK member call remains. Real provider services, all optional vendor modules and paid integrations are outside this gate. The earlier47-module counts are historical checkpoints, not current totals.

## Official OpenAI SDK and Agentic MCP

The default reactor and BOM now select54 modules. Add `cn.iocoder.langchain4j:langchain4j-open-ai-official` or `cn.iocoder.langchain4j:langchain4j-agentic-mcp` only when needed; both retain their upstream beta34 version through the BOM. OpenAI Java SDK4.101.0 is retained. Two Java9 Duration.toSeconds calls in the batch model use the equivalent Java8 getSeconds API. HTTP5/Core5 remain aligned with the compatibility parent (5.6.5/5.4.4). The SDK tests include actual client requests to local WireMock endpoints, not only mocked SDK method calls.

After the52-module gate, these two added modules passed140 and68 native8 cases respectively, zero failures/errors/skips; no shared core source changed in this batch. These are component gates in addition to the preceding reactor checkpoint, not a claim of a second one-shot full54-module run. Paid OpenAI/Azure endpoints and external MCP Agent integration tests remain outside the unit gate. Runtime scan expands to307jars /109760 effective base classes, none above major52. Static member audit remains15 reviewed third-party findings with no self-owned newer-JDK API call.
