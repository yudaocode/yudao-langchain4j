## Six document loaders on Java 8

Add only the required `cn.iocoder.langchain4j` loader dependency; the compatibility BOM manages its beta version. The loader implementations and public Java packages remain upstream originals.

| Artifact suffix (`langchain4j-document-loader-…`) | SDK retained / selected | Local acceptance |
| --- | --- | --- |
| `amazon-s3` | AWS S3 2.57.12 | Actual signed SDK GET against loopback HTTP |
| `azure-storage-blob` | Azure Blob 12.36.0, Azure Core 1.60.0 | Original four Azurite integration cases |
| `google-cloud-storage` | Google Storage 2.75.0 / libraries BOM 26.90.0 | Actual SDK metadata/media GET, source/parser pipeline |
| `tencent-cos` | COS 5.6.283 | Actual signed SDK GET with length validation |
| `playwright` | Playwright 1.57.0 | Fresh headless Chrome loads a Unicode page |
| `selenium` | Selenium 4.13.0 | Fresh headless Chrome and matching driver |

Selenium 4.13.0 is the last release supporting Java 8 ([official release note](https://www.selenium.dev/blog/2023/selenium-4-13-0-released/)); the Java 8 branch does not use upstream's Java-11 Selenium 4.33. Other SDK versions above remain the upstream source baseline. High SDK versions do not imply all cloud-service features are accepted.

```xml
<dependency>
    <groupId>cn.iocoder.langchain4j</groupId>
    <artifactId>langchain4j-document-loader-amazon-s3</artifactId>
</dependency>
```

For example, use `new AmazonS3DocumentLoader(s3Client).loadDocument(bucket, key, parser)` with your configured SDK client and parser. Credentials, service endpoints and resource permissions remain application configuration. The local tests use dummy credentials; no real cloud account was accepted. Browser libraries also need a browser binary and, for Selenium, a compatible driver; neither is bundled as a Java dependency.

An independent Boot 2 parent consumer is provided at `compatibility-tests/boot2-document-loaders`. Its POM is the complete example of the required Boot-managed version overrides; a compatibility BOM import cannot override an inherited direct dependency-management entry. Keep Boot 2 / Spring 5 and SLF4J 1.7; do not mix in Boot 3/4 starters or SLF4J 2.

Build all default modules first, using JDK 17 build tools and the native Java 8 test VM as documented above. The standalone consumer then uses the installed jars:

```sh
PLAYWRIGHT_SKIP_BROWSER_DOWNLOAD=1 ./mvnw \
  -f compatibility-tests/boot2-document-loaders/pom.xml verify \
  -Dcompat.test.jvm="$JAVA8_HOME/bin/java" \
  -Dcompat.browser.tests=true \
  -Dcompat.chrome.binary="/absolute/path/to/test-chrome" \
  -Dcompat.chrome.driver="/absolute/path/to/matching-chromedriver"
```

Docker is required for Azurite. Both browser tests use fresh temporary profiles. Omitting `compat.browser.tests=true` explicitly skips those two cases. The original eight Playwright browser contracts now launch the actual SDK directly in a fresh local Chrome profile, removing the Java-11-only Docker test helper; all eight passed on Java 8. Selenium Docker browser and real cloud-provider suites still require their documented environments and are not asserted by the default unit gate.

The runtime bytecode and API scans are separate: references in guarded Guava / shaded Netty modern-JDK implementations and Google logging adapters remain visible. Target-VM tests exercise CRC32C, spilled files, futures, unsigned comparison, buffer refcounts and the SLF4J 1.7 logging fallback; this is not a claim that every optional branch of those SDKs was tested.

## Validation checkpoints

The complete76-module clean install unit gate passed8887 executions, zero failures/errors,102 conditional skips:7705 executions on native Java8 and1182 upstream modern mock-server executions on Java17. ITs are separate from this command. After replacing only the Playwright test facility, its unit1 and browser9 cases passed again on native8; no production loader implementation was changed.

Six-loader component acceptance passed24 native8 cases with zero skips (15unit,3 protocol,4 Azurite,2 browser). The independent Boot2 parent consumer passed12 native8 cases with zero skips, including context/version checks and guarded SDK fallback paths. These are distinct runs, not one combined full-suite count.

Runtime scan including standalone consumers:78 classpath files,587 jars,175864 effective base classes, none above Java8 major52. The static API audit keeps60 third-party findings rather than deleting guarded modern-JDK implementations. No live S3/Azure/GCS/COS account, all browser engines or every SDK feature is claimed.
