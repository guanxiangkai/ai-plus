package io.github.guanxiangkai.web.plus.security.filter;

import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import io.github.guanxiangkai.web.plus.core.constants.AuthConstants;
import io.github.guanxiangkai.web.plus.core.properties.TrustedForwardProperties;
import io.github.guanxiangkai.web.plus.core.util.UserClaimsCodec;
import io.github.guanxiangkai.web.plus.security.authorization.AuthorizationScope;
import io.github.guanxiangkai.web.plus.security.authorization.UserAuthorizationProvider;
import io.github.guanxiangkai.web.plus.security.context.UserContext;
import io.github.guanxiangkai.web.plus.security.context.UserContextHolder;
import io.github.guanxiangkai.web.plus.security.context.UserContextThreadLocalAccessor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

/**
 * 请求头认证过滤器（下游服务专用）
 * <p>
 * 信任网关已完成 JWT 验证，从网关转发的请求头中读取身份信息：
 * <ul>
 *   <li>{@code X-User-Id} — 用户 ID</li>
 *   <li>{@code X-Tenant-Id} — 租户 ID</li>
 *   <li>{@code X-User-Claims} — 用户 Claims（默认由网关使用 UTF-8 Base64URL 编码）</li>
 * </ul>
 * 构建 {@link org.springframework.security.core.Authentication} 写入
 * {@link org.springframework.security.core.context.ReactiveSecurityContextHolder}，
 * 同时填充 {@link UserContextHolder} 的 ThreadLocal（通过 Micrometer Context Propagation 跨线程传播），
 * 使得 {@link io.github.guanxiangkai.web.plus.security.util.SecurityUtils SecurityUtils} 的同步方法和响应式方法均可正常工作。
 * </p>
 *
 * @author guanxiangkai
 * @since 1.0.0
 */
@Slf4j
public class HeaderAuthenticationFilter implements WebFilter {

    private static final String SUPER_ADMIN_AUTHORITY = "SUPER_ADMIN";
    private static final String WEB_PLUS_CURRENT_USER_CONTEXT_KEY = "web-plus.currentUser";
    private static final WebPlusCurrentUserBridge WEB_PLUS_CURRENT_USER_BRIDGE = new WebPlusCurrentUserBridge();

    private final TrustedForwardProperties trustedForwardProperties;
    private final UserAuthorizationProvider userAuthorizationProvider;

    public HeaderAuthenticationFilter(TrustedForwardProperties trustedForwardProperties) {
        this(trustedForwardProperties, (UserAuthorizationProvider) null);
    }

    public HeaderAuthenticationFilter(TrustedForwardProperties trustedForwardProperties,
                                      UserAuthorizationProvider userAuthorizationProvider) {
        this.trustedForwardProperties = trustedForwardProperties;
        this.userAuthorizationProvider = userAuthorizationProvider;
    }

    public HeaderAuthenticationFilter(TrustedForwardProperties trustedForwardProperties,
                                      ObjectProvider<UserAuthorizationProvider> userAuthorizationProvider) {
        this.trustedForwardProperties = trustedForwardProperties;
        this.userAuthorizationProvider = userAuthorizationProvider == null
                ? null
                : userAuthorizationProvider.getIfAvailable();
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String userId = request.getHeaders().getFirst(AuthConstants.HeaderConstants.USER_ID);
        String forwardedToken = request.getHeaders().getFirst(trustedForwardProperties.getHeaderName());

        if (!StringUtils.hasText(userId) && !StringUtils.hasText(forwardedToken)) {
            return chain.filter(exchange);
        }

        if (!trustedForwardProperties.matches(forwardedToken)) {
            log.warn("拒绝未受信任的身份透传请求: path={}, remote={}, userId={}",
                    request.getURI().getPath(),
                    request.getRemoteAddress(),
                    userId);
            exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
            return exchange.getResponse().setComplete();
        }

        String effectiveUserId = StringUtils.hasText(userId)
                ? userId
                : AuthConstants.HeaderConstants.INTERNAL_SERVICE_USER_ID;

        // 解析 Claims
        Map<String, Object> claims = new HashMap<>();
        String rawClaims = request.getHeaders().getFirst(AuthConstants.HeaderConstants.USER_CLAIMS);
        if (StringUtils.hasText(rawClaims)) {
            try {
                String claimsJson = UserClaimsCodec.decode(rawClaims,
                        request.getHeaders().getFirst(AuthConstants.HeaderConstants.USER_CLAIMS_ENCODING));
                JSONObject json = JSONUtil.parseObj(claimsJson);
                claims.putAll(json);
            } catch (Exception e) {
                log.warn("解析 X-User-Claims 失败: {}", e.getMessage());
            }
        }

        String tenantId = request.getHeaders().getFirst(AuthConstants.HeaderConstants.TENANT_ID);
        if (StringUtils.hasText(tenantId)) {
            claims.putIfAbsent("tenantId", tenantId);
        }

        // 构建 UserContext，通过 Reactor Context 传播（不在 Netty I/O 线程上设置 ThreadLocal，
        // 避免 set/clear 在不同线程上执行导致 ThreadLocal 泄漏）
        UserContext userContext = buildUserContext(effectiveUserId, tenantId, claims);
        // 构建 Authentication 并写入 SecurityContext
        Object webPlusCurrentUser = WEB_PLUS_CURRENT_USER_BRIDGE.create(userContext);
        List<SimpleGrantedAuthority> authorities = userContext.permissions().stream()
                .filter(StringUtils::hasText)
                .map(SimpleGrantedAuthority::new)
                .collect(Collectors.toCollection(ArrayList::new));
        if (userContext.superAdmin()) {
            authorities.add(new SimpleGrantedAuthority(SUPER_ADMIN_AUTHORITY));
        }
        var authentication = new UsernamePasswordAuthenticationToken(
                effectiveUserId,
                null,
                authorities
        );
        authentication.setDetails(webPlusCurrentUser != null ? webPlusCurrentUser : claims);

        log.debug("下游服务认证（请求头）: userId={}, tenantId={}", effectiveUserId, tenantId);
        log.debug("下游服务认证详情: userId={}, superAdmin={}, roles={}, permissionCount={}",
                effectiveUserId,
                userContext.superAdmin(),
                userContext.roles(),
                userContext.permissions().size());

        return chain.filter(exchange)
                .contextWrite(ctx -> {
                    var updated = ctx.put(UserContextThreadLocalAccessor.KEY, userContext);
                    if (webPlusCurrentUser != null) {
                        updated = updated.put(WEB_PLUS_CURRENT_USER_CONTEXT_KEY, webPlusCurrentUser);
                    }
                    return updated;
                })
                .contextWrite(ReactiveSecurityContextHolder.withAuthentication(authentication));
    }

