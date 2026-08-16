package io.github.guanxiangkai.web.plus.core.config;

import io.github.guanxiangkai.web.plus.core.net.ClientIpResolver;
import io.github.guanxiangkai.web.plus.core.net.TrustedProxyClientIpResolver;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;

class CoreAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(CoreAutoConfiguration.class));

    @Test
    void registersExplicitCoreInfrastructureWithoutPackageScanning() {
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(ClientIpResolver.class);
            assertThat(context.getBean(ClientIpResolver.class))
                    .isInstanceOf(TrustedProxyClientIpResolver.class);
            assertThat(context).hasBean("webPlusJsonMapperBuilderCustomizer");
        });
    }

    @Test
    void backsOffWhenApplicationProvidesClientIpResolver() {
        contextRunner.withUserConfiguration(ClientIpOverride.class)
                .run(context -> {
                    assertThat(context).hasSingleBean(ClientIpResolver.class);
                    assertThat(context.getBean(ClientIpResolver.class).resolve(
                            MockServerHttpRequest.get("/").build()))
                            .isEqualTo("application-defined");
                });
    }

    @Configuration(proxyBeanMethods = false)
    static class ClientIpOverride {

        @Bean
        ClientIpResolver clientIpResolver() {
            return ignored -> "application-defined";
        }
    }
}
