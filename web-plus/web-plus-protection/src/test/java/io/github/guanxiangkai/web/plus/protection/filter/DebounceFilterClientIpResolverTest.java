package io.github.guanxiangkai.web.plus.protection.filter;

import io.github.guanxiangkai.web.plus.core.config.ClientIpAutoConfiguration;
import io.github.guanxiangkai.web.plus.core.net.ClientIpResolver;
import io.github.guanxiangkai.web.plus.protection.autoconfigure.DebounceAutoConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ReactiveWebApplicationContextRunner;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.core.ReactiveValueOperations;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.core.publisher.Mono;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DebounceFilterClientIpResolverTest {

    private final ReactiveWebApplicationContextRunner contextRunner = new ReactiveWebApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(ClientIpAutoConfiguration.class, DebounceAutoConfiguration.class));

    @Test
    void defaultResolverAllowsDebounceFilterToStart() {
        contextRunner.withBean(ReactiveStringRedisTemplate.class,
                        () -> redisTemplate(new AtomicReference<>()))
                .withBean(ObjectMapper.class, ObjectMapper::new)
                .run(context -> {
                    assertThat(context).hasSingleBean(ClientIpResolver.class);
                    assertThat(context).hasSingleBean(DebounceFilter.class);
                });
    }

    @Test
    void customResolverIsCalledByAutoConfiguredDebounceFilter() {
        AtomicInteger calls = new AtomicInteger();
        ClientIpResolver resolver = request -> {
            calls.incrementAndGet();
            return "203.0.113.10";
        };
        AtomicReference<String> redisKey = new AtomicReference<>();
        ReactiveStringRedisTemplate redisTemplate = redisTemplate(redisKey);

        contextRunner.withBean(ClientIpResolver.class, () -> resolver)
                .withBean(ReactiveStringRedisTemplate.class, () -> redisTemplate)
                .withBean(ObjectMapper.class, ObjectMapper::new)
                .run(context -> {
                    DebounceFilter filter = context.getBean(DebounceFilter.class);
                    MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.post("/orders").build());

                    filter.filter(exchange, ignored -> Mono.empty()).block();

                    assertThat(calls.get()).isEqualTo(1);
                    assertThat(redisKey.get()).isEqualTo("web-plus:debounce:203.0.113.10:POST:/orders");
                });
    }

    @Test
    void disablingDebounceSkipsItsFilter() {
        contextRunner.withPropertyValues("web-plus.debounce.enabled=false")
                .withBean(ReactiveStringRedisTemplate.class, () -> mock(ReactiveStringRedisTemplate.class))
                .withBean(ObjectMapper.class, ObjectMapper::new)
                .run(context -> assertThat(context).doesNotHaveBean(DebounceFilter.class));
    }

    @Test
    void debounceAutoConfigurationRequiresClientIpResolverInsteadOfCreatingFallback() {
        ReactiveStringRedisTemplate redisTemplate = redisTemplate(new AtomicReference<>());

        new ReactiveWebApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(DebounceAutoConfiguration.class))
                .withBean(ReactiveStringRedisTemplate.class, () -> redisTemplate)
                .withBean(ObjectMapper.class, ObjectMapper::new)
                .run(context -> {
                    assertThat(context.getStartupFailure()).isNotNull();
                    assertThat(context.getStartupFailure()).hasMessageContaining(ClientIpResolver.class.getName());
                });
    }

    @SuppressWarnings("unchecked")
    private static ReactiveStringRedisTemplate redisTemplate(AtomicReference<String> redisKey) {
        ReactiveStringRedisTemplate redisTemplate = mock(ReactiveStringRedisTemplate.class);
        ReactiveValueOperations<String, String> valueOperations = mock(ReactiveValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.setIfAbsent(anyString(), eq("1"), any(Duration.class))).thenAnswer(invocation -> {
            redisKey.set(invocation.getArgument(0));
            return Mono.just(true);
        });
        return redisTemplate;
    }
}
