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

## Azure and observability

Default scope expands from54 to57 modules: Azure OpenAI, Micrometer Metrics and Observation. Native8 module install gates passed67+45+2=114 cases, zero failures/errors/skips, without changing shared core source. Azure SDK1.0.0-beta.16, Micrometer1.16.6/1.16.5 are retained; the Azure test logging binding matches the Java8 branch's SLF4J1.7. Production closure346jars /114553 effective base classes stays at major52 or below, and15 reviewed static API findings remain confined to third-party optional paths.

For a Boot2 parent application using metrics/observation, add `<micrometer.version>1.16.6</micrometer.version>` alongside the earlier HTTP/Logback overrides. The parent imports Micrometer's BOM through this property, aligning core/commons/observation. A separate actual Boot2.7.18 Actuator application starts on Java8, asserts the loaded Micrometer version, constructs both listeners and records a metric. Reproduce it after installing this source branch:

```sh
./mvnw -f compatibility-tests/boot2-observability/pom.xml \
  -Dcompat.test.jvm="$JAVA8_HOME/bin/java" verify
```

Add the appropriate listener to model builders; the test is an in-process Actuator/Micrometer compatibility gate, not an Azure cloud-service acceptance. External model and instrumentation integration tests remain separate.

## JDBC vector stores and Chroma

Default scope reaches60 modules with PGVector, MariaDB and Chroma. Add the corresponding artifact from the compatibility BOM. PGVector0.1.6, PostgreSQL42.7.14 and MariaDB3.5.11 remain at upstream versions. Boot2 parent directly manages older JDBC drivers, so use `postgresql.version=42.7.14` / `mariadb.version=3.5.11` when selecting those stores; check the final dependency tree. Chroma builder still defaults to upstream V1: explicitly choose `ChromaApiVersion.V2` for newer servers.

Native8 unit gates:51+35+64=150 cases, no failures/errors/skips. Separate actual database-container gates: PGVector692/13 skips, MariaDB469/8 skips, Chroma242/4 skips;1403 cases, zero failures/errors,25 upstream capability skips. These tests execute real local MiniLM quantized inference with DJL/ONNX and actual vector insertion/search/filter/removal; no external paid model service participates. The default unit install uses `-DskipITs`, so these local database gates are recorded separately:

```sh
./mvnw -pl langchain4j-pgvector,langchain4j-mariadb,langchain4j-chroma \
  -Dcompat.test.jvm="$JAVA8_HOME/bin/java" -DskipITs -Dmaven.javadoc.skip=true install
./mvnw -pl langchain4j-pgvector,langchain4j-mariadb,langchain4j-chroma \
  -Dcompat.test.jvm="$JAVA8_HOME/bin/java" '-Dit.test=*IT' \
  failsafe:integration-test failsafe:verify
```

Chroma1.1 cosine queries showed one-ULP vector round-trip differences, and the unchanged exact-equality contract also failed on Java17. Production results and shared assertions were not changed. The V2 generic store contract now uses an L2 collection to retain exact-vector checks, converting its expected unit-vector cosine score to the correct L2 score with `1/(5-4*s)` and retaining the original1% tolerance. Separate cosine distance/score tests remain; a new real-MiniLM cosine vector precision/input-immutability regression checks every returned component within1e-6. Chroma's own [embedding invariants](https://github.com/chroma-core/chroma/blob/main/chromadb/test/property/invariants.py) also use approximate numerical comparison. This describes observed float precision, not a guarantee that every returned vector is unit-normalized.

Final runtime bytecode scope353jars /115824 effective base classes, none above52;15 reviewed optional third-party API findings remain. The preceding52-module complete/resumed reactor gate and later module/consumer gates remain distinct; no one-shot full60-module run is claimed.

## OpenSearch3.11 Java8 extension

