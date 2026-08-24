package io.github.guanxiangkai.web.plus.mq.config;

import io.github.guanxiangkai.web.plus.core.config.ContextPropagationAutoConfiguration;
import io.github.guanxiangkai.web.plus.mq.context.TraceMessageChannelInterceptor;
import io.github.guanxiangkai.web.plus.mq.producer.MessageProducer;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.cloud.stream.function.StreamBridge;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class MqAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    ContextPropagationAutoConfiguration.class,
                    MqAutoConfiguration.class))
            .withBean(StreamBridge.class, () -> mock(StreamBridge.class));

    @Test
    void registersProducerAndConsumerTracePropagation() {
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(MessageProducer.class);
            assertThat(context).hasSingleBean(TraceMessageChannelInterceptor.class);
        });
    }
}
