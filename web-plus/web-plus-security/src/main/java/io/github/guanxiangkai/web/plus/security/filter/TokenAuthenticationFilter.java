package io.github.guanxiangkai.web.plus.security.filter;

import io.github.guanxiangkai.web.plus.core.constant.WebPlusConstants;
import io.github.guanxiangkai.web.plus.core.context.CurrentUser;
import io.github.guanxiangkai.web.plus.core.util.IpUtils;
import io.github.guanxiangkai.web.plus.core.util.UserClaimsCodec;
import io.github.guanxiangkai.web.plus.security.context.CurrentUserThreadLocalAccessor;
import io.github.guanxiangkai.web.plus.security.properties.AuthProperties;
import io.github.guanxiangkai.web.plus.security.service.TokenService;
import io.github.guanxiangkai.web.plus.security.spi.AuthorizationScope;
import io.github.guanxiangkai.web.plus.security.spi.AuthorizationScopeProvider;
import io.github.guanxiangkai.web.plus.security.spi.NoOpAuthorizationScopeProvider;
import io.github.guanxiangkai.web.plus.security.spi.TokenRevocationStore;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.net.InetSocketAddress;
import java.util.*;
import java.util.stream.Collectors;

/**
 * JWT Token 认证过滤器（WebFlux WebFilter）
 * <p>
 * 从请求头 {@code Authorization: Bearer <token>} 中解析 JWT，
 * 构建 {@link org.springframework.security.core.Authentication} 并写入
 * {@link ReactiveSecurityContextHolder}，并通过 Reactor Context 传播用户信息
 * 供 {@link io.github.guanxiangkai.web.plus.core.context.CurrentUserHolder} 在线程切换后使用。
 * </p>
 * <p>
 * 也支持网关透传模式：从 {@code X-User-Id} / {@code X-User-Claims} 请求头读取用户信息。
 * </p>
 *
 * @author guanxiangkai
 * @since 1.0.0
 */
@Slf4j
public class TokenAuthenticationFilter implements WebFilter {

    private final TokenService tokenService;
    private final TokenRevocationStore revocationStore;
    private final AuthorizationScopeProvider authorizationScopeProvider;
    private final AuthProperties properties;
    private final ObjectMapper objectMapper;
    private final String tokenHeader;
    private final String tokenPrefix;
    private static final String SUPER_ADMIN_AUTHORITY = "SUPER_ADMIN";

    public TokenAuthenticationFilter(TokenService tokenService,
                                     TokenRevocationStore revocationStore,
                                     AuthProperties properties,
                                     ObjectMapper objectMapper) {
        this(tokenService, revocationStore, new NoOpAuthorizationScopeProvider(), properties, objectMapper);
    }

    public TokenAuthenticationFilter(TokenService tokenService,
                                     TokenRevocationStore revocationStore,
                                     AuthorizationScopeProvider authorizationScopeProvider,
                                     AuthProperties properties,
                                     ObjectMapper objectMapper) {
        this.tokenService = tokenService;
        this.revocationStore = revocationStore;
        this.authorizationScopeProvider = authorizationScopeProvider;
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.tokenHeader = properties.tokenHeader();
        this.tokenPrefix = properties.tokenPrefix();
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        // 1. 尝试从 Authorization 头解析 JWT
        String header = exchange.getRequest().getHeaders().getFirst(tokenHeader);
        if (StringUtils.hasText(header) && header.startsWith(tokenPrefix)) {
            String token = header.substring(tokenPrefix.length()).trim();

            // 检查 Token 是否已被吊销
            if (revocationStore.isRevoked(token)) {
                return chain.filter(exchange);
            }

            // 单点登录：检查 token 是否为用户当前有效 token
            return tokenService.parseCurrentUser(token)
                    .map(identity -> {
                        CurrentUser user = withAuthorizationScope(identity);
                        if (Boolean.TRUE.equals(properties.singleLogin())) {
                            String currentToken = revocationStore.getCurrentToken(user.userId());
                            if (currentToken != null && !currentToken.equals(token)) {
                                return chain.filter(exchange);
                            }
                        }
                        return processAuthenticated(user, exchange, chain);
                    })
                    .orElseGet(() -> chain.filter(exchange));
        }

        // 2. 尝试从网关透传请求头读取（下游服务场景），仅在显式启用时生效
        if (Boolean.TRUE.equals(properties.gatewayPassthroughEnabled())) {
            String userId = exchange.getRequest().getHeaders()
                    .getFirst(WebPlusConstants.USER_ID_HEADER);
            String userClaims = exchange.getRequest().getHeaders()
                    .getFirst(WebPlusConstants.USER_CLAIMS_HEADER);
            String userClaimsEncoding = exchange.getRequest().getHeaders()
                    .getFirst(WebPlusConstants.USER_CLAIMS_ENCODING_HEADER);
            if (StringUtils.hasText(userId) || StringUtils.hasText(userClaims)) {
                List<String> trustedIps = properties.gatewayTrustedIps();
                if (trustedIps.isEmpty()) {
                    log.error("[web-plus] gatewayPassthroughEnabled=true 但未配置 gatewayTrustedIps，已拒绝信任 X-User-Id 请求头");
                    return chain.filter(exchange);
                }
                if (!isTrustedGatewayRequest(exchange, trustedIps, properties.gatewayTrustedProxyIps())) {
                    log.warn("[web-plus] 拒绝来自非受信网关/代理的透传请求: path={}, forwardedHopCount={}",
                            exchange.getRequest().getURI().getPath(), resolveForwardedChain(exchange).size());
                    return chain.filter(exchange);
                }
                return resolveGatewayUser(userId, userClaims, userClaimsEncoding)
                        .map(user -> processAuthenticated(user, exchange, chain))
                        .orElseGet(() -> chain.filter(exchange));
            }
        }

        return chain.filter(exchange);
    }

