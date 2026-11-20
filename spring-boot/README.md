# LangChain4j Spring Boot 启动器

Boot 3 和 Boot 4 的 starter 版本均为 `1.25.0-beta35-preview-20261120`。导入芋道 [LangChain4j BOM `1.25.0-preview-20261120`](https://central.sonatype.com/artifact/cn.iocoder.langchain4j/langchain4j-bom/1.25.0-preview-20261120) 后选择对应的 starter。

`main` 提供下列启动器。两者使用 Java 17 字节码，Java 包名沿用上游。

| Spring Boot | Maven 依赖 | 开发版本 |
| --- | --- | --- |
| 3.5.x | `cn.iocoder.langchain4j:langchain4j-spring-boot-starter` | `1.25.0-beta35-SNAPSHOT` |
| 4.1.x / 4.2.x | `cn.iocoder.langchain4j:langchain4j-spring-boot4-starter` | `1.25.0-beta35-SNAPSHOT` |

在 Boot 3 应用里导入发布版 BOM，并引入 starter。Boot 4 应用将 starter 的 artifactId 换成上表中的 `langchain4j-spring-boot4-starter`。

```xml
<dependencyManagement>
    <dependencies>
        <dependency>
            <groupId>cn.iocoder.langchain4j</groupId>
            <artifactId>langchain4j-bom</artifactId>
            <version>1.25.0-preview-20261120</version>
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
</dependencies>
```

构建开发版本时，在仓库根目录用 JDK 17 安装核心与启动器。

```bash
mvn -pl :langchain4j-spring-boot-starter,:langchain4j-spring-boot4-starter \
  -am -DskipTests install
```

应用选择表中对应的启动器。模型提供方可按需配置，例如复验项目使用各自 Boot 版本的官方 OpenAI starter。如果应用已有相同 Boot 版本的官方 LangChain4j 基础 starter，先把它换成表中坐标，避免重复加载相同 Java 包。

普通 `@Bean` 工厂方法可以提供工具。返回类型应声明包含 `@Tool` 方法的具体类，以便 Spring 在启动时发现。例如 `@Bean Calculator calculator()`。在 classpath 上，包级可见的 `@AiService` 接口可声明默认方法。

## 复验

下面命令使用已安装的启动器和核心，实际启动 Boot，用本地 HTTP 夹具验证工具调用、默认方法、RAG 和 SSE。

```bash
mvn -f spring-boot/compatibility-tests/consumer/pom.xml clean test \
  -Dcompat.test.jvm=/path/to/jdk17/bin/java -Dexpected.boot=3.5.15 -Dexpected.jdk=17

mvn -f spring-boot/compatibility-tests/consumer/pom.xml clean test \
  -Dcompat.test.jvm=/path/to/jdk17/bin/java -Dexpected.boot=4.1.2 -Dexpected.jdk=17 \
  -Dboot.version=4.1.2 -Dstarter.artifactId=langchain4j-spring-boot4-starter \
  -Dopenai.starter.artifactId=langchain4j-open-ai-spring-boot4-starter

mvn -f spring-boot/compatibility-tests/consumer/pom-boot2.xml clean test \
  -Dcompat.test.jvm=/path/to/jdk8/bin/java -Dexpected.boot=2.7.18 -Dexpected.jdk=1.8
```

复验 JDK 25 时将前两条命令的 Java 路径和 `expected.jdk` 值换为对应版本。Boot 2 命令使用 `main-jdk8` 路线对应的已发布坐标，复用同一份测试源码。
