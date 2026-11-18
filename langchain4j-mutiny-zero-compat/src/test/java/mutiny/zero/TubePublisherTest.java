package mutiny.zero;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Optional;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.ai.compat.jdkhttp.Flow.Publisher;

/**
 * @author 芋道源码
 */
class TubePublisherTest {

    @ParameterizedTest
    @EnumSource(BackpressureStrategy.class)
    void dispatchesEachStrategyThroughJava8Flow(BackpressureStrategy strategy) throws Exception {
        TubeConfiguration configuration = new TubeConfiguration().withBackpressureStrategy(strategy).withBufferSize(2);
        Publisher<String> publisher = ZeroPublisher.create(configuration, tube -> tube.send("芋道流").complete());
        Optional<String> result = ZeroPublisher.toCompletionStage(publisher)
                .toCompletableFuture().get(2, TimeUnit.SECONDS);
        assertEquals(Optional.of("芋道流"), result);
    }
}
