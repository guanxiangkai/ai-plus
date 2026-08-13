package io.github.guanxiangkai.web.plus.protection.filter;

import io.github.guanxiangkai.web.plus.protection.properties.ApiRateLimitProperties;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.http.HttpMethod;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

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

    private ApiRateLimitProperties defaults() {
        return new ApiRateLimitProperties(true, 120, 0, Duration.ofMinutes(1), null, null);
    }
}
