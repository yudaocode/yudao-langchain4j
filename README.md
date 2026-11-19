# 芋道 LangChain4j 兼容版

> 版本命名：保留当前源码基线的官方版本号；共用现代制品不加 JDK 后缀，隔离维护的兼容路线添加 `jdk8` / `jdk17` / `jdk25`。SNAPSHOT 尚未发布 Maven Central。JDK 路线后缀不等于最低字节码版本，框架依赖仍按 Boot 版本选择。
> 完整版本规则与预览命名见 [VERSIONING.md](VERSIONING.md)。


本仓库基于官方 LangChain4j 新版源码，为仍使用 **JDK 8 / Spring Boot 2.x** 的项目提供兼容分支，尽量保留新版本功能。上游作者、许可证和 Java 包名保留；以下说明之后继续保留官方 README。

| 分支 | 运行环境 | 使用方式 |
| --- | --- | --- |
| `main` | JDK 17+；Spring Boot 3.x / 4.x 使用对应官方 starter | 跟踪官方源码，优先使用官方 `dev.langchain4j` 制品 |
| `main-jdk8` | JDK 8；Spring Boot 2.7.18 / Spring 5.3 | 使用 `cn.iocoder.langchain4j` 兼容制品 |

GitHub：[yudaocode/yudao-langchain4j](https://github.com/yudaocode/yudao-langchain4j) · Gitee：[yudaocode/yudao-langchain4j](https://gitee.com/yudaocode/yudao-langchain4j)

## JDK 8 / Boot 2 如何使用

切换到 `main-jdk8`。兼容版统一使用 `cn.iocoder.langchain4j`，stable 模块为 `1.24.0-jdk8-SNAPSHOT`、beta 模块为 `1.24.0-beta34-jdk8-SNAPSHOT`，保留对应的 artifactId 与 `dev.langchain4j` Java 包名。**尚未发布 Maven Central 或远端 SNAPSHOT 仓库**；当前需先按兼容分支的构建说明安装到本地 Maven 仓库。正式发布时在官方基线和 JDK 路线后追加日期，未发布上游基线增加 preview；不增加同日序号。stable 与 beta 模块保持各自官方编号，由 BOM 管理。

使用官方 `spring-boot-starter-parent:2.7.18` 时，还需设置以下三个属性：Boot 父 POM 直接管理这些依赖，Maven 中导入 BOM 不能覆盖父 POM 的直接声明。仅导入 Boot BOM 的项目应将兼容 BOM 放在 Boot BOM 前，并核对最终依赖树。

```xml
<properties>
    <httpclient5.version>5.6.5</httpclient5.version>
    <httpcore5.version>5.4.4</httpcore5.version>
    <logback.version>1.2.13</logback.version>
</properties>

<dependencyManagement>
    <dependencies>
        <dependency>
            <groupId>cn.iocoder.langchain4j</groupId>
            <artifactId>langchain4j-bom</artifactId>
            <version>1.24.0-jdk8-SNAPSHOT</version>
            <type>pom</type>
            <scope>import</scope>
        </dependency>
    </dependencies>
</dependencyManagement>

<dependencies>
    <dependency>
        <groupId>cn.iocoder.langchain4j</groupId>
        <artifactId>langchain4j-spring-boot-starter</artifactId>
    </dependency>
    <dependency>
        <groupId>cn.iocoder.langchain4j</groupId>
        <artifactId>langchain4j-open-ai-spring-boot-starter</artifactId>
    </dependency>
</dependencies>
```

```yaml
langchain4j:
  open-ai:
    chat-model:
      base-url: https://api.openai.com/v1
      api-key: ${OPENAI_API_KEY}
      model-name: gpt-4o-mini
```

```java
@AiService
public interface Assistant {
    String chat(String message);
}
```

通过 Spring 注入 `Assistant` 后调用 `chat`。独立 Java 项目可以引入兼容版 `langchain4j` 和 `langchain4j-open-ai`，使用原有 builder API。不要在同一个运行时混用官方与兼容版同名核心模块；它们的 Java 包名相同。Java 8 的 `Flow` 等新 JDK API 使用窄桥接类型，不能将它视为与官方 JDK 17 制品完全二进制兼容。

前一批默认兼容构建为 **54 个 Maven 模块**，新增 TypeSafe、Agentic、Agentic Patterns、Guardrails、SQL Filter Parser。按需添加以下依赖，版本由上述 BOM 管理；Agentic 及 Patterns 保留官方 beta 编号，不能手动统一成 stable 版本：

```xml
<dependency>
    <groupId>cn.iocoder.langchain4j</groupId>
    <artifactId>langchain4j-agentic</artifactId>
</dependency>
<dependency>
    <groupId>cn.iocoder.langchain4j</groupId>
    <artifactId>langchain4j-agentic-patterns</artifactId>
</dependency>
```

Java 包名及 `AgenticServices` builder 入口沿用官方，Java8 应用使用普通接口/类声明 Agent。其他可选模块为 `langchain4j-typesafe`、`langchain4j-guardrails`、`langchain4j-embedding-store-filter-parser-sql`，不需要全部引入。前一批 52 模块报告去重后 8390 项，失败/错误为零；其中 JDK8 7209 项、JDK17 上游现代模拟服务 1181 项，条件跳过共 102 条。外部模型集成测试需单独配置和验证。

新增 `langchain4j-open-ai-official` 和 `langchain4j-agentic-mcp`，均由上述 BOM 管理、按需引入。前者保留上游 OpenAI Java SDK `4.101.0`，入口为 `dev.langchain4j.model.openaiofficial.OpenAiOfficialChatModel` / `OpenAiOfficialResponsesChatModel`；后者将现有 `McpClient` 接入 `dev.langchain4j.agentic.mcp.McpAgent` builder。两模块真实 JDK8 单测共 208 项全部通过，包含实际 SDK 到本地 HTTP 服务的请求；真实模型及远程 MCP 服务需独立验证。

可观测性与 Azure 扩展已纳入默认构建（前一批 **57 个 Maven 模块**）：`langchain4j-azure-open-ai`、`langchain4j-micrometer-metrics`、`langchain4j-observation`，均按需引入、由上述 BOM 管理。保留 Azure SDK `1.0.0-beta.16`、Micrometer Metrics `1.16.6`、Observation `1.16.5` 的上游依赖；三个模块真实 JDK8 测试共 114 项通过。

Boot2 parent 应用使用这些可观测性扩展时，额外设置 `<micrometer.version>1.16.6</micrometer.version>`，避免父 POM 覆盖成旧版；本仓独立 [Boot2 Actuator 消费者](compatibility-tests/boot2-observability) 已在真实 JDK8 上启动，并断言加载的版本及两个 Listener 可用。创建 `MicrometerMetricsChatModelListener(meterRegistry)` 或 `ObservationChatModelListener(observationRegistry, meterRegistry)` 后，加入模型 builder 的 listeners。Azure 模型使用 `AzureOpenAiChatModel` builder，需配置自己的 endpoint/API key；付费 Azure 服务尚未验收。

向量库扩展继续加入 **PGVector、MariaDB、Chroma**，该批次已纳入默认兼容构建。按需引入 `langchain4j-pgvector`、`langchain4j-mariadb`、`langchain4j-chroma`（同 groupId，版本由 BOM 管理）。保留上游 PGVector `0.1.6` / PostgreSQL JDBC `42.7.14` / MariaDB JDBC `3.5.11`；Boot2 parent 应用使用对应数据库时，设置 `<postgresql.version>42.7.14</postgresql.version>`、`<mariadb.version>3.5.11</mariadb.version>`，避免父管理覆盖。数据库地址、认证和建表权限由应用配置。

Chroma 新服务请显式使用 `.apiVersion(ChromaApiVersion.V2)`；builder 沿用上游默认 V1，不能默认连到只支持 V2 的服务。这三库单测150项全部通过，真实JDK8容器集成1403项、零失败/错误、25项上游条件跳过，包含真实本地MiniLM推理和向量数据库操作。跳过项与浮点精度验证边界见 COMPATIBILITY。

OpenSearch 扩展已加入 JDK8 默认构建，复用 SDK **3.11.0** 的独立兼容客户端。按需引入 `langchain4j-opensearch`（BOM 管理）；引擎 **2.19+** 使用 `.scoreBoost(1.0f)`，较老引擎保留默认 `0.5f`。原生8通过9项unit、119项真实引擎IT（2项contains能力跳过）及2项Boot2消费测试，详见[接入与引擎配置](langchain4j-opensearch/README-JAVA8.md)。

2026-11-17 前一检查点 **61 模块完整 `clean install` 单测检查点**：8,872 项，失败/错误为 0，条件跳过 102 项。其中原生 JDK8 执行 7,690 项，上游现代模拟服务在 JDK17 执行 1,182 项；该命令跳过 IT，实际向量库等集成门禁单列，不能当作所有外部服务都验收通过。

本地 Embedding 模型新增 MiniLM 非量化、E5 small v2（普通/量化）、BGE small en / en v1.5 / zh v1.5（普通/量化），现共 10 个模型制品，默认构建扩为 **70 模块**。新增 9 个模型的 **58 项真实本地推理测试在原生 JDK8 全部通过**，权重逐一按上游 SHA256 校验；ONNX Runtime `1.23.2`、DJL `0.40.0` 保持。接入与制品列表见[本地模型使用说明](embeddings/README-JAVA8.md)。

此前70模块完整clean install单测8872项零失败/错误、102条件跳过：7690项native8、1182项现代fixture17。模型58项IT与真实向量库等门禁单列，未合并成一次全量运行。

支持范围、构建步骤与验证边界见 **`main-jdk8` 分支的 [COMPATIBILITY.md](https://github.com/yudaocode/yudao-langchain4j/blob/main-jdk8/COMPATIBILITY.md)**。BOM 仅列入已纳入兼容构建的模块；其余上游模块源码保留，不代表已经支持 Java 8。


新增 **S3、Azure Blob、GCS、腾讯 COS、Playwright、Selenium** 六个文档加载器，该批次覆盖 **76 个 Maven 模块**。按需引入 `cn.iocoder.langchain4j:langchain4j-document-loader-…`，beta 版本由 BOM 管理；完整 POM、SDK 版本和调用示例见[文档加载器用法](document-loaders/README-JAVA8.md)。Selenium 使用官方最后支持 Java8 的 `4.13.0`，其余五类 SDK 保留上游基线；Boot2 父 POM 的版本覆盖示例见[独立消费者](compatibility-tests/boot2-document-loaders)。

76模块完整单测8887项零失败/错误、102条件跳过（7705 native8，1182现代fixture17）；组件24项、独立Boot2消费12项原生8全部通过且零跳过。Playwright另保留原8项浏览器断言并通过原生8实跑。扫描587jar/175864基础类无高于Java8字节码；第三方受guard API引用仍保留审计，不代表全部云账号或外部服务验收。详见兼容说明。

## Qdrant 向量存储（JDK 8）

导入本分支 BOM 后，按需引入：

```xml
<dependency>
    <groupId>cn.iocoder.langchain4j</groupId>
    <artifactId>langchain4j-qdrant</artifactId>
</dependency>
```

保留上游 Qdrant Java Client `1.20.0`。在服务端创建与 embedding 维度一致的集合后，使用 `QdrantEmbeddingStore.builder().host(host).port(6334).collectionName(collection).build()`。端口为 gRPC 端口，TLS 和 API key 按部署配置。应用退出时调用 `close()`。

Qdrant 批次覆盖 77 个默认模块，单测 8,906 项。Qdrant 在原生 JDK8 通过 19 项单测；真实 Qdrant 1.20.0 容器有 70 项通过、1 项按上游条件跳过。独立安装包也验证了向量写入、元数据过滤与删除。

本批同时修复 Java 8 MCP WebSocket 停止连接时，与关闭回调相互等待的问题。已有连接立即中止，正在建立的连接仍在打开后中止。

## Easy RAG（JDK 8）

导入本分支 BOM 后，引入 `cn.iocoder.langchain4j:langchain4j-easy-rag`。默认从依赖发现 Tika 解析器、递归切分器和量化 BGE small en v1.5 模型。

```java
Document document = FileSystemDocumentLoader.loadDocument(Paths.get("handbook.html"));
EmbeddingStore<TextSegment> store = new InMemoryEmbeddingStore<>();
EmbeddingStoreIngestor.ingest(document, store);
List<Content> matches = EmbeddingStoreContentRetriever.from(store)
        .retrieve(Query.from("What is the cancellation policy?"));
```

上述本地流程可直接在 JDK 8 运行。若需要让模型根据检索内容生成答案，再将检索器和 ChatModel 接入 `AiServices`。

Easy RAG 批次覆盖 78 个 Java 8 默认模块。

## ONNX 文本排序（JDK 8）

导入本分支 BOM 后，加入 `cn.iocoder.langchain4j:langchain4j-onnx-scoring`。准备排序模型的 ONNX 文件和 `tokenizer.json`，使用 `new OnnxScoringModel(modelPath, tokenizerPath, 512)`，通过 `scoreAll(segments, query)` 对段落评分，使用完后关闭模型。

保持 ONNX Runtime `1.23.2` 和 DJL `0.40.0`。原生 JDK 8 用量化 MiniLM 实际推理。该批次覆盖 79 个默认模块。

## MCP Docker Transport（JDK 8）

导入本分支 BOM 后，引入 `cn.iocoder.langchain4j:langchain4j-mcp-docker`。用 `DockerMcpTransport.builder().dockerHost(dockerHost).image(image).build()` 创建传输对象，再传给 `DefaultMcpClient.Builder.transport(...)`。退出时关闭客户端，由它释放容器。

保留 `docker-java:3.7.1`。使用 Boot 2 父 POM 时，设置 `httpclient5.version=5.6.5`、`httpcore5.version=5.4.4`，使已安装传输层能加载相应 HTTP API。MCP Docker 批次覆盖 80 个 Java 8 默认模块。

## Kotlin 扩展（JDK 8）

导入本分支 BOM 后引入 `cn.iocoder.langchain4j:langchain4j-kotlin`。AI Services 接口可返回 `Flow<String>` 或 `Flow<StreamingChatModelReply>`，传入 `StreamingChatModel` 后按正常方式收集 Flow。也可用文档异步解析、模型调用和流式回调扩展。

保留 Kotlin 编译器 `2.1.21`、stdlib `1.9.25` 和 Coroutines `1.8.1`。Boot 2 父 POM 消费验证采用对应的 `kotlin.version` 和 `kotlin-coroutines.version`。Kotlin 批次覆盖 81 个默认模块。

## Agentic A2A（JDK 8）

导入 `cn.iocoder.langchain4j:langchain4j-bom:1.24.0-jdk8-SNAPSHOT` 后引入 `cn.iocoder.langchain4j:langchain4j-agentic-a2a`，通过 `AgenticServices.a2aBuilder(url, MyAgent.class)` 连接 A2A 服务。A2A Java SDK 1.5.1.Final 由 [AgentScope Java 8 兼容分支](https://github.com/yudaocode/yudao-agentscope-java/tree/main-jdk8) 提供，请先安装其 `agentscope-a2a-sdk-java8:2.1.0-jdk8-SNAPSHOT`。同一应用只使用一套 A2A SDK 制品。

Boot 2.7.18 应用按 [AgentScope A2A 接入说明](https://github.com/yudaocode/yudao-agentscope-java/blob/main-jdk8/JAVA8-COMPATIBILITY.md#a2anacos-与聚合包) 对齐 Gson、Jackson、HTTP Client 与 Reactor。A2A 批次覆盖 82 个默认模块。

## 实验性 SQL 检索（JDK 8）

导入兼容 BOM 后引入 `cn.iocoder.langchain4j:langchain4j-experimental-sql`，使用 `SqlDatabaseContentRetriever.builder().dataSource(source).chatModel(model).build()`；数据库驱动根据实际数据库添加，数据源账号授予所需表的只读权限。保留上游 JSqlParser `4.9`。SQL 批次覆盖 83 个默认模块。

## Milvus V1 与 V2（JDK 8）

导入兼容 BOM 后按既有 API 选择 `cn.iocoder.langchain4j:langchain4j-milvus` 或 `cn.iocoder.langchain4j:langchain4j-milvus-v2`，通过 `MilvusEmbeddingStore.builder()` 或 `MilvusV2EmbeddingStore.builder()` 配置服务地址、集合与向量维度。两者均保留 Milvus Java SDK `2.6.27`，实测服务端 `2.6.27`。V2 的 BM25、稀疏向量和混合检索按原有 API 使用。Milvus 批次覆盖 85 个默认模块。

## Bedrock、Google GenAI 与 Azure AI Search（JDK 8）

导入兼容 BOM 后，按需引入 `cn.iocoder.langchain4j` 的 `langchain4j-bedrock`、`langchain4j-google-genai` 或 `langchain4j-azure-ai-search`。使用原有的 `BedrockChatModel.builder()`、`GoogleGenAiChatModel.builder()` 或 `AzureAiSearchEmbeddingStore.builder()`，配置相应的区域、模型、索引和凭据。

保留 AWS SDK `2.57.12`、Google Gen AI SDK `1.80.0` 和 Azure AI Search SDK `12.0.3`。Spring Boot 2.7.18 使用这些模块时，请参考 [已验证的依赖版本](COMPATIBILITY.md#cloud-adapters-on-java-8)；Azure AI Search 需要对齐 Reactor 和 Netty。本批云适配覆盖 88 个默认模块。

## Oracle、Azure Cosmos Mongo vCore 与 Azure DocumentDB（JDK 8）

导入兼容 BOM 后按需引入 `cn.iocoder.langchain4j` 的 `langchain4j-oracle`、`langchain4j-azure-cosmos-mongo-vcore` 或 `langchain4j-azure-documentdb`。Oracle 通过 `OracleEmbeddingStore.builder().dataSource(source)` 配置数据源和表。Azure 适配通过对应 builder 配置 Mongo 客户端、数据库、集合和向量索引类型。

保留 Oracle JDBC `23.26.4.0.0` 和 MongoDB Java Driver `5.6.0`。Spring Boot 2.7.18 应用使用 `oracle-database.version=23.26.4.0.0` 和 `mongodb.version=5.6.0` 对齐实际使用的驱动。Oracle 和 Azure Mongo 批次覆盖 91 个默认模块。

## Weaviate（JDK 8）

导入兼容 BOM 后引入 `cn.iocoder.langchain4j:langchain4j-weaviate`，在 `WeaviateEmbeddingStore.builder()` 中填写服务地址、对象类名与元数据字段。可按需开启 gRPC 写入并配置 gRPC 端口。保留上游 Java 客户端 `5.5.0`，实测 Weaviate 服务端 `1.40.5`。Spring Boot 2.7.18 应用使用 `gson.version=2.15.0` 与客户端调用对齐。Weaviate 批次覆盖 92 个默认模块。

## Vertex AI 与 Tablestore（JDK 8）

导入兼容 BOM 后，按需选择 `langchain4j-vertex-ai`、`langchain4j-vertex-ai-gemini`、`langchain4j-vertex-ai-anthropic` 或 `langchain4j-tablestore`。Vertex AI 通过相应 builder 传入工程、区域、模型名与 Google 凭据。Tablestore 可使用 `new TablestoreEmbeddingStore(syncClient, vectorDimension)` 并按需调用 `init()` 建表与索引。Spring Boot 2.7.18 已验证的依赖配置见 [兼容说明](COMPATIBILITY.md#vertex-ai-and-tablestore-on-java-8)。Vertex AI 与 Tablestore 批次覆盖 96 个默认模块。

## Azure Cosmos NoSQL、Couchbase、MongoDB Atlas 与 Pinecone（JDK 8）

导入兼容 BOM 后，按需引入 `langchain4j-azure-cosmos-nosql`、`langchain4j-couchbase`、`langchain4j-mongodb-atlas` 或 `langchain4j-pinecone`。通过各自的 `EmbeddingStore.builder()` 提供库、集合或索引和客户端凭据。Spring Boot 2.7.18 的验证依赖见 [兼容说明](COMPATIBILITY.md#additional-vector-sdk-adapters-on-java-8)。四个向量 SDK 批次覆盖 100 个默认模块。

Couchbase 保留 Java SDK `3.13.1`。原生 JDK 8 在 Docker 中连接 Couchbase Server Enterprise `8.0.3`，通过 9 项真实集成测试和 9 项契约测试，均零跳过。真库用例覆盖批量写入、文本与元数据回读、向量检索和得分阈值。独立 Spring Boot 2.7.18 / JDK 8 应用使用本地安装的 JAR 通过写入、检索与删除验证。可用 `-Dcouchbase.image` 指定测试镜像，见[复验命令](COMPATIBILITY.md#additional-vector-sdk-adapters-on-java-8)。

MongoDB Atlas Local 7.0.9 在 ARM64 Docker 和原生 JDK 8 下通过 130 项真库集成测试与 14 项单测，覆盖向量写入、过滤、更新、删除和旧索引。1 项专门测试不支持 contains 的公共用例按能力条件跳过，该适配器支持 contains。独立 Boot 2.7.18 项目使用 `mongodb.version=5.6.0`，用本地安装的 JAR 通过建索引、写入、检索和删除验证。复验命令见[兼容说明](COMPATIBILITY.md#additional-vector-sdk-adapters-on-java-8)。

## Azure Container Apps Dynamic Sessions（JDK 8）

导入兼容 BOM 后引入 `cn.iocoder.langchain4j:langchain4j-code-execution-engine-azure-acads`，使用 `SessionsREPLTool(poolManagementEndpoint)` 连接已配置的 Azure 会话池。适配器沿用 Azure 默认凭据与 Java 8 HTTP 传输。Dynamic Sessions 批次覆盖 101 个默认模块。

## Elasticsearch（JDK 8）

导入兼容 BOM 后引入 `cn.iocoder.langchain4j:langchain4j-elasticsearch`，使用 `ElasticsearchEmbeddingStore.builder()` 配置客户端及索引。Java 8 路线使用 Elasticsearch Java 与 REST 客户端 `8.19.25`，Spring Boot 2.7.18 中对应 `elasticsearch.version=8.19.25`、`jakarta-json.version=2.1.3` 和 `jackson-bom.version=2.21.4`。使用 RRF 混合检索和返回向量时，服务端还需支持对应 API。Elasticsearch 批次覆盖 102 个默认模块。

官方 Elasticsearch 8.19.25 的 Docker 试用节点已在原生 JDK 8 通过 346 项集成测试和 45 项单测。RRF 混合检索已执行，另有 6 项按现有 contains 能力条件跳过。独立 Boot 2.7.18 项目使用本地安装的 JAR，通过向量写入检索删除和 RRF 排序两个用例。复验命令见[兼容说明](COMPATIBILITY.md#elasticsearch-on-java-8)。

## Amazon S3 文档加载（JDK 8）

引入 `cn.iocoder.langchain4j:langchain4j-document-loader-amazon-s3`，通过 `AmazonS3DocumentLoader` 加载文档。原生 JDK 8 在 LocalStack 3.1.0 上通过 4 项 S3 API 集成测试，覆盖单文件、空桶、多文件和前缀筛选，另有 3 项单测通过。

## Oracle Coherence（JDK 8）

导入兼容 BOM 后引入 `cn.iocoder.langchain4j:langchain4j-coherence`。先安装 [Spring AI Java 8 Coherence 兼容包](https://github.com/yudaocode/yudao-spring-ai/tree/main-jdk8/compatibility/coherence-ai) `cn.iocoder.spring-ai:spring-ai-coherence-ai-compat:2.1.0-jdk8-SNAPSHOT`。构造 `CoherenceEmbeddingStore` 或 `CoherenceChatMemoryStore` 时可以传入 Java 8 的 `Session.create()` 结果，也可以使用默认缓存工厂。字符串命名 Session 属于现代版本路线。POF 应用使用 `coherence.pof.config=langchain4j-coherence-java8-pof-config.xml` 或在自己的 POF 配置中包含它。Coherence 批次覆盖 103 个默认模块。

## Docling 文档解析（JDK 8）

导入兼容 BOM 后添加 `cn.iocoder.langchain4j:langchain4j-document-parser-docling` 和参考 HTTP 实现 `cn.iocoder.langchain4j:langchain4j-docling-serve-client-compat`。Java 8 路线沿用 Docling Java SDK 0.7.1 的类型与 SPI，使用 Jackson 2。Spring Boot 2.7.18 消费项目使用前文的 Jackson 2 和 HttpComponents 5 版本覆盖。

```java
DoclingServeApi api = DoclingServeApi.builder()
        .baseUrl(URI.create("http://localhost:5001"))
        .build();
DoclingDocumentParser parser = DoclingDocumentParser.builder()
        .doclingClient(api)
        .build();
Document document = parser.parse(inputStream);
```

`DoclingServeApi` 位于 `ai.docling.serve.api`，`DoclingDocumentParser` 位于 `dev.langchain4j.data.document.parser.docling`。解析器连接独立运行的 Docling Serve。Docling 批次覆盖 106 个默认模块。

## 文档聊天索引更新器（JDK 8）

`internal/langchain4j-docu-chatbot-updater` 是维护文档搜索索引的命令行应用，默认构建会安装本地制品。按前文准备兼容分支依赖后，使用构建 JDK 运行以下 Maven 命令，再用 Java 8 启动。

```bash
mvn -pl internal/langchain4j-docu-chatbot-updater -am -DskipTests -DskipITs install
mvn -pl internal/langchain4j-docu-chatbot-updater dependency:build-classpath -Dmdep.outputFile=target/runtime-classpath.txt
module=internal/langchain4j-docu-chatbot-updater
"$JAVA8_HOME/bin/java" -cp "$module/target/classes:$(cat "$module/target/runtime-classpath.txt")" dev.langchain4j.docu.chatbot.updater.Updater
```

从包含 `docs/docs` 的工作目录启动。应用读取 `GOOGLE_AI_GEMINI_API_KEY`、`MILVUS_URI` 和 `MILVUS_API_KEY`，更新指定 Milvus 实例已有的 `docu` 集合。更新器批次覆盖 107 个默认模块。

## Cassandra 和 Astra DB（JDK 8）

导入兼容 BOM 后添加 `cn.iocoder.langchain4j:langchain4j-cassandra`，它会传递引入 Astra DB Java SDK 1.2.4 / Stargate SDK 2.3.4 的兼容包。Cassandra 的向量检索使用 Cassandra 5.0 和 Apache Cassandra Java Driver 4.19.0。Spring Boot 2.7.18 项目使用前文的 Jackson 2、HttpComponents 5 及 Reactor 版本覆盖。

使用 `CassandraEmbeddingStore` 与 `CassandraChatMemoryStore` 时可传入含 keyspace 的 `CqlSession`。Astra CQL 可沿用 `builderAstra()`，或按官方文档构建带 Secure Connect Bundle 的会话。Astra Data API 则将 `new AstraDB(token, endpoint, keyspace).collection(collection)` 传入 `AstraDbEmbeddingStore`。Cassandra 批次覆盖 109 个默认模块。[Astra Java Driver 文档](https://docs.datastax.com/en/astra-db-serverless/drivers/java-driver.html)

## Vespa（JDK 8）

导入兼容 BOM 后添加 `cn.iocoder.langchain4j:langchain4j-vespa`，通过 `VespaEmbeddingStore.builder().url(endpoint).build()` 连接已部署向量 schema 的服务。Vespa 8.763.13 已实测。Java 8 客户端通过 Vespa Document API 提交单条和批量写入，批量会逐条提交，并通过 Vespa Query API 执行最近邻搜索。mTLS 应用沿用 `certPath(...)` 和 `keyPath(...)`。Vespa 批次覆盖 110 个默认模块。[Vespa Document API](https://docs.vespa.ai/en/reference/api/document-v1.html)

## IBM watsonx.ai（JDK 8）

导入兼容 BOM 后添加 `cn.iocoder.langchain4j:langchain4j-watsonx`。它使用 IBM SDK 0.50.0 的 Java 8 兼容源码与 Jackson 2 SPI，并依赖已更新的 Spring AI Java 8 HTTP 兼容包。使用 `WatsonxChatModel` 或 `WatsonxStreamingChatModel` 的 builder 配置 `baseUrl`、`projectId`、`modelName` 与 `apiKey` 或 `authenticator`。Spring Boot 2.7.18 应用采用前文的 Jackson 2、HttpComponents 5 和 Reactor 版本覆盖。Watsonx 批次覆盖 112 个默认模块。[IBM Java SDK](https://github.com/IBM/watsonx-ai-java-sdk/tree/0.50.0)

## Hibernate 实验检索器（JDK 8）

导入兼容 BOM 后添加 `cn.iocoder.langchain4j:langchain4j-experimental-hibernate`。它使用 Spring Boot 2 管理的 Hibernate ORM 5.6.15.Final，通过 `HibernateContentRetriever.builder().entityManagerFactory(entityManagerFactory).chatModel(chatModel).build()` 接入 Boot 2 的 `javax.persistence.EntityManagerFactory`。结果以 JSON 表示标量、投影及已加载的实体字段，关联以标识表示。检索器通过 HQL 查询结果 API 读取数据。Hibernate 实验检索器批次覆盖 113 个默认模块。

## Infinispan（JDK 8）

导入兼容 BOM 后添加 `cn.iocoder.langchain4j:langchain4j-infinispan`，使用 `InfinispanEmbeddingStore` 和 `InfinispanChatMemoryStore` 接入服务。Boot 2 项目可设置 `infinispan.version=13.0.22.Final`，对齐 JDK 8 客户端版本。已连接使用 SCRAM-SHA-512 的 Infinispan 16.3.1。单节点经 Docker 端口映射或 NAT 接入且服务端通告地址不可达时，可在传入的客户端配置上设置 `clientIntelligence(ClientIntelligence.BASIC)`。Infinispan 批次覆盖 115 个默认模块。

## 响应式流（JDK 8）

导入兼容 BOM 后添加 `cn.iocoder.langchain4j:langchain4j-reactive-streaming`。该模块使用兼容版 Mutiny Zero 1.3.1 和 Spring AI 的 Java 8 Flow API，已验证六种背压策略及 Boot 2 安装包的上游取消行为。构建会生成二进制、源码和 Javadoc 制品。

## 构建制品（JDK 8 路线）

使用 JDK 17 构建和生成 Javadoc。发布的库编译为 Java 8 字节码，单测和安装包消费验证可用实际 JDK 8 运行。使用 Jackson 2 的 Java 8 制品已纳入默认 BOM。Jackson 3 和依赖较新 JVM 的原生推理扩展保留上游环境要求。

## Hibernate 5 的 PostgreSQL 向量查询（JDK 8）

导入兼容 BOM 后加入 `cn.iocoder.langchain4j:langchain4j-hibernate-pgvector`。数据库需启用 pgvector。在 Hibernate 配置中令 `hibernate.metadata_builder_contributor` 指向 `dev.langchain4j.store.embedding.hibernate.pgvector.PgVectorFunctions`，Spring Boot 2 对应属性为 `spring.jpa.properties.hibernate.metadata_builder_contributor`。如果应用已注册其他 contributor，可在现有 contributor 中调用 `new PgVectorFunctions().contribute(metadataBuilder)`。

把实体的 `float[]` 字段映射到 PostgreSQL 向量列，例如三维字段：

```java
@org.hibernate.annotations.Type(type = "dev.langchain4j.store.embedding.hibernate.pgvector.PgVectorType")
@javax.persistence.Column(columnDefinition = "vector(3)")
private float[] embedding;
```

半精度列使用 `@Parameter(name = "storage", value = "halfvec")` 作为 `@Type` 的 `parameters` 属性，并配置 `@Column(columnDefinition = "halfvec(3)")`。查询会根据实体映射使用对应的 PostgreSQL 运算符和类型，可对接已有的 halfvec 向量索引。

在应用的 JPA 上下文调用 `new HibernatePgVectorSearch<>(Document.class, "embedding").search(entityManager, vector, Metric.COSINE, 0.8, 10, restriction)`。其中 `vector` 是 LangChain4j 的 `Embedding`，`restriction` 可传 JPA Criteria 条件或 `null`。实体写入与更新继续使用 JPA。支持余弦、L2、平方 L2、L1 和内积。Boot 2 项目可以设置 `postgresql.version=42.7.14` 对齐已经通过验证的 JDBC 驱动。

## HibernateEmbeddingStore 的 PostgreSQL 路线（JDK 8）

导入兼容 BOM 后添加原组件坐标 `cn.iocoder.langchain4j:langchain4j-hibernate`。使用 Spring Boot 2 的 `DataSource` 或 PostgreSQL JDBC URL 建立动态存储，建表和索引由指定的选项控制。

```java
HibernateEmbeddingStore<EmbeddingEntity> store = HibernateEmbeddingStore.dynamicDatasourceBuilder()
        .dataSource(dataSource)
        .databaseKind(DatabaseKind.POSTGRESQL)
        .table("document_vectors").dimension(384).createTable(true)
        .createIndex(true).indexType("hnsw").build();
```

用标准 `EmbeddingStore<TextSegment>` 接口批量写入并按元数据和最低分检索或删除。动态表支持 UUID、JSONB 元数据与 float32 向量。应用退出时关闭动态创建的存储。

已有 Hibernate 5 实体可以用 `HibernateEmbeddingStore.builder(Document.class).sessionFactory(sessionFactory).build()`。按上节配置 `PgVectorFunctions` 与 `PgVectorType`，用 `@EmbeddingVector` 标明向量，`@EmbeddedText` 标明文本，`@UnmappedMetadata` 标明通用元数据；JSON 字符串字段可用 `@Type(type = "dev.langchain4j.store.embedding.hibernate.JsonbMetadataType")`。标注 `@MetadataAttribute` 的标量字段也可以参与过滤。类型化路线支持 halfvec，调用方负责自己提供的 SessionFactory 生命周期。JDK 8 制品支持 PostgreSQL，其他数据库方言和 Hibernate NoSQL 的 HQL 兼容仍在适配。默认构建共 117 个模块。

## Hibernate 5 / Milvus（JDK 8）

导入兼容 BOM 后添加 `cn.iocoder.langchain4j:langchain4j-hibernate-milvus`。从 Spring Boot 2 的 `EntityManagerFactory` 获取 Hibernate 5 的 `SessionFactory`，再传入已连接的 Milvus Java SDK 客户端。

```java
SessionFactory factory = entityManagerFactory.unwrap(SessionFactory.class);
HibernateMilvusStore<Document> store =
        new HibernateMilvusStore<>(milvusClient, factory, Document.class, "documents", "vector", 384);
store.initializeSchema();
store.upsert(document);
EmbeddingSearchResult<Document> result = store.search(EmbeddingSearchRequest.builder()
        .queryEmbedding(reference).filter(metadataKey("language").isEqualTo("zh")).maxResults(10).build());
```

实体使用 `javax.persistence`，应用指定 String、UUID 或 Long 主键。每个属性映射到单列，字段名沿用 Hibernate 的列名，过滤键使用 Java 属性名。用 `@Type(type = "dev.langchain4j.store.embedding.hibernate.pgvector.PgVectorType")` 将向量属性映射为 `float[]`。支持 String、UUID、Long、Integer、Float、Double 和 Boolean 标量列，写入字段值不能为空；String 主键最长 512 UTF-8 字节，其他 String 字段最长 65,535 字节。标量过滤支持比较、集合成员及逻辑组合。

`initializeSchema()` 会创建或校验集合、创建缺失的 FLAT / COSINE 索引并加载集合。已有 schema 或相似度配置不匹配时会报错，保留已有数据。Milvus Lite 测试使用 `initializeSchema(false)`，由服务处理索引和加载请求。查询返回脱管实体，写入和删除使用 Milvus SDK，调用方管理 client 和 factory 的生命周期。原生 JDK 8 已验证 Milvus Lite 3.2.2 和 Milvus Standalone 2.6.27，包括独立 Boot 2.7.18 应用从安装包调用默认的 `initializeSchema()`。Hibernate 7 / NoSQL 的 JDBC、HQL 和托管事务使用现代 JDK 路线。默认构建共 118 个模块。

2026-11-19 从干净检出通过 118 个模块的 `clean install`。单测报告共 11,114 项，零失败、零错误，118 项按条件跳过。实际通过的 10,996 项中，9,844 项使用原生 JDK 8，1,152 项使用 JDK 17 的上游模拟服务。外部服务集成按组件执行。完整构建所需的 Spring AI 与 AgentScope 前置依赖见 [构建说明](COMPATIBILITY.md#build-and-consume)。

Docling 通过 32 项原生 JDK 8 单测及独立 Boot 2.7.18 安装包测试。使用同一组安装包连接真实 Docling Serve v1.35.0，本地 5 项集成用例全部通过，覆盖 PDF、DOCX、自定义提取、异步转换与分块。

Shell 模块补齐 Windows / Java 8 的子进程超时清理。Windows Server 2022 与 Corretto 8u514 上的 17 项 Windows 专属测试全部通过，包含超时、部分输出和工作目录。另用独立 Boot 2.7.18 应用验证已安装的 Shell 模块与 JNA 依赖。[Windows CI](https://github.com/yudaocode/yudao-langchain4j/actions/runs/44442408658)

---

# LangChain4j: idiomatic, open-source Java library for building LLM-powered applications on the JVM

[![Build Status](https://img.shields.io/github/actions/workflow/status/langchain4j/langchain4j/main.yaml?branch=main&style=for-the-badge&label=CI%20BUILD&logo=github)](https://github.com/langchain4j/langchain4j/actions/workflows/main.yaml)
[![Nightly Build](https://img.shields.io/github/actions/workflow/status/langchain4j/langchain4j/nightly_jdk17.yaml?branch=main&style=for-the-badge&label=NIGHTLY%20BUILD&logo=github)](https://github.com/langchain4j/langchain4j/actions/workflows/nightly_jdk17.yaml)
[![CODACY](https://img.shields.io/badge/Codacy-Dashboard-blue?style=for-the-badge&logo=codacy)](https://app.codacy.com/gh/langchain4j/langchain4j/dashboard)

[![Discord](https://img.shields.io/discord/1156626270772269217?logo=discord&style=for-the-badge)](https://discord.gg/JzTFvyjG6R)
[![BlueSky](https://img.shields.io/badge/@langchain4j-follow-blue?logo=bluesky&style=for-the-badge)](https://bsky.app/profile/langchain4j.dev)
[![X](https://img.shields.io/badge/@langchain4j-follow-blue?logo=x&style=for-the-badge)](https://x.com/langchain4j)
[![Maven Version](https://img.shields.io/maven-central/v/dev.langchain4j/langchain4j?logo=apachemaven&style=for-the-badge)](https://search.maven.org/#search|gav|1|g:"dev.langchain4j"%20AND%20a:"langchain4j")


## Introduction

Welcome!

The goal of LangChain4j is to simplify integrating LLMs into Java applications.

Here's how:
1. **Unified APIs:**
   LLM providers (like OpenAI or Google Vertex AI) and embedding (vector) stores (such as Pinecone or Milvus)
   use proprietary APIs. LangChain4j offers a unified API to avoid the need for learning and implementing specific APIs for each of them.
   To experiment with different LLMs or embedding stores, you can easily switch between them without the need to rewrite your code.
   LangChain4j currently supports [20+ popular LLM providers](https://docs.langchain4j.dev/integrations/language-models/)
   and [30+ embedding stores](https://docs.langchain4j.dev/integrations/embedding-stores/).
2. **Comprehensive Toolbox:**
   Since early 2023, the community has been building numerous LLM-powered applications,
   identifying common abstractions, patterns, and techniques. LangChain4j has refined these into practical code.
   Our toolbox includes tools ranging from low-level prompt templating, chat memory management, and function calling
   to high-level patterns like Agents and RAG.
   For each abstraction, we provide an interface along with multiple ready-to-use implementations based on common techniques.
   Whether you're building a chatbot or developing a RAG with a complete pipeline from data ingestion to retrieval,
   LangChain4j offers a wide variety of options.
3. **Numerous Examples:**
   These [examples](https://github.com/langchain4j/langchain4j-examples) showcase how to begin creating various LLM-powered applications,
   providing inspiration and enabling you to start building quickly.

LangChain4j began development in early 2023 amid the ChatGPT hype.
We noticed a lack of Java counterparts to the numerous Python and JavaScript LLM libraries and frameworks,
and we had to fix that!

**Despite the name, LangChain4j is not a Java port of LangChain (Python) — it is built for Java, not ported to it.**
It is an idiomatic Java library designed from the ground up around Java conventions:
type safety, POJOs, annotations, interfaces, dependency injection, fluent APIs, and first-class integrations with Quarkus, Spring Boot, Helidon, and Micronaut.
Its API, internals, and release cycle are independent of the Python LangChain project.

We actively monitor community developments, aiming to quickly incorporate new techniques and integrations,
ensuring you stay up-to-date.
The library is under active development. While some features are still being worked on,
the core functionality is in place, allowing you to start building LLM-powered apps now!


## Documentation
Documentation can be found [here](https://docs.langchain4j.dev).

The documentation chatbot (experimental) can be found [here](https://chat.langchain4j.dev/).


## Getting Started
Getting started guide can be found [here](https://docs.langchain4j.dev/get-started).


## Code Examples
Please see examples of how LangChain4j can be used in [langchain4j-examples](https://github.com/langchain4j/langchain4j-examples) repo:
- [Examples in plain Java](https://github.com/langchain4j/langchain4j-examples/tree/main/other-examples/src/main/java)
- [Examples with Quarkus](https://github.com/quarkiverse/quarkus-langchain4j/tree/main/samples) (uses [quarkus-langchain4j](https://github.com/quarkiverse/quarkus-langchain4j) dependency)
- [Example with Spring Boot](https://github.com/langchain4j/langchain4j-examples/tree/main/spring-boot-example/src/main/java/dev/langchain4j/example)
- [Examples with Helidon](https://github.com/helidon-io/helidon-examples/tree/helidon-4.x/examples/integrations/langchain4j) (uses [io.helidon.integrations.langchain4j](https://mvnrepository.com/artifact/io.helidon.integrations.langchain4j) dependency)
- [Examples with Micronaut](https://github.com/micronaut-projects/micronaut-langchain4j/tree/0.3.x/doc-examples/example-openai-java) (uses [micronaut-langchain4j](https://micronaut-projects.github.io/micronaut-langchain4j/latest/guide/) dependency)

## Useful Materials
Useful materials can be found [here](https://docs.langchain4j.dev/useful-materials).


## Get Help
Please use [Discord](https://discord.gg/JzTFvyjG6R) or [GitHub discussions](https://github.com/langchain4j/langchain4j/discussions)
to get help.


## Request Features
Please let us know what features you need by [opening an issue](https://github.com/langchain4j/langchain4j/issues/new/choose).


## Contribute
Contribution guidelines can be found [here](https://github.com/langchain4j/langchain4j/blob/main/CONTRIBUTING.md).
