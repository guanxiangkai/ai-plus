package io.github.guanxiangkai.web.plus.web.config;

import io.github.guanxiangkai.web.plus.web.client.ExternalHttpClient;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.web.reactive.function.client.WebClient;

import static org.assertj.core.api.Assertions.assertThat;

class HttpClientAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(HttpClientAutoConfiguration.class));

    @Test
    void shouldProvideFallbackWebClientBuilderAndExternalHttpClient() {
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(WebClient.Builder.class);
            assertThat(context).hasSingleBean(ExternalHttpClient.class);
        });
    }
}
