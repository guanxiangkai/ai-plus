package io.github.guanxiangkai.web.plus.protection.filter;

import io.github.guanxiangkai.web.plus.core.constants.AuthConstants;
import io.github.guanxiangkai.web.plus.core.model.ApiResponse;
import io.github.guanxiangkai.web.plus.protection.properties.ApiRateLimitProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.net.InetSocketAddress;
import java.util.List;
import java.util.regex.Pattern;
import tools.jackson.databind.ObjectMapper;

/**
 * 服务侧 API 固定窗口限流过滤器。
 *
 * @author guanxiangkai
 * @since 1.0.0
 */
@Slf4j
public class ApiRateLimitFilter implements WebFilter, Ordered {

    private static final String RATE_LIMIT_PREFIX = "security:api:rate:";
    private static final String RATE_LIMIT_MESSAGE = "请求过于频繁，请稍后再试";
    private static final RedisScript<Long> INCR_WITH_EXPIRE_SCRIPT = RedisScript.of(
            """
                    local count = redis.call('INCR', KEYS[1])
                    if redis.call('TTL', KEYS[1]) < 0 then
                        redis.call('EXPIRE', KEYS[1], ARGV[1])
                    end
                    return count
                    """,
            Long.class
    );

    private final ReactiveStringRedisTemplate redisTemplate;
    private final ApiRateLimitProperties properties;
    private final ObjectMapper objectMapper;

    public ApiRateLimitFilter(ReactiveStringRedisTemplate redisTemplate,
                              ApiRateLimitProperties properties,
                              ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        if (!shouldLimit(request)) {
            return chain.filter(exchange);
        }

        return resolveSubject(request)
                .flatMap(subject -> {
                    String key = RATE_LIMIT_PREFIX + subject + ":" + request.getPath().value();
                    long windowSeconds = Math.max(1, properties.window().toSeconds());
                    return redisTemplate.execute(INCR_WITH_EXPIRE_SCRIPT, List.of(key), String.valueOf(windowSeconds))
                            .single()
                            .flatMap(count -> {
                                if (count > properties.effectiveLimit()) {
                                    log.warn("[ApiRateLimit] API 限流: subject={}, path={}, count={}, limit={}/{}s",
                                            subject, request.getPath().value(), count, properties.effectiveLimit(), windowSeconds);
                                    return reject(exchange);
                                }
                                return chain.filter(exchange);
                            });
                });
    }

    boolean shouldLimit(ServerHttpRequest request) {
        if (HttpMethod.OPTIONS.equals(request.getMethod())) {
            return false;
        }
        String path = request.getPath().value();
        return matchesAny(path, properties.includePaths()) && !matchesAny(path, properties.excludePaths());
    }

    private boolean matchesAny(String path, List<String> patterns) {
        for (String pattern : patterns) {
            if (Pattern.matches(toRegex(pattern), path)) {
                return true;
            }
        }
        return false;
    }

    private String toRegex(String pattern) {
        StringBuilder regex = new StringBuilder("^");
        for (int i = 0; i < pattern.length(); i++) {
            char ch = pattern.charAt(i);
            if (ch == '*') {
                boolean doubleStar = i + 1 < pattern.length() && pattern.charAt(i + 1) == '*';
                regex.append(doubleStar ? ".*" : "[^/]*");
                if (doubleStar) {
                    i++;
                }
            } else if ("\\.[]{}()+-^$?|".indexOf(ch) >= 0) {
                regex.append('\\').append(ch);
            } else {
                regex.append(ch);
            }
        }
        return regex.append('$').toString();
    }

    private Mono<String> resolveSubject(ServerHttpRequest request) {
        String userId = request.getHeaders().getFirst(AuthConstants.HeaderConstants.USER_ID);
        if (StringUtils.hasText(userId)) {
            return Mono.just("user:" + userId);
        }
        return ReactiveSecurityContextHolder.getContext()
                .map(SecurityContext::getAuthentication)
                .filter(auth -> auth.isAuthenticated() && !"anonymousUser".equals(auth.getPrincipal()))
                .map(Authentication::getName)
                .filter(StringUtils::hasText)
                .map(authUserId -> "user:" + authUserId)
                .defaultIfEmpty("ip:" + clientIp(request));
    }

    private String clientIp(ServerHttpRequest request) {
        String ip = firstHeaderValue(request.getHeaders().getFirst("X-Forwarded-For"));
        if (!StringUtils.hasText(ip)) {
            ip = firstHeaderValue(request.getHeaders().getFirst("X-Real-IP"));
        }
        if (!StringUtils.hasText(ip)) {
            InetSocketAddress remoteAddress = request.getRemoteAddress();
            ip = remoteAddress != null && remoteAddress.getAddress() != null
                    ? remoteAddress.getAddress().getHostAddress()
                    : "unknown";
        }
        return ip;
    }

    private String firstHeaderValue(String value) {
        if (!StringUtils.hasText(value) || "unknown".equalsIgnoreCase(value)) {
            return null;
        }
        int comma = value.indexOf(',');
        return comma >= 0 ? value.substring(0, comma).trim() : value.trim();
    }

    private Mono<Void> reject(ServerWebExchange exchange) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(HttpStatus.TOO_MANY_REQUESTS);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        try {
            byte[] body = objectMapper.writeValueAsBytes(ApiResponse.fail(
                    HttpStatus.TOO_MANY_REQUESTS.value(), RATE_LIMIT_MESSAGE));
            DataBuffer buffer = response.bufferFactory().wrap(body);
            return response.writeWith(Mono.just(buffer));
        } catch (Exception exception) {
            return Mono.error(new IllegalStateException("API 限流响应序列化失败", exception));
        }
    }

    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE - 100;
    }
}
