# GraalVM 独立 worker

`ForkedGraalVmWorker` 是一次只处理一个请求的标准输入输出入口。其语言和结果格式沿用本模块的 `GraalVmJavaScriptExecutionEngine`、`GraalVmPythonExecutionEngine`。使用本仓库 Java 8 客户端即可启动它。

本模块以 GraalVM Polyglot 24.1.1 为基线，经 JDK 17、25 复验。部署保留 worker JAR 与 Maven 运行依赖，语言资源缓存路径由客户端配置。[Java 8 接入和复验命令](https://github.com/yudaocode/yudao-langchain4j/tree/main-jdk8/compatibility-tests/graalvm-process)
