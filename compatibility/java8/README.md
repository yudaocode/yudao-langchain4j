# Java 8 build and audit tools

`Java8SourceGenerator` and `Java8RecordMetadata` execute on build JDK 17. Generated sources/classes remain under each module's target directory.

`audit-runtime-bytecode.py [repository-root] [report.json]` scans runtime jars plus current-module jars after:

```sh
./mvnw dependency:build-classpath -DincludeScope=runtime -Dmdep.outputFile=target/java8-runtime-classpath.txt
python3 compatibility/java8/audit-runtime-bytecode.py
```

It rejects any effective base class above major 52 and fails on missing classpath evidence. Java 8 ignores module-info and multi-release overlays, so those are excluded. This is a bytecode gate, not proof of every API path.

`Java8RuntimeApiAudit` is a separate **report-only** static member-reference audit. Compile it with ASM 9.7 and `javac --release 8`, then run with an actual Java 8 executable, the same ASM jar and the complete audited jar classpath. Its argument is a UTF-8 file containing one absolute jar path per line. It resolves JDK members against that Java 8 boot classpath and logging calls against the supplied SLF4J jars. It handles signature-polymorphic MethodHandle calls and Object methods on interfaces, and skips duplicate vendor classes shadowed by bootstrap classes.

The current closure has optional JDK 9 Flow adapter references inside Reactive Streams/Reactor; generated LangChain4j uses `dev.langchain4j.internal.compat.FlowAdapters` instead. SLF4J1.7's legacy binder getSingleton probes explicitly catch NoSuchMethodError and fall back to SINGLETON. Such findings must be reviewed with bytecode/source and active runtime tests; raw findings are preserved and are not silently filtered into a zero-finding report. The audit is not an interprocedural proof of reachability and does not assert that every optional vendor feature runs on Java 8.
