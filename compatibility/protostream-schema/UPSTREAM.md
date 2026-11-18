# ProtoStream 6.0.12 schema builder

Upstream https://github.com/infinispan/protostream/tree/62711413e0fc7eb8a299d0e2c4ddb8efa355c17f.
The source archive SHA-256 is 95072ae966faff646595b228b424219b4299ac2d63d3c6d97d1e187b8a388f3f.
The Java 8 artifact contains the schema builder only. The Java 8 Hot Rod client keeps its original ProtoStream 4.4.4 marshaller. The schema validation preserves upstream exception types and uses a local helper with no JBoss Logging dependency. The internal sparse bit set is kept in the schema package.
