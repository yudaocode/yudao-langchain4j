# JDK 8 应用接入 GraalVM 独立执行

Boot 2 应用使用 `cn.iocoder.langchain4j:langchain4j-code-execution-engine-graalvm-process:1.24.0-beta34-jdk8-SNAPSHOT`。该版本也由本分支的 SNAPSHOT BOM 管理。

从本仓库的 `main` 分支构建现代 worker。

```bash
module=code-execution-engines/langchain4j-code-execution-engine-graalvm-polyglot
mvn -pl ":langchain4j-code-execution-engine-graalvm-polyglot" -am -DskipTests install
mvn -f "$module/pom.xml" dependency:copy-dependencies -DincludeScope=runtime \
  "-DoutputDirectory=$PWD/$module/target/worker-lib"
```

使用 worker 模块生成的 JAR 和 `worker-lib` 目录。JDK 8 应用通过原有的 `CodeExecutionEngine` 接口调用。

```java
import dev.langchain4j.code.CodeExecutionEngine;
import dev.langchain4j.code.graalvm.process.ForkedGraalVmExecutionEngine;
import dev.langchain4j.code.graalvm.process.ForkedGraalVmExecutionEngine.Language;
import java.nio.file.Paths;
import java.time.Duration;

CodeExecutionEngine engine = new ForkedGraalVmExecutionEngine(
        Paths.get("/opt/jdk-17/bin/java"),
        Paths.get("/opt/worker/graalvm-worker.jar"),
        Paths.get("/opt/worker/worker-lib"),
        Language.JAVASCRIPT,
        Duration.ofSeconds(15),
        Paths.get("/var/cache/langchain4j-graalvm"));
String result = engine.execute("6 * 7");
```

语言可选 `JAVASCRIPT` 或 `PYTHON`。应用运行账户应能写入指定缓存目录，用于提取 GraalVM 语言资源。也可省略最后一个参数，使用应用账户的默认缓存目录。

每次调用使用一个独立子 JVM，通过标准输入输出交换数据。输入上限 64 KiB，回应上限 1 MiB，worker 最大 Java 堆为 512 MiB。到达指定时限或调用线程被中断时，客户端终止该子进程。

JavaScript 与 Python 沿用各自上游的 GraalVM 策略和 HostAccess 设置。操作系统级限制由运行账户及容器配置提供。

## 复验

用 JDK 17 运行 Maven。在 `main-jdk8` 根目录安装客户端，再指定运行测试的 JDK 8 和独立 worker 的 JDK。

```bash
mvn -pl :langchain4j-code-execution-engine-graalvm-process -am -DskipTests install
mvn -f compatibility-tests/graalvm-process/pom.xml clean test \
  -Dcompat.test.jvm=/path/to/jdk8/bin/java \
  -Dgraal.boot=2.7.18 -Dgraal.jdk=1.8 \
  -Dgraal.worker.java=/path/to/jdk17/bin/java \
  -Dgraal.worker.jar=/opt/worker/graalvm-worker.jar \
  -Dgraal.worker.libs=/opt/worker/worker-lib
```

这套验收使用 POSIX 进程探针检查超时和中断后 JVM 是否退出。JDK 25 worker 可通过 `graal.worker.java` 切换。
