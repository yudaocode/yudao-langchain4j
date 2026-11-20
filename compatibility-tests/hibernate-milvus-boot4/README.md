# Boot 4 的 Hibernate Milvus 复验

独立消费者使用当前分支的 `dev.langchain4j:langchain4j-hibernate-milvus`，通过 Boot 4 的 JPA 自动配置建立 Hikari 数据源、Hibernate EntityManagerFactory 和 `JpaTransactionManager`，连接真实 Milvus Standalone。请使用专用测试容器，用例会创建并删除 `lc4j_hibernate_milvus_boot4` 集合。

```bash
docker run --rm -d --name lc4j-hibernate-milvus-boot4 \
  -p 127.0.0.1::19530 -p 127.0.0.1::9091 \
  -e DEPLOY_MODE=STANDALONE -e MILVUS_MODE=standalone \
  -e ETCD_USE_EMBED=true -e COMMON_STORAGETYPE=local \
  milvusdb/milvus:v2.6.27 milvus run standalone
MILVUS_ADDRESS=$(docker port lc4j-hibernate-milvus-boot4 19530/tcp)
MILVUS_HEALTH=$(docker port lc4j-hibernate-milvus-boot4 9091/tcp)
curl --fail "http://$MILVUS_HEALTH/healthz"
```

健康检查返回 OK 后，用 JDK 17 启动 Maven，在仓库根目录安装当前分支的模块。将 `TEST_JAVA_HOME` 指向目标 JDK 安装目录，`TEST_JAVA_SPEC` 对应设置为 `17` 或 `25`。

```bash
mvn -pl :langchain4j-hibernate-milvus -am \
  -DskipTests -DskipITs -Dmaven.javadoc.skip=true -Dmaven.deploy.skip=true install

mvn -f compatibility-tests/hibernate-milvus-boot4/pom.xml clean test \
  -Dmilvus.address="$MILVUS_ADDRESS" \
  -Dcompat.test.jvm="$TEST_JAVA_HOME/bin/java" \
  -Dcompat.java.version="$TEST_JAVA_SPEC"
```

Boot 4.1.2 默认使用 Hibernate ORM `7.4.13.Final`。测试同时验证了与 LangChain4j 构建版本一致的 `7.4.15.Final`，复验时增加 `-Dhibernate.version=7.4.15.Final`。Milvus JDBC 驱动使用 Hibernate NoSQL `1.0.0.Alpha4`。

两种 ORM 版本在 JDK 17、25 均通过标量 HQL、带过滤的余弦距离 HQL、`HibernateEmbeddingStore` 的向量检索和托管实体写入。事务用例分别校验 flush 前后的回滚结果。flush 前，Hibernate 丢弃待写实体。flush 后，已发送到 Milvus 的写入仍可查询。驱动的 `supportsTransactions()` 返回 `false`，`rollback()` 为空操作。[Hibernate NoSQL 驱动源码](https://github.com/hibernate/hibernate-nosql/blob/1.0.0.Alpha4/hibernate-milvus/src/main/java/org/hibernate/milvus/jdbc/internal/MilvusConnection.java#L1196-L1220)
