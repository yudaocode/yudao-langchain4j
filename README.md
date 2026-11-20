# 芋道 LangChain4j 兼容版

> 版本命名：保留当前源码基线的官方版本号；共用现代制品不加 JDK 后缀，隔离维护的兼容路线添加 `jdk8` / `jdk17` / `jdk25`。SNAPSHOT 尚未发布 Maven Central。JDK 路线后缀不等于最低字节码版本，框架依赖仍按 Boot 版本选择。
> 完整版本规则与预览命名见 [VERSIONING.md](VERSIONING.md)。


本仓库基于官方 LangChain4j 新版源码，为仍使用 **JDK 8 / Spring Boot 2.x** 的项目提供兼容分支，尽量保留新版本功能。上游作者、许可证和 Java 包名保留；以下说明之后继续保留官方 README。

| 分支 | 运行环境 | 使用方式 |
| --- | --- | --- |
| `main` | JDK 17+；Spring Boot 3.5 / 4.1 | [芋道现代启动器](spring-boot/README.md)提供对应 Boot 制品 |
| `main-jdk8` | JDK 8；Spring Boot 2.7.18 / Spring 5.3 | 使用 `cn.iocoder.langchain4j` 兼容制品 |

GitHub：[yudaocode/yudao-langchain4j](https://github.com/yudaocode/yudao-langchain4j) · Gitee：[yudaocode/yudao-langchain4j](https://gitee.com/yudaocode/yudao-langchain4j)

Boot 3.5.15、Boot 4.1.2 的芋道启动器支持用普通 `@Bean` 工厂方法注册工具，声明返回类型为含 `@Tool` 的具体类。核心支持在 classpath 上调用包级可见 `@AiService` 接口的默认方法。两条现代路线在 JDK 17、25 各通过独立安装包测试，Boot 2.7.18 / JDK 8 使用同一份测试源码也通过。覆盖工具调用、RAG、默认方法和 SSE。见[坐标与复验命令](spring-boot/README.md)。

## JDK 8 / Boot 2 如何使用

Boot 2 / JDK 8 已发布芋道兼容版。导入 [`cn.iocoder.langchain4j:langchain4j-bom:1.24.0-jdk8-20261119`](https://central.sonatype.com/artifact/cn.iocoder.langchain4j/langchain4j-bom/1.24.0-jdk8-20261119)，stable 模块使用 `1.24.0-jdk8-20261119`，beta 模块使用 `1.24.0-beta34-jdk8-20261119`。BOM 管理两类坐标，Java 包名沿用 `dev.langchain4j`。

首批发布 75 个坐标，覆盖核心、Boot 2 starter、已纳入发行的适配器和 Easy RAG 依赖的量化 BGE 模型。其余预训练模型、暂缓的云模块和 Hibernate 仍可按源码说明构建。已发布范围以公开 BOM 为准。

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
            <version>1.24.0-jdk8-20261119</version>
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

## Jackson 2／3 JSON 互读

同一套工具参数、消息和结构化输出样例，覆盖 Boot 2.7.18／JDK 8 与 Boot 3.5.15、4.1.2 各在 JDK 17、25 上的双向读写。使用 Boot 3.5.15 BOM 与 Jackson 3.3.0 时，显式管理 `jackson-annotations:2.23`。见 [覆盖示例与复验命令](compatibility-tests/jackson-wire)。

## GraalVM 独立 worker

GraalVM Polyglot 模块提供独立 worker 入口，可与 Boot 2 / JDK 8 应用配套。实际执行使用 JDK 17 或 25，并复用模块的 JavaScript 和 Python 引擎。见 [worker 说明](code-execution-engines/langchain4j-code-execution-engine-graalvm-polyglot/README-PROCESS.md)。

支持范围、构建步骤与验证边界见 **`main-jdk8` 分支的 [COMPATIBILITY.md](https://github.com/yudaocode/yudao-langchain4j/blob/main-jdk8/COMPATIBILITY.md)**。BOM 仅列入已纳入兼容构建的模块；其余上游模块源码保留，不代表已经支持 Java 8。

## Hibernate Milvus（Boot 4）

Boot 4.1.2／JDK 17、25 已通过 Milvus 2.6.27 Standalone 的标量 HQL、带过滤的向量 HQL 和 `HibernateEmbeddingStore` 检索。Spring 的 JPA 事务边界可以提交 Hibernate 待写实体。flush 前回滚会丢弃待写实体，flush 后回滚仍可查到已写入 Milvus 的数据。当前 Hibernate NoSQL Milvus 驱动缺少数据库级回滚。[Docker 复验](compatibility-tests/hibernate-milvus-boot4)


## Hibernate pgvector（Boot 4）

Boot 4.1.2／JDK 17、25 已使用 Docker PostgreSQL 17 和 pgvector 0.8.6 验证标量 HQL、带过滤的余弦距离 HQL、`HibernateEmbeddingStore` 检索和 JPA 提交。显式 flush 后回滚，独立 PostgreSQL JDBC 连接确认对应记录为 0 行。[Docker 复验](compatibility-tests/hibernate-pgvector-boot4)


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
