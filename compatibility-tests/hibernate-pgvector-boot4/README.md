# Boot 4 的 Hibernate pgvector 复验

此独立 Maven 工程从当前分支安装后的 `dev.langchain4j:langchain4j-hibernate` JAR 启动 Boot 4.1.2。测试使用 Boot 的 JPA 自动配置和默认 Hibernate ORM `7.4.13.Final`。请使用专用测试数据库，Hibernate 会创建并删除 `lc4j_hibernate_pgvector_boot4` 表。

```bash
docker run --rm -d --name lc4j-hibernate-pgvector-boot4 \
  -p 127.0.0.1::5432 \
  -e POSTGRES_DB=fixture -e POSTGRES_USER=fixture -e POSTGRES_PASSWORD=fixture_local \
  pgvector/pgvector:pg17

docker exec lc4j-hibernate-pgvector-boot4 pg_isready -h 127.0.0.1 -U fixture -d fixture
docker exec lc4j-hibernate-pgvector-boot4 psql -X -v ON_ERROR_STOP=1 \
  -U fixture -d fixture -c 'CREATE EXTENSION IF NOT EXISTS vector'
PGVECTOR_ADDRESS=$(docker port lc4j-hibernate-pgvector-boot4 5432/tcp)
```

PostgreSQL 就绪后，以 JDK 17 启动 Maven，在仓库根目录安装当前分支的制品。将 `TEST_JAVA_HOME` 指向目标 JDK，按实际组合设置 `TEST_JAVA_SPEC` 为 `17` 或 `25`。

```bash
mvn -pl :langchain4j-hibernate -am \
  -DskipTests -DskipITs -Dmaven.javadoc.skip=true -Dmaven.deploy.skip=true install
mvn -f compatibility-tests/hibernate-pgvector-boot4/pom.xml clean test \
  -Dpostgres.address="$PGVECTOR_ADDRESS" \
  -Dcompat.test.jvm="$TEST_JAVA_HOME/bin/java" \
  -Dcompat.java.version="$TEST_JAVA_SPEC"
```

回归包括带参标量 HQL、带过滤的 `cosine_distance` HQL、`HibernateEmbeddingStore` 检索、实体提交和 flush 前后回滚。flush 后回滚用例先在同一事务的 JDBC 连接查到 1 行，回滚结束后用 `DriverManager` 新建直连查到 0 行，也通过 HQL 核对结果为空。
