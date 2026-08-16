package io.github.guanxiangkai.web.plus.protection.filter;

import io.github.guanxiangkai.web.plus.core.crypto.SecurityFingerprint;
import io.github.guanxiangkai.web.plus.core.enums.HttpMethod;
import io.github.guanxiangkai.web.plus.core.model.ApiResponse;
import io.github.guanxiangkai.web.plus.core.net.ClientIpResolver;
import io.github.guanxiangkai.web.plus.protection.properties.DebounceProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import org.springframework.web.util.pattern.PathPattern;
import org.springframework.web.util.pattern.PathPatternParser;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
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
    private final ClientIpResolver clientIpResolver;

    public DebounceFilter(ReactiveStringRedisTemplate redisTemplate,
                          DebounceProperties properties,
                          ObjectMapper objectMapper) {
        this(redisTemplate, properties, objectMapper, ClientIpResolver.directPeer());
    }

    /**
     * 创建 API 防抖过滤器。
     *
     * @param redisTemplate Redis 响应式客户端
     * @param properties 防抖配置
     * @param objectMapper JSON 序列化器
     * @param clientIpResolver 可信客户端 IP 解析策略
     */
    public DebounceFilter(ReactiveStringRedisTemplate redisTemplate,
                          DebounceProperties properties,
                          ObjectMapper objectMapper,
                          ClientIpResolver clientIpResolver) {
        this.redisTemplate = redisTemplate;
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.clientIpResolver = clientIpResolver;
        this.excludePatterns = buildPatterns(properties.excludePaths());
    }

    @Override
    public int getOrder() {
        // 必须在 Spring Security 建立可信认证上下文后执行。
        return Ordered.LOWEST_PRECEDENCE - 200;
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

        return resolveSubject(request)
                .flatMap(subject -> {
                    String key = buildKey(request, subject);
                    return redisTemplate.opsForValue()
                            .setIfAbsent(key, "1", properties.duration())
                            .flatMap(acquired -> {
                                if (!acquired) {
                                    log.warn("[web-plus] 防抖拦截重复提交: {} {}",
                                            method, request.getPath().value());
                                    return writeDuplicateResponse(exchange);
                                }
                                return chain.filter(exchange);
                            });
                });
    }

    // ──────────────────────────── 私有辅助 ────────────────────────────────

    private boolean isExcluded(ServerHttpRequest request) {
        var pathContainer = request.getPath().pathWithinApplication();
        return excludePatterns.stream().anyMatch(p -> p.matches(pathContainer));
    }

    /**
     * 构建不包含原始 Token、IP、路径参数或查询参数的防抖键。
     *
     * <p>用户标识与请求目标分别生成完整 SHA-256 指纹，既保持同一请求的稳定去重语义，
     * 又避免 Redis Key 暴露认证材料和可能包含个人标识的 URL。</p>
     */
    private String buildKey(ServerHttpRequest request, String subject) {
        StringBuilder target = new StringBuilder(request.getMethod().name())
                .append('\n')
                .append(request.getPath().value());
        if (properties.includeParams()) {
            String query = request.getURI().getQuery();
            if (query != null && !query.isBlank()) {
                target.append('\n').append(query);
            }
        }
        return KEY_PREFIX + SecurityFingerprint.sha256(subject)
                + ':' + SecurityFingerprint.sha256(target.toString());
    }

    /**
     * 只使用已经完成认证的安全上下文；匿名请求回退到可信客户端 IP。
     * 任意 Authorization 或用户请求头不能改变防抖主体，避免攻击者通过随机请求头绕过限制。
     */
    private Mono<String> resolveSubject(ServerHttpRequest request) {
        return ReactiveSecurityContextHolder.getContext()
                .map(SecurityContext::getAuthentication)
                .filter(auth -> auth.isAuthenticated() && !(auth instanceof AnonymousAuthenticationToken))
                .map(Authentication::getName)
                .filter(StringUtils::hasText)
                .map(userId -> "user:" + userId)
                .defaultIfEmpty("ip:" + resolveClientIp(request));
    }

    private String resolveClientIp(ServerHttpRequest request) {
        return clientIpResolver.resolve(request);
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
