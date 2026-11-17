package dev.langchain4j.http.client.jdk;

import dev.langchain4j.http.client.AbstractHttpClientPublisherNonBlockingIT;
import dev.langchain4j.http.client.HttpClient;
import dev.langchain4j.http.client.log.LoggingHttpClient;

class JdkHttpClientPublisherNonBlockingIT extends AbstractHttpClientPublisherNonBlockingIT {

    @Override
    protected HttpClient newClient(boolean logging) {
        HttpClient client = JdkHttpClient.builder().build();
        return logging ? new LoggingHttpClient(client, true, true) : client;
    }

    @Override
    protected String policedThreadNamePrefix() {
        // The Java8 HTTP bridge dispatches bodies on its Apache I/O reactor threads.
        return "mcp-http-";
    }
}