    private Mono<Void> processAuthenticated(CurrentUser user,
                                            ServerWebExchange exchange,
                                            WebFilterChain chain) {
        log.debug("认证成功: path={}", exchange.getRequest().getURI().getPath());

        List<SimpleGrantedAuthority> authorities = user.permissions().stream()
                .map(SimpleGrantedAuthority::new)
                .collect(Collectors.toList());
        if (Boolean.TRUE.equals(user.superAdmin())) {
            authorities.add(new SimpleGrantedAuthority(SUPER_ADMIN_AUTHORITY));
        }
        var authentication = new UsernamePasswordAuthenticationToken(
                user.userId(), null, authorities);
        authentication.setDetails(user);

        // 通过 Reactor Context 传播当前用户上下文，由 CurrentUserThreadLocalAccessor 负责
        // 在线程切换时自动设置/清理 ThreadLocal，避免跨线程 ThreadLocal 泄漏。
        return chain.filter(exchange)
                .contextWrite(ReactiveSecurityContextHolder.withAuthentication(authentication))
                .contextWrite(ctx -> ctx.put(CurrentUserThreadLocalAccessor.KEY, user));
    }

    private String resolvePeerIp(ServerWebExchange exchange) {
        return java.util.Optional.ofNullable(exchange.getRequest().getRemoteAddress())
                .map(InetSocketAddress::getAddress)
                .filter(Objects::nonNull)
                .map(addr -> addr.getHostAddress())
                .orElse("unknown");
    }

    private boolean isTrustedGatewayRequest(ServerWebExchange exchange,
                                            List<String> trustedGatewayIps,
                                            List<String> trustedProxyIps) {
        String peerIp = IpUtils.normalizeIpLiteral(resolvePeerIp(exchange));
        if (!StringUtils.hasText(peerIp)) {
            return false;
        }
        if (trustedGatewayIps.contains(peerIp)) {
            return true;
        }
        if (!trustedProxyIps.contains(peerIp)) {
            return false;
        }
        List<String> forwardedChain = resolveForwardedChain(exchange);
        if (forwardedChain.isEmpty()) {
            return false;
        }
        for (int i = forwardedChain.size() - 1; i >= 0; i--) {
            String hopIp = IpUtils.normalizeIpLiteral(forwardedChain.get(i));
            if (!StringUtils.hasText(hopIp)) {
                return false;
            }
            if (trustedProxyIps.contains(hopIp)) {
                continue;
            }
            return trustedGatewayIps.contains(hopIp);
        }
        return false;
    }

    private List<String> resolveForwardedChain(ServerWebExchange exchange) {
        return new ArrayList<>(IpUtils.getForwardedIps(exchange.getRequest().getHeaders()));
    }