Default scope expands60→61, adding langchain4j-opensearch to the compatibility BOM.
It reuses the separately verified Java8 SDK3.11 payload, retaining HTTP5.6.5/Core5.4.4/Jackson2.21.4/Reactor3.7.19 and SLF4J1.7.
The new optional scoreBoost builder setting preserves the legacy0.5 default and supports1.0 for OpenSearch2.19+; no implicit cluster-info request is added.
Actual native8 gates passed9 units,119 local-engine IT cases with2 contains capability skips, and2 standalone Boot2 consumers, zero failures/errors.
The real engine gate usesOpenSearch2.19.3 plus the actual local MiniLM model, and preserves all upstream assertions.
The first engine attempt found the cosine normalization change and disk/index-creation limits in the small fixture; final passes configure normalization explicitly and isolate fixture resource settings.
The preceding52-module complete/resumed unit gate remains a separate checkpoint; no one-shot full61 unit run or AWS account acceptance is claimed here.
Runtime dependency scan:62 classpath files including an earlier standalone fixture,390jars/127676 effective base classes, none above52; static API scan retains15 reviewed third-party optional references.
See [usage and engine settings](langchain4j-opensearch/README-JAVA8.md).

## Complete61-module unit checkpoint

A fresh serial clean install completed all61 default modules, with8872 unique XML unit cases, zero failures/errors and102 conditional skips.
Actual VM split:7690 cases on Java1.8.0_472 (72 skips),1182 cases on Java17.0.21 modern provider fixtures (30 skips).
These are current reports from the completed full command, not a sum of older component gates.
That command used skipITs; prior real-store integration gates, native Boot2 consumers and the new119-case OpenSearch engine gate remain separate evidence.
After clean, runtime classpaths were regenerated:62 files including the existing standalone fixture,390jars/127676 effective base classes, none above52; native API report retains15 reviewed optional third-party references.
This records the completed61-module scope before any later optional model expansion.

## Nine additional local models /70-module checkpoint

The default reactor expands61→70 with the remaining nine local MiniLM/E5/BGE model artifacts, all managed by the compatibility BOM.
Only module coordinates/activation change; original Java APIs, tokenizer resources, model hashes and inference backends remain unchanged.
All nine upstream SHA256 model hashes were independently rechecked, then58 original integration cases passed on native Java1.8.0_472, zero failures/errors/skips.
The actual models execute locally; this is not a fake embedding-response fixture.
A subsequent complete70-module clean install unit gate passed8872 cases, zero failures/errors and102 conditional skips;7690 cases used Java8 and1182 used Java17 modern provider fixtures.
That full unit command skips IT, so the58 model cases and earlier store integrations remain separate gates.
After clean, runtime scope71 files/399jars/127694 effective base classes has no major>52; native member audit remains15 reviewed optional third-party findings.
See [model coordinates and code](embeddings/README-JAVA8.md).

## Six document loaders on Java 8

Add only the required `cn.iocoder.langchain4j` loader dependency; the compatibility BOM manages its beta version. The loader implementations and public Java packages remain upstream originals.

| Artifact suffix (`langchain4j-document-loader-…`) | SDK retained / selected | Local acceptance |
| --- | --- | --- |
| `amazon-s3` | AWS S3 2.57.12 | Actual signed SDK GET against loopback HTTP |
| `azure-storage-blob` | Azure Blob 12.36.0, Azure Core 1.60.0 | Original four Azurite integration cases |
| `google-cloud-storage` | Google Storage 2.75.0 / libraries BOM 26.90.0 | Actual SDK metadata/media GET, source/parser pipeline |
| `tencent-cos` | COS 5.6.283 | Actual signed SDK GET with length validation |
| `playwright` | Playwright 1.57.0 | Fresh headless Chrome loads a Unicode page |
| `selenium` | Selenium 4.13.0 | Fresh headless Chrome and matching driver |

