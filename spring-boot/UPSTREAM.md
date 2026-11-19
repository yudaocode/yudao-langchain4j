# LangChain4j Spring Boot starter sources

The Java sources and Boot auto-configuration resources come from Apache-2.0 licensed [LangChain4j Spring](https://github.com/langchain4j/langchain4j-spring) artifacts. Sources shared by both upstream artifacts are kept in the parent source directory. Boot-specific auto-configuration class names and imports follow their respective upstream starters.

- `langchain4j-spring-boot-starter:1.24.0-beta34` sources JAR SHA-256 `aabc97678d5123737bc96fa9dbc74085b0a8c9376712e13ce53d86c694e9dc17`.
- `langchain4j-spring-boot4-starter:1.24.0-beta34` sources JAR SHA-256 `77be6e51d5e33d55a4973d5912b14791b813f4b9588ef6cdd78db5da3cfe19aa`.

Factory-method beans fall back to the declared Spring bean type without eager initialization. This allows an ordinary `@Bean` with a concrete return type to contribute its `@Tool` methods. Upstream source authorship and the repository's Apache-2.0 license are retained.
