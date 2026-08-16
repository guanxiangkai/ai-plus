package io.github.guanxiangkai.web.plus.protection.filter;

import io.github.guanxiangkai.web.plus.core.crypto.SecurityFingerprint;
import io.github.guanxiangkai.web.plus.protection.properties.ApiRateLimitProperties;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.http.HttpMethod;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import tools.jackson.databind.ObjectMapper;

import java.net.InetSocketAddress;
import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** API 限流默认路径契约测试。 */
class ApiRateLimitFilterTest {
    private final ApiRateLimitFilter filter = new ApiRateLimitFilter(
            mock(ReactiveStringRedisTemplate.class), defaults(), new ObjectMapper());

    @Test
    void shouldLimitCurrentAgentSessionAndSpeechEndpoints() {
        assertThat(filter.shouldLimit(MockServerHttpRequest.post("/agent/session/ask").build())).isTrue();
        assertThat(filter.shouldLimit(MockServerHttpRequest.post("/agent/speech/transcribe").build())).isTrue();
    }

    @Test
    void shouldKeepOnlyInfrastructureEndpointsExcluded() {
        assertThat(filter.shouldLimit(MockServerHttpRequest.post("/auth/login").build())).isFalse();
        assertThat(filter.shouldLimit(MockServerHttpRequest.get("/actuator/health").build())).isFalse();
        assertThat(filter.shouldLimit(MockServerHttpRequest.get("/internal/status").build())).isFalse();
    }

    @Test
    void shouldNotLimitCorsPreflightRequests() {
        assertThat(filter.shouldLimit(MockServerHttpRequest.method(
                HttpMethod.OPTIONS, "/agent/session/ask").build())).isFalse();
    }

    @Test
    void shouldIgnoreSpoofedIdentityHeadersAndProtectRedisKeys() {
        ReactiveStringRedisTemplate redisTemplate = mock(ReactiveStringRedisTemplate.class);
        when(redisTemplate.execute(org.mockito.ArgumentMatchers.<RedisScript<Long>>any(),
                org.mockito.ArgumentMatchers.<List<String>>any(), anyString()))
                .thenReturn(Flux.just(1L));
        ApiRateLimitFilter rateLimitFilter = new ApiRateLimitFilter(
                redisTemplate, defaults(), new ObjectMapper());
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.post("/agent/session/ask?prompt=private-value")
                        .header("X-User-Id", "spoofed-user")
                        .header("Authorization", "Bearer attacker-controlled")
                        .remoteAddress(new InetSocketAddress("198.51.100.5", 8080))
                        .build());

        rateLimitFilter.filter(exchange, ignored -> Mono.empty()).block();

        String expectedSubject = SecurityFingerprint.sha256("ip:198.51.100.5");
        String expectedPath = SecurityFingerprint.sha256("/agent/session/ask");
        verify(redisTemplate).execute(
                org.mockito.ArgumentMatchers.<RedisScript<Long>>any(),
                argThat(keys -> keys.equals(List.of(
                        "security:api:rate:" + expectedSubject + ":" + expectedPath))),
                anyString());
    }

    private static ApiRateLimitProperties defaults() {
        return new ApiRateLimitProperties(true, 120, 0, Duration.ofMinutes(1), null, null);
    }
}