    @SuppressWarnings("unchecked")
    private UserContext buildUserContext(String userId, String tenantId, Map<String, Object> claims) {
        boolean superAdmin = false;
        Object sa = claims.get("superAdmin");
        if (sa instanceof Boolean b) {
            superAdmin = b;
        } else if (sa != null) {
            superAdmin = Boolean.parseBoolean(sa.toString());
        }

        String deptId = claims.get("deptId") != null
                ? claims.get("deptId").toString() : null;

        AuthorizationScope scope = loadAuthorizationScope(userId, superAdmin);
        if (!scope.roles().isEmpty()) {
            claims.put("roles", scope.roles());
        }
        if (!scope.permissions().isEmpty()) {
            claims.put("permissions", scope.permissions());
        }
        if (!scope.deptIds().isEmpty()) {
            claims.put("deptIds", scope.deptIds());
        }

        return new UserContext(userId, tenantId, superAdmin, deptId,
                scope.deptIds(), scope.roles(), scope.permissions(), claims);
    }

    private AuthorizationScope loadAuthorizationScope(String userId, boolean superAdmin) {
        if (userAuthorizationProvider == null
                || AuthConstants.HeaderConstants.INTERNAL_SERVICE_USER_ID.equals(userId)) {
            return AuthorizationScope.EMPTY;
        }
        return userAuthorizationProvider.load(userId, superAdmin);
    }

    @SuppressWarnings("unchecked")
    private static final class WebPlusCurrentUserBridge {

        private static final String CURRENT_USER_CLASS_NAME = "io.github.guanxiangkai.web.plus.core.context.CurrentUser";

        private final Constructor<?> constructor;
        private final AtomicBoolean failureLogged = new AtomicBoolean(false);

        private WebPlusCurrentUserBridge() {
            this.constructor = resolveConstructor();
        }

        private static Set<String> toStringSet(Object value) {
            if (value instanceof Collection<?> collection) {
                return collection.stream().map(Object::toString).collect(Collectors.toSet());
            }
            return Set.of();
        }

        private static String asText(Object value) {
            return value != null ? value.toString() : null;
        }

        private Object create(UserContext userContext) {
            if (constructor == null) {
                return null;
            }
            Map<String, Object> claims = userContext.claims();
            try {
                return constructor.newInstance(
                        userContext.userId(),
                        asText(claims.get("nickname")),
                        userContext.tenantId(),
                        userContext.deptId(),
                        userContext.deptIds(),
                        userContext.roles(),
                        userContext.permissions(),
                        userContext.superAdmin(),
                        asText(claims.get("deviceType")),
                        System.currentTimeMillis(),
                        claims
                );
            } catch (InstantiationException | IllegalAccessException | InvocationTargetException e) {
                if (failureLogged.compareAndSet(false, true)) {
                    log.warn("构建 web-plus CurrentUser 失败: {}", e.getMessage());
                }
                return null;
            }
        }

        private Constructor<?> resolveConstructor() {
            try {
                Class<?> currentUserClass = Class.forName(CURRENT_USER_CLASS_NAME);
                return currentUserClass.getConstructor(
                        String.class,
                        String.class,
                        String.class,
                        String.class,
                        Set.class,
                        Set.class,
                        Set.class,
                        Boolean.class,
                        String.class,
                        long.class,
                        Map.class
                );
            } catch (ClassNotFoundException e) {
                return null;
            } catch (NoSuchMethodException e) {
                log.warn("初始化 web-plus CurrentUser 桥接失败: {}", e.getMessage());
                return null;
            }
        }
    }
}
