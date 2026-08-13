package io.github.guanxiangkai.web.plus.protection.filter;

import io.github.guanxiangkai.web.plus.core.enums.HttpMethod;
import io.github.guanxiangkai.web.plus.core.model.ApiResponse;
import io.github.guanxiangkai.web.plus.core.util.IpUtils;
import io.github.guanxiangkai.web.plus.protection.properties.DebounceProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import org.springframework.web.util.pattern.PathPattern;
import org.springframework.web.util.pattern.PathPatternParser;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import tools.jackson.databind.ObjectMapper;

/**
 * API 防抖过滤器（WebFlux WebFilter）
 * <p>
 * 利用 Redis SET NX + TTL 在分布式场景下对相同请求（用户标识 + 方法 + 路径）
 * 进行去重，防止前端或网络抖动导致的重复提交。
 * </p>
 *
 * <ul>
 *   <li>仅拦截修改类请求（POST / PUT / DELETE / PATCH）</li>
 *   <li>命中防抖窗口时返回 HTTP 429 和统一响应信封</li>
 *   <li>路径排除规则支持 Ant 风格通配符（如 {@code /api/auth/**}）</li>
 * </ul>
 *
 * @author guanxiangkai
 * @since 1.0.0
 */
@Slf4j
public class DebounceFilter implements WebFilter, Ordered {

    private static final String KEY_PREFIX = "web-plus:debounce:";
    private static final int DUPLICATE_CODE = 429;
    private static final String DUPLICATE_MSG = "重复提交，请勿重复操作";

    private final ReactiveStringRedisTemplate redisTemplate;
    private final DebounceProperties properties;
    private final ObjectMapper objectMapper;
    private final List<PathPattern> excludePatterns;

    public DebounceFilter(ReactiveStringRedisTemplate redisTemplate,
                          DebounceProperties properties,
                          ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.excludePatterns = buildPatterns(properties.excludePaths());
    }

    @Override
    public int getOrder() {
        // 在认证过滤器之后、业务逻辑之前执行
        return Ordered.HIGHEST_PRECEDENCE + 200;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        if (!properties.enabled()) {
            return chain.filter(exchange);
        }

        ServerHttpRequest request = exchange.getRequest();
        String method = request.getMethod().name();

        // 只对写操作防抖
        if (!HttpMethod.isModifyingMethod(method)) {
            return chain.filter(exchange);
        }

        // 排除路径白名单
        if (isExcluded(request)) {
            return chain.filter(exchange);
        }

        String key = buildKey(request);

        return redisTemplate.opsForValue()
                .setIfAbsent(key, "1", properties.duration())
                .flatMap(acquired -> {
                    if (!acquired) {
                        log.warn("[web-plus] 防抖拦截重复提交: {} {}", method, request.getPath().value());
                        return writeDuplicateResponse(exchange);
                    }
                    return chain.filter(exchange);
                });
    }

    // ──────────────────────────── 私有辅助 ────────────────────────────────

    private boolean isExcluded(ServerHttpRequest request) {
        var pathContainer = request.getPath().pathWithinApplication();
        return excludePatterns.stream().anyMatch(p -> p.matches(pathContainer));
    }

    /**
     * 防抖 key = PREFIX + 用户标识 + ":" + 方法 + ":" + 路径 [+ "?" + 查询参数]
     * <p>用户标识优先取 Authorization 的 SHA-256 哈希前 16 位（保证唯一性且避免 Token 信息泄露），
     * 无 Token 时退回客户端 IP。</p>
     */
    private String buildKey(ServerHttpRequest request) {
        StringBuilder sb = new StringBuilder(KEY_PREFIX);

        String auth = request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (auth != null && !auth.isBlank()) {
            sb.append(sha256Prefix(auth));
        } else {
            sb.append(resolveClientIp(request));
        }

        sb.append(':').append(request.getMethod().name());
        sb.append(':').append(request.getPath().value());

        if (properties.includeParams()) {
            String query = request.getURI().getQuery();
            if (query != null && !query.isBlank()) {
                sb.append('?').append(query);
            }
        }

        return sb.toString();
    }

    private String sha256Prefix(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash, 0, 8); // 取前 8 字节（16 位十六进制）
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 是 JVM 必须支持的算法，不会走到这里
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    private String resolveClientIp(ServerHttpRequest request) {
        // 仅在连接对端位于内网/本机时信任转发头，避免外网客户端伪造 X-Forwarded-For 绕过防抖。
        return IpUtils.getClientIp(request);
    }

    private Mono<Void> writeDuplicateResponse(ServerWebExchange exchange) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(org.springframework.http.HttpStatus.TOO_MANY_REQUESTS);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);

        byte[] body;
        try {
            body = objectMapper.writeValueAsBytes(ApiResponse.fail(DUPLICATE_CODE, DUPLICATE_MSG));
        } catch (Exception ex) {
            body = ("{\"code\":" + DUPLICATE_CODE + ",\"message\":\"" + DUPLICATE_MSG
                    + "\",\"data\":null,\"timestamp\":" + System.currentTimeMillis() + "}")
                    .getBytes(StandardCharsets.UTF_8);
        }

        DataBuffer buffer = response.bufferFactory().wrap(body);
        return response.writeWith(Mono.just(buffer));
    }

    private List<PathPattern> buildPatterns(List<String> paths) {
        PathPatternParser parser = new PathPatternParser();
        return paths.stream().map(parser::parse).toList();
    }
}
