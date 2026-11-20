# LangChain4j Jackson 2／3 JSON 互读

两套 POM 使用同一个 `JacksonWireTest.java`。它先用已安装的 LangChain4j 制品写出三类 JSON，再由另一套 Jackson 在真实 Spring Boot 上下文中读取。

- 工具参数走 `DefaultToolExecutor`，覆盖嵌套参数、中文、日期与时间、空值、布尔值和大于 JavaScript 安全整数范围的 ID。
- 消息走 `ChatMessageSerializer` 与 `ChatMessageDeserializer`，包含文本、内联图片、工具调用参数和工具结果。
- 结构化输出走 `ServiceOutputParser`，检查泛型列表中的订单、枚举和精确 `BigDecimal`。

## Boot 3 使用 Jackson 3.3.0

导入 Boot 3.5.15 的 BOM 并引入 `langchain4j-jackson3` 时，在应用的 `dependencyManagement` 明确声明注解包版本。Boot 3 默认的 2.21 缺少 Jackson 3.3.0 需要的 `JsonApplyView`。只调整此依赖即可复验，其他 Jackson 依赖沿用所用框架的管理。

```xml
<dependency>
    <groupId>com.fasterxml.jackson.core</groupId>
    <artifactId>jackson-annotations</artifactId>
    <version>2.23</version>
</dependency>
```

`verify.sh` 通过 `boot3-jackson3-annotations` profile 为 Boot 3 探针应用这条覆盖。Boot 4 探针使用自身 BOM。

## 复验

用 JDK 17 在 `main` 和 `main-jdk8` 分别安装所需 SNAPSHOT 制品。现代分支需要 `langchain4j`、`langchain4j-jackson3` 及其依赖。Java 8 分支需要 Java 8 版 `langchain4j` 及其依赖。

```bash
export JDK8_HOME=/path/to/jdk8
export JDK17_HOME=/path/to/jdk17
export JDK25_HOME=/path/to/jdk25
bash compatibility-tests/jackson-wire/verify.sh
```

脚本使用 Maven 构建测试，并以每组指定的 Java 运行 Spring Boot 和 LangChain4j。每次执行会在 `target/wire/matrix.*` 输出独立的 JSON 文件、日志和测试报告。验证比较解析后的内容和类型，允许 JSON 对象键顺序、数字格式等表达差异。
