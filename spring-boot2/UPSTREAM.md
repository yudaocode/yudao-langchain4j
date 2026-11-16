Official langchain4j/langchain4j-spring main e00111bdccae9f091f0cc3a2fc4b56be0c273f99 (1.24.0-beta34-SNAPSHOT).
Preserved main sources/resources of the base and OpenAI starters. Tests are being added/ported separately; upstream tests not copied must not be counted as passed.
Boot2 transport uses the Apache HTTP5 client, with the same named HttpClientBuilder override beans. Boot3 RestClient settings/customizers and transport-specific streaming executors are not available; use the core ExecutorProvider/Spring task executor integration for offloaded AI work.
Immutable property types use explicit Boot2 ConstructorBinding and generated canonical parameter metadata.

Selected official PropertyBindingTest and IgnoredPropertyWarningTest are retained for Java 8 execution; the complete upstream test suite is not imported.