    private Optional<CurrentUser> resolveGatewayUser(String userId, String userClaims, String userClaimsEncoding) {
        if (!StringUtils.hasText(userId)) {
            log.warn("[web-plus] 拒绝网关透传请求：缺少 {}", WebPlusConstants.USER_ID_HEADER);
            return Optional.empty();
        }
        if (!StringUtils.hasText(userClaims)) {
            log.warn("[web-plus] 拒绝网关透传请求：缺少 {}", WebPlusConstants.USER_CLAIMS_HEADER);
            return Optional.empty();
        }
        CurrentUser currentUser;
        try {
            JsonNode claimsNode = objectMapper.readTree(UserClaimsCodec.decode(userClaims, userClaimsEncoding));
            if (!claimsNode.isObject()) {
                log.warn("[web-plus] 拒绝网关透传请求：{} 不是合法的 JSON 对象",
                        WebPlusConstants.USER_CLAIMS_HEADER);
                return Optional.empty();
            }
            currentUser = toIdentityUser(claimsNode);
            if (!StringUtils.hasText(currentUser.userId())) {
                log.warn("[web-plus] 拒绝网关透传请求：{} 中缺少 userId", WebPlusConstants.USER_CLAIMS_HEADER);
                return Optional.empty();
            }
            if (!userId.equals(currentUser.userId())) {
                log.warn("[web-plus] 拒绝网关透传请求：{} 与 {} 不一致",
                        WebPlusConstants.USER_ID_HEADER, WebPlusConstants.USER_CLAIMS_HEADER);
                return Optional.empty();
            }
        } catch (JacksonException | IllegalArgumentException e) {
            log.warn("[web-plus] 拒绝网关透传请求：{} 不是合法的 CurrentUser JSON",
                    WebPlusConstants.USER_CLAIMS_HEADER);
            return Optional.empty();
        }
        return Optional.of(withAuthorizationScope(currentUser));
    }

    private CurrentUser toIdentityUser(JsonNode claimsNode) {
        return new CurrentUser(
                requiredText(claimsNode, "userId"),
                optionalText(claimsNode, "nickname"),
                optionalText(claimsNode, "tenantId"),
                optionalText(claimsNode, "deptId"),
                Set.of(),
                Set.of(),
                Set.of(),
                booleanValue(claimsNode, "superAdmin"),
                optionalText(claimsNode, "deviceType"),
                longValue(claimsNode, "loginTime"),
                Map.of()
        );
    }

    private CurrentUser withAuthorizationScope(CurrentUser identity) {
        AuthorizationScope scope = authorizationScopeProvider.load(
                identity.userId(), Boolean.TRUE.equals(identity.superAdmin()));
        if (scope == null) {
            throw new IllegalStateException("AuthorizationScopeProvider.load must not return null");
        }
        return new CurrentUser(
                identity.userId(),
                identity.nickname(),
                identity.tenantId(),
                identity.deptId(),
                scope.deptIds(),
                scope.roles(),
                scope.permissions(),
                identity.superAdmin(),
                identity.deviceType(),
                identity.loginTime(),
                identity.extraClaims()
        );
    }

    private String requiredText(JsonNode claimsNode, String fieldName) {
        String value = optionalText(claimsNode, fieldName);
        if (!StringUtils.hasText(value)) {
            throw new IllegalArgumentException(fieldName + " 不能为空");
        }
        return value;
    }

    private String optionalText(JsonNode claimsNode, String fieldName) {
        JsonNode fieldNode = claimsNode.get(fieldName);
        if (fieldNode == null || fieldNode.isNull()) {
            return null;
        }
        if (!fieldNode.isString()) {
            throw new IllegalArgumentException(fieldName + " 必须为字符串");
        }
        return fieldNode.asString();
    }

    private boolean booleanValue(JsonNode claimsNode, String fieldName) {
        JsonNode fieldNode = claimsNode.get(fieldName);
        if (fieldNode == null || fieldNode.isNull()) {
            return false;
        }
        if (!fieldNode.isBoolean()) {
            throw new IllegalArgumentException(fieldName + " 必须为布尔值");
        }
        return fieldNode.booleanValue();
    }

    private long longValue(JsonNode claimsNode, String fieldName) {
        JsonNode fieldNode = claimsNode.get(fieldName);
        if (fieldNode == null || fieldNode.isNull()) {
            return 0L;
        }
        if (!fieldNode.canConvertToLong()) {
            throw new IllegalArgumentException(fieldName + " 必须为整数");
        }
        return fieldNode.longValue();
    }

}
