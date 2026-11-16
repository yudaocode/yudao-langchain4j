# Standalone Boot 2 consumer

This fixture inherits the **official Spring Boot 2.7.18 parent**, imports the fork BOM, and consumes locally installed jars. It does not inherit the compatibility build parent or the source transformer. Run after installing the supported reactor and Spring AI's two small Java 8 compatibility artifacts.

Build with JDK 17 and pass `-Dcompat.test.jvm=/absolute/path/to/jdk8/bin/java`. `mvn -f compatibility-tests/boot2-consumer/pom.xml verify` checks actual Boot discovery, properties, tools, RAG and a default interface method on the requested VM. This is a local protocol fixture, not an external provider test.

The three version properties in this consumer are required when inheriting Boot's parent: an imported BOM cannot override inherited direct dependency-management entries. This fixture verifies the exact README instructions, including runtime version assertions and actual SSE, rather than assuming BOM import alone wins.
