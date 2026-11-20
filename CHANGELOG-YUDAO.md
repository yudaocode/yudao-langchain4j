# 芋道兼容版变更记录

## 1.25.0-preview-20261120

- 将现代模块与 BOM 的 Maven 坐标统一为 `cn.iocoder.langchain4j`，沿用 `dev.langchain4j` Java 包名。
- 纳入此前的异步异常回传与 Infinispan 16.3.1 异步向量和记忆适配。
- Boot 3.5.15 与 4.2.0 各在 JDK 17、25 验证安装后的 starter、工具调用和 SSE。

适用于 Boot 3.5.15／4.2.0 和 JDK 17／25。beta 模块使用 1.25.0-beta35-preview-20261120。

上游提交 `c5f5d093f`，兼容分支源码 `9f1f26a47`。
