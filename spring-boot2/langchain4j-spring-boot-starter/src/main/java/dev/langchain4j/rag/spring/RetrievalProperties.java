package dev.langchain4j.rag.spring;

@org.springframework.boot.context.properties.ConstructorBinding
record RetrievalProperties(Integer maxResults, Double minScore) {
}
