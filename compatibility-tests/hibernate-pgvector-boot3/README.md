# Boot 3 的 Hibernate pgvector 复验

本独立工程从已安装的 `dev.langchain4j:langchain4j-hibernate` JAR 启动 Spring Boot 3.5.15。Boot 管理的 Hibernate ORM 保持 `6.6.53.Final`。Boot 3.5.15 的 BOM 未管理 `hibernate-vector`，因此采用 Boot Maven parent 的应用显式声明同版向量扩展：

```xml
<dependency>
    <groupId>org.hibernate.orm</groupId>
    <artifactId>hibernate-vector</artifactId>
    <version>${hibernate.version}</version>
</dependency>
```

通过 `HibernateEmbeddingStore.builder(EntityType.class)` 使用应用的 SessionFactory，将结果声明为 `EmbeddingStore<TextSegment>`。测试覆盖已映射向量实体、HQL 标量和向量查询、`EmbeddingStore.search` 和 `removeAll(ids)`。同一用例通过存储接口写入 JSON 元数据，组合列与 JSON 条件进行向量检索和 `removeAll(filter)`。覆盖数值比较、集合条件、UUID、含通配字符的文本、缺失字段和过滤后的 topK。Hibernate 6.6 使用 PostgreSQL JSON 路径提取实现通用过滤接口。使用公开的 Hibernate `Restriction` 重载时，运行环境仍需 Hibernate 7。

已在 PostgreSQL 16 和 17 上验证。按 [Boot 4 复验](../hibernate-pgvector-boot4/README.md) 的容器命令启动专用 PostgreSQL pgvector，并启用 `vector` 扩展。记录映射到本机的 `host:port` 为 `PGVECTOR_ADDRESS`。测试使用数据库 `fixture` 和表 `lc4j_hibernate_pgvector_boot3`，Hibernate 会创建并删除该表。

在当前分支仓库根目录以 JDK 17 启动 Maven。设置 `TEST_JAVA_HOME` 和对应的 `TEST_JAVA_SPEC`（`17` 或 `25`）。

```bash
mvn -pl :langchain4j-hibernate -am \
  -DskipTests -DskipITs -Dmaven.javadoc.skip=true -Dmaven.deploy.skip=true install

mvn -f compatibility-tests/hibernate-pgvector-boot3/pom.xml clean test \
  -Dpostgres.address="$PGVECTOR_ADDRESS" \
  -Dcompat.test.jvm="$TEST_JAVA_HOME/bin/java" \
  -Dcompat.java.version="$TEST_JAVA_SPEC"
```

flush 后回滚用例在当前 Hibernate 的 JDBC 连接查到 1 行，事务结束后以 `DriverManager` 新建 PostgreSQL 直连查到 0 行，随后用 HQL 再次核对。
