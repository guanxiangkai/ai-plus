package io.github.guanxiangkai.web.plus.protection.filter;

import io.github.guanxiangkai.web.plus.core.crypto.SecurityFingerprint;
import io.github.guanxiangkai.web.plus.protection.properties.DebounceProperties;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.core.ReactiveValueOperations;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.core.publisher.Mono;
import tools.jackson.databind.ObjectMapper;

import java.net.InetSocketAddress;
import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DebounceFilterTest {

    @Test
    void shouldIgnoreUnverifiedAuthorizationAndProtectRedisKeyMaterial() {
        ReactiveStringRedisTemplate redisTemplate = mock(ReactiveStringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ReactiveValueOperations<String, String> valueOperations = mock(ReactiveValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.setIfAbsent(anyString(), eq("1"), eq(Duration.ofSeconds(1))))
                .thenReturn(Mono.just(true));
        DebounceFilter filter = new DebounceFilter(
                redisTemplate,
                new DebounceProperties(true, Duration.ofSeconds(1), true, List.of("/auth/**")),
                new ObjectMapper());
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.post("/agent/session/ask?prompt=private-value")
                        .header("Authorization", "Bearer attacker-controlled")
                        .header("X-User-Id", "spoofed-user")
                        .remoteAddress(new InetSocketAddress("198.51.100.5", 8080))
                        .build());

        filter.filter(exchange, ignored -> Mono.empty()).block();

        String expectedKey = "web-plus:debounce:"
                + SecurityFingerprint.sha256("ip:198.51.100.5")
                + ':' + SecurityFingerprint.sha256("POST\n/agent/session/ask\nprompt=private-value");
        verify(valueOperations).setIfAbsent(expectedKey, "1", Duration.ofSeconds(1));
        assertThat(expectedKey)
                .doesNotContain("private-value", "attacker-controlled", "spoofed-user", "198.51.100.5");
    }
}
