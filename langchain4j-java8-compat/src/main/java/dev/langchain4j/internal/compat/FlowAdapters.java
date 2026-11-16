package dev.langchain4j.internal.compat;

import org.reactivestreams.Publisher;
import org.springframework.ai.compat.jdkhttp.Flow;

/**
 * Bridges Reactive Streams and the Java 8 Flow compatibility interfaces.
 *
 * @author 芋道源码
 */
public final class FlowAdapters {
    private FlowAdapters() {}
    @SuppressWarnings("unchecked")
    public static <T> Publisher<T> toPublisher(Flow.Publisher<? extends T> publisher) {
        return org.springframework.ai.compat.jdkhttp.FlowAdapters.flowPublisherToFlux((Flow.Publisher<T>) publisher);
    }
    @SuppressWarnings("unchecked")
    public static <T> Flow.Publisher<T> toFlowPublisher(Publisher<? extends T> publisher) {
        return org.springframework.ai.compat.jdkhttp.FlowAdapters.publisherToFlowPublisher((Publisher<T>) publisher);
    }
}
