package io.github.guanxiangkai.web.plus.web.config;

import io.github.guanxiangkai.web.plus.core.context.CurrentUser;
import io.github.guanxiangkai.web.plus.core.context.CurrentUserHolder;
import io.github.guanxiangkai.web.plus.core.spi.CurrentUserProvider;
import io.github.guanxiangkai.web.plus.security.config.SecurityAutoConfiguration;
import io.github.guanxiangkai.web.plus.security.context.UserContext;
import io.github.guanxiangkai.web.plus.security.context.UserContextHolder;
import io.github.guanxiangkai.web.plus.security.spi.PermissionResolver;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Mono;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * web-plus 当前用户桥接配置
 * <p>
 * 将 web-plus-security 的 {@link UserContextHolder} 与 WebFlux Reactor Context
 * 桥接为 web-plus {@link CurrentUserProvider} / {@link CurrentUserHolder}，
 * 使 {@code @RequiresLogin} / {@code @RequiresPermission} 在响应式与同步链路中都能读取当前用户。
 * </p>
 *
 * @author guanxiangkai
 * @since 1.0.0
 */
@Slf4j
@AutoConfiguration
@AutoConfigureBefore(SecurityAutoConfiguration.class)
@ConditionalOnClass(CurrentUserProvider.class)
public class WebPlusCurrentUserAutoConfiguration {

    @Bean
    @ConditionalOnClass(PermissionResolver.class)
    @ConditionalOnMissingBean(PermissionResolver.class)
    public PermissionResolver permissionResolver() {
        return new WebPlusPermissionResolver();
    }

    @Bean
    @Primary
    public CurrentUserProvider currentUserProvider() {
        log.info("[web-plus] 当前用户桥接已启用（Reactor Context → CurrentUserProvider / ThreadLocal）");
        return new ReactiveBridgeCurrentUserProvider();
    }

    static final class WebPlusPermissionResolver implements PermissionResolver {

        @Override
        public boolean hasPermission(CurrentUser user, String permission) {
            return user != null && (Boolean.TRUE.equals(user.superAdmin()) || user.permissions().contains(permission));
        }

        @Override
        public boolean hasRole(CurrentUser user, String role) {
            return user != null && (Boolean.TRUE.equals(user.superAdmin()) || user.roles().contains(role));
        }
    }

    static final class ReactiveBridgeCurrentUserProvider implements CurrentUserProvider {

        private static CurrentUser toCurrentUser(UserContext userContext) {
            if (userContext == null || !StringUtils.hasText(userContext.userId())) {
                return null;
            }
            Map<String, Object> claims = userContext.claims();
            return new CurrentUser(
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
        }

        @SuppressWarnings("unchecked")
        private static Map<String, Object> toClaims(Object details) {
            if (details instanceof CurrentUser currentUser) {
                return currentUser.extraClaims();
            }
            if (details instanceof Map<?, ?> map) {
                return map.entrySet().stream()
                        .collect(Collectors.toUnmodifiableMap(
                                entry -> String.valueOf(entry.getKey()),
                                Map.Entry::getValue
                        ));
            }
            return Map.of();
        }

        private static Set<String> toStringSet(Object value) {
            if (value instanceof Collection<?> collection) {
                return collection.stream().map(String::valueOf).collect(Collectors.toUnmodifiableSet());
            }
            return Set.of();
        }

        private static String asText(Object value) {
            return value != null ? String.valueOf(value) : null;
        }

        @Override
        public Optional<CurrentUser> getCurrentUser() {
            CurrentUser currentUser = CurrentUserHolder.get();
            if (currentUser != null) {
                return Optional.of(currentUser);
            }
            return Optional.ofNullable(toCurrentUser(UserContextHolder.get()));
        }

        @Override
        public Mono<Optional<CurrentUser>> getCurrentUserMono() {
            return Mono.deferContextual(contextView -> {
                        Object currentUser = contextView.getOrDefault(CurrentUserHolder.REACTOR_CONTEXT_KEY, null);
                        if (currentUser instanceof CurrentUser user) {
                            return Mono.just(Optional.of(user));
                        }
                        Optional<CurrentUser> fallback = getCurrentUser();
                        if (fallback.isPresent()) {
                            return Mono.just(fallback);
                        }
                        return ReactiveSecurityContextHolder.getContext()
                                .map(SecurityContext::getAuthentication)
                                .map(this::toCurrentUser)
                                .filter(Optional::isPresent)
                                .switchIfEmpty(Mono.just(Optional.empty()));
                    })
                    .onErrorResume(IllegalStateException.class, ex -> {
                        log.debug("[web-plus] Reactor Context 读取当前用户失败，回退至 SecurityContext: exception={}",
                                ex.getClass().getSimpleName());
                        return ReactiveSecurityContextHolder.getContext()
                                .map(SecurityContext::getAuthentication)
                                .map(this::toCurrentUser)
                                .filter(Optional::isPresent)
                                .switchIfEmpty(Mono.fromSupplier(this::getCurrentUser));
                    });
        }

        private static boolean toBoolean(Object value) {
            if (value instanceof Boolean booleanValue) {
                return booleanValue;
            }
            return value != null && Boolean.parseBoolean(value.toString());
        }

        private Optional<CurrentUser> toCurrentUser(Authentication authentication) {
            if (authentication == null || !authentication.isAuthenticated()) {
                return Optional.empty();
            }
            String userId = authentication.getName();
            if (!StringUtils.hasText(userId) || "anonymousUser".equals(authentication.getPrincipal())) {
                return Optional.empty();
            }
            if (authentication.getPrincipal() instanceof CurrentUser currentUser) {
                return Optional.of(currentUser);
            }
            if (authentication.getDetails() instanceof CurrentUser currentUser) {
                return Optional.of(currentUser);
            }
            Map<String, Object> claims = toClaims(authentication.getDetails());
            return Optional.of(new CurrentUser(
                    userId,
                    asText(claims.get("nickname")),
                    asText(claims.get("tenantId")),
                    asText(claims.get("deptId")),
                    toStringSet(claims.get("deptIds")),
                    toStringSet(claims.get("roles")),
                    toStringSet(claims.get("permissions")),
                    toBoolean(claims.get("superAdmin")),
                    asText(claims.get("deviceType")),
                    System.currentTimeMillis(),
                    claims
            ));
        }
    }
}