Selenium 4.13.0 is the last release supporting Java 8 ([official release note](https://www.selenium.dev/blog/2023/selenium-4-13-0-released/)); the Java 8 branch does not use upstream's Java-11 Selenium 4.33. Other SDK versions above remain the upstream source baseline. High SDK versions do not imply all cloud-service features are accepted.

```xml
<dependency>
    <groupId>cn.iocoder.langchain4j</groupId>
    <artifactId>langchain4j-document-loader-amazon-s3</artifactId>
</dependency>
```

For example, use `new AmazonS3DocumentLoader(s3Client).loadDocument(bucket, key, parser)` with your configured SDK client and parser. Credentials, service endpoints and resource permissions remain application configuration. The local tests use dummy credentials; no real cloud account was accepted. Browser libraries also need a browser binary and, for Selenium, a compatible driver; neither is bundled as a Java dependency.

An independent Boot 2 parent consumer is provided at `compatibility-tests/boot2-document-loaders`. Its POM is the complete example of the required Boot-managed version overrides; a compatibility BOM import cannot override an inherited direct dependency-management entry. Keep Boot 2 / Spring 5 and SLF4J 1.7; do not mix in Boot 3/4 starters or SLF4J 2.

Build all default modules first, using JDK 17 build tools and the native Java 8 test VM as documented above. The standalone consumer then uses the installed jars:

```sh
PLAYWRIGHT_SKIP_BROWSER_DOWNLOAD=1 ./mvnw \
  -f compatibility-tests/boot2-document-loaders/pom.xml verify \
  -Dcompat.test.jvm="$JAVA8_HOME/bin/java" \
  -Dcompat.browser.tests=true \
  -Dcompat.chrome.binary="/absolute/path/to/test-chrome" \
  -Dcompat.chrome.driver="/absolute/path/to/matching-chromedriver"
```

Docker is required for Azurite. Both browser tests use fresh temporary profiles. Omitting `compat.browser.tests=true` explicitly skips those two cases. The original eight Playwright browser contracts now launch the actual SDK directly in a fresh local Chrome profile, removing the Java-11-only Docker test helper; all eight passed on Java 8. Selenium Docker browser and real cloud-provider suites still require their documented environments and are not asserted by the default unit gate.

The runtime bytecode and API scans are separate: references in guarded Guava / shaded Netty modern-JDK implementations and Google logging adapters remain visible. Target-VM tests exercise CRC32C, spilled files, futures, unsigned comparison, buffer refcounts and the SLF4J 1.7 logging fallback; this is not a claim that every optional branch of those SDKs was tested.

## Validation checkpoints

The complete76-module clean install unit gate passed8887 executions, zero failures/errors,102 conditional skips:7705 executions on native Java8 and1182 upstream modern mock-server executions on Java17. ITs are separate from this command. After replacing only the Playwright test facility, its unit1 and browser9 cases passed again on native8; no production loader implementation was changed.

Six-loader component acceptance passed24 native8 cases with zero skips (15unit,3 protocol,4 Azurite,2 browser). The independent Boot2 parent consumer passed12 native8 cases with zero skips, including context/version checks and guarded SDK fallback paths. These are distinct runs, not one combined full-suite count.

Runtime scan including standalone consumers:78 classpath files,587 jars,175864 effective base classes, none above Java8 major52. The static API audit keeps60 third-party findings rather than deleting guarded modern-JDK implementations. No live S3/Azure/GCS/COS account, all browser engines or every SDK feature is claimed.


## Qdrant 1.20.0 on Java 8

Use `cn.iocoder.langchain4j:langchain4j-qdrant` from the Java 8 BOM. The module keeps the Qdrant Java Client at 1.20.0. Its integration tests use the matching server image and native Java 8. The installed consumer opens a gRPC connection, creates a test collection, writes and filters a UTF-8 text segment, then deletes it. The native runtime dependency graph contains 38 JARs and 13,870 base classes, all at Java 8 class-file level.

The native Java 8 Qdrant suite has 19 unit tests and 71 integration cases, with one negative case conditionally disabled because the backend supports contains filtering. The 77-module default unit run reports 8,906 cases (7,724 native Java 8, 1,182 modern fixtures) and 102 conditional skips. The container integration count is reported separately.

The Java 8 MCP WebSocket transport now clears its connection state under the transport monitor and aborts the detached connection outside that monitor. The reader can invoke its close callback while holding the WebSocket implementation lock. The regression test verifies that stop releases the transport monitor before calling abort, and the local WebSocket server tests cover a silent peer and a stale close callback.


## Easy RAG on Java 8

`cn.iocoder.langchain4j:langchain4j-easy-rag` is in the default reactor and the Java 8 BOM. Its SPI-provided Tika HTML parser, token-based recursive splitter and bundled quantized BGE small en v1.5 model run together on native Java 8. The local test parses and ingests an HTML document and retrieves the generated text segment. An installed-artifact consumer managed by the Boot 2.7.18 parent and the compatibility BOM passes the same workflow on native Java 8 with 109 runtime JARs and 31,097 base classes at Java 8 bytecode level. The upstream OpenAI integration test remains conditional on `OPENAI_API_KEY`.

The 78-module reactor passed Maven `validate`. The new module passed native Java 8 `clean install` and `verify`; the previous 77-module unit-test checkpoint is recorded above.
