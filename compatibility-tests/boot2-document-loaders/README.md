# Boot 2 document-loader consumer

This standalone fixture inherits the official Spring Boot2.7.18 parent and consumes installed compatibility jars. It is outside the default SDK reactor and is not deployed.

Follow [document-loader usage](../../document-loaders/README-JAVA8.md). This POM contains the full Boot-parent overrides: Jackson2.21.4, Netty4.1.140.Final, Selenium4.13.0, Gson2.14.0, OkHttp4.12.1, Kotlin2.2.21, Reactor Core3.7.19 and Reactor Netty1.2.18, alongside the existing HTTP5/Logback overrides. These remain Java8-compatible lines; Boot2 and SLF4J1.7 are retained.

Its12 target-VM cases cover actual S3/GCS/COS local SDK requests, original Azurite cases, fresh Chrome through both loaders, Boot context/loaded versions and Google/Guava/shaded-Netty fallback paths. Docker plus a configured Chrome binary/matching driver are required to run every case. Browser cases are opt-in; the usage command enables them explicitly. It does not call real cloud accounts.
