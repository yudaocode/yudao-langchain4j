package local.compat;

import static org.junit.jupiter.api.Assertions.*;

import dev.langchain4j.micrometer.metrics.listeners.MicrometerMetricsChatModelListener;
import dev.langchain4j.observation.listener.ObservationChatModelListener;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.observation.ObservationRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;

/** Checks Boot2 Actuator with the documented modern Micrometer override. @author 芋道源码 */
@SpringBootTest(classes = ObservabilityBoot2Test.Application.class)
class ObservabilityBoot2Test {
    @Autowired
    MeterRegistry meters;

    @Test
    void initializesActuatorAndBothListeners() {
        assertEquals("1.16.6", MeterRegistry.class.getPackage().getImplementationVersion());
        assertNotNull(new MicrometerMetricsChatModelListener(meters));
        assertNotNull(new ObservationChatModelListener(ObservationRegistry.create(), meters));
        meters.counter("compat.native8").increment();
        assertEquals(1.0, meters.get("compat.native8").counter().count());
    }
    /** Probe application. @author 芋道源码 */
    @SpringBootConfiguration
    @EnableAutoConfiguration
    static class Application {}
}
