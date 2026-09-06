package io.github.guanxiangkai.web.plus.core.config;

import io.github.guanxiangkai.web.plus.core.net.ClientIpResolver;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;

import java.net.InetAddress;
import java.net.InetSocketAddress;

import static org.assertj.core.api.Assertions.assertThat;

class ClientIpAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(ClientIpAutoConfiguration.class));

    @Test
    void providesPeerOnlyResolverByDefault() {
        contextRunner.run(context -> assertThat(context).hasSingleBean(ClientIpResolver.class));
    }

    @Test
    void preservesApplicationClientIpResolver() {
        ClientIpResolver customResolver = request -> "custom-client";

        contextRunner.withBean(ClientIpResolver.class, () -> customResolver)
                .run(context -> {
                    assertThat(context).hasSingleBean(ClientIpResolver.class);
                    assertThat(context.getBean(ClientIpResolver.class)).isSameAs(customResolver);
                });
    }

    @Test
    void bindsExplicitTrustedProxies() {
        contextRunner.withPropertyValues("web-plus.client-ip.trusted-proxies[0]=10.0.0.0/8")
                .run(context -> {
                    ClientIpResolver resolver = context.getBean(ClientIpResolver.class);
                    MockServerHttpRequest request = MockServerHttpRequest.get("/")
                            .header("X-Forwarded-For", "203.0.113.10")
                            .remoteAddress(new InetSocketAddress(InetAddress.ofLiteral("10.0.0.2"), 8080))
                            .build();

                    assertThat(resolver.resolve(request)).isEqualTo("203.0.113.10");
                });
    }
}
