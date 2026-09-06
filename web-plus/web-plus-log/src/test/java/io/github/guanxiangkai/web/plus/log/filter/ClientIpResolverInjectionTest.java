package io.github.guanxiangkai.web.plus.log.filter;

import io.github.guanxiangkai.web.plus.core.config.ClientIpAutoConfiguration;
import io.github.guanxiangkai.web.plus.core.net.ClientIpResolver;
import io.github.guanxiangkai.web.plus.log.autoconfigure.WebPlusLogAutoConfiguration;
import io.github.guanxiangkai.web.plus.log.entity.BaseLog;
import io.github.guanxiangkai.web.plus.log.spi.AccessLogHandler;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ReactiveWebApplicationContextRunner;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.core.publisher.Mono;
import tools.jackson.databind.ObjectMapper;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class ClientIpResolverInjectionTest {

    private final ReactiveWebApplicationContextRunner contextRunner = new ReactiveWebApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(ClientIpAutoConfiguration.class, WebPlusLogAutoConfiguration.class))
            .withBean(ObjectMapper.class, ObjectMapper::new)
            .withPropertyValues("web-plus.log.access-log-entity-class=" + TestLog.class.getName());

    @Test
    void defaultResolverStartsAndAccessLogUsesTcpPeer() {
        AtomicReference<TestLog> captured = new AtomicReference<>();

        contextRunner.withBean(AccessLogHandler.class, () -> entity -> captured.set((TestLog) entity))
                .run(context -> {
                    AccessLogFilter filter = context.getBean(AccessLogFilter.class);
                    MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/orders")
                            .header("X-Forwarded-For", "203.0.113.10")
                            .remoteAddress(address("10.0.0.2")));

                    filter.filter(exchange, ignored -> Mono.empty()).block();

                    assertThat(captured.get().getClientIp()).isEqualTo("10.0.0.2");
                });
    }

    @Test
    void customResolverIsCalledByAutoConfiguredAccessLogFilter() {
        AtomicInteger calls = new AtomicInteger();
        ClientIpResolver resolver = request -> {
            calls.incrementAndGet();
            return "203.0.113.10";
        };
        AtomicReference<TestLog> captured = new AtomicReference<>();

        contextRunner.withBean(ClientIpResolver.class, () -> resolver)
                .withBean(AccessLogHandler.class, () -> entity -> captured.set((TestLog) entity))
                .run(context -> {
                    AccessLogFilter filter = context.getBean(AccessLogFilter.class);
                    MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/orders"));

                    filter.filter(exchange, ignored -> Mono.empty()).block();

                    assertThat(calls.get()).isEqualTo(1);
                    assertThat(captured.get().getClientIp()).isEqualTo("203.0.113.10");
                });
    }

    @Test
    void disablingAccessLogSkipsItsFilter() {
        contextRunner.withPropertyValues("web-plus.log.access-log-enabled=false")
                .run(context -> assertThat(context).doesNotHaveBean(AccessLogFilter.class));
    }

    @Test
    void logAutoConfigurationRequiresClientIpResolverInsteadOfCreatingFallback() {
        new ReactiveWebApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(WebPlusLogAutoConfiguration.class))
                .withBean(ObjectMapper.class, ObjectMapper::new)
                .run(context -> {
                    assertThat(context.getStartupFailure()).isNotNull();
                    assertThat(context.getStartupFailure()).hasMessageContaining(ClientIpResolver.class.getName());
                });
    }

    private static InetSocketAddress address(String ip) {
        return new InetSocketAddress(InetAddress.ofLiteral(ip), 8080);
    }

    public static final class TestLog extends BaseLog {
    }
}
