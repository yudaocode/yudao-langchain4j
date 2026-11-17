# Local embedding models on Java8

The default compatibility reactor includes ten local model artifacts: the existing quantized MiniLM plus these nine additions.
Coordinates use `cn.iocoder.langchain4j` and the compatibility BOM manages `1.24.0-beta34-jdk8-SNAPSHOT`.
Select the model artifact you need; each includes its own tokenizer and ONNX model.
The ONNX Runtime1.23.2 and DJL0.40.0 backend versions are retained.

| Artifact | Verified native8 integration cases |
| --- | ---: |
| `langchain4j-embeddings-e5-small-v2` | 7 |
| `langchain4j-embeddings-bge-small-en` | 7 |
| `langchain4j-embeddings-bge-small-zh-v15` | 7 |
| `langchain4j-embeddings-bge-small-zh-v15-q` | 5 |
| `langchain4j-embeddings-bge-small-en-v15` | 6 |
| `langchain4j-embeddings-bge-small-en-q` | 6 |
| `langchain4j-embeddings-all-minilm-l6-v2` | 8 |
| `langchain4j-embeddings-bge-small-en-v15-q` | 5 |
| `langchain4j-embeddings-e5-small-v2-q` | 7 |

```xml
<dependency>
    <groupId>cn.iocoder.langchain4j</groupId>
    <artifactId>langchain4j-embeddings-bge-small-zh-v15-q</artifactId>
</dependency>
```

```java
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.embedding.onnx.bgesmallzhv15q.BgeSmallZhV15QuantizedEmbeddingModel;

EmbeddingModel model = new BgeSmallZhV15QuantizedEmbeddingModel();
float[] vector = model.embed("你好").content().vector();
```

All nine original model payloads were independently SHA256-checked against the upstream POM hashes before use.
Maven downloads/verifies the models into target and its local cache; no model binaries or generated classes are committed.
The original module source/resource licenses and Java APIs remain unchanged.
Building uses JDK17 and the Java8 generator; the resulting jars and the58 integration cases execute on native Java8.
Those tests perform actual local model inference, tokenizer/counting, batching and similarity assertions; they do not call a paid model service.

The complete70-module clean install unit gate passed8872 cases with zero failures/errors and102 conditional skips.
VM split:7690 on Java8,1182 on Java17 modern provider fixtures.
The full command skips IT; the58 local-model IT cases and earlier store/provider integration gates are recorded separately.
Runtime scope71 classpaths (including the existing standalone fixture),399jars/127694 effective base classes has no major>52; static API findings remain15 reviewed optional third-party references.
Source-install the matching compatibility artifacts before using these SNAPSHOT coordinates; Maven Central deployment has not occurred.
