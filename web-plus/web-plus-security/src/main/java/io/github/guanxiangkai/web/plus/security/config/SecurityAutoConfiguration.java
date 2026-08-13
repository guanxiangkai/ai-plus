package io.github.guanxiangkai.web.plus.security.config;

import io.github.guanxiangkai.web.plus.core.properties.TrustedForwardProperties;
import io.github.guanxiangkai.web.plus.core.spi.CurrentUserProvider;
import io.github.guanxiangkai.web.plus.security.authorization.SuperAdminAuthorizationManagerFactory;
import io.github.guanxiangkai.web.plus.security.authorization.UserAuthorizationProvider;
import io.github.guanxiangkai.web.plus.security.aspect.AuthPermissionAspect;
import io.github.guanxiangkai.web.plus.security.context.CurrentUserThreadLocalAccessor;
import io.github.guanxiangkai.web.plus.security.context.UserContextThreadLocalAccessor;
import io.github.guanxiangkai.web.plus.security.filter.HeaderAuthenticationFilter;
import io.github.guanxiangkai.web.plus.security.handler.CustomAccessDeniedHandler;
import io.github.guanxiangkai.web.plus.security.handler.CustomAuthenticationEntryPoint;
import io.github.guanxiangkai.web.plus.security.spi.PermissionResolver;
import io.github.guanxiangkai.web.plus.security.spi.ReactiveCurrentUserProvider;
import io.micrometer.context.ContextRegistry;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.access.expression.method.DefaultMethodSecurityExpressionHandler;
import org.springframework.security.access.expression.method.MethodSecurityExpressionHandler;
import org.springframework.security.config.annotation.method.configuration.EnableReactiveMethodSecurity;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.SecurityWebFiltersOrder;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.context.NoOpServerSecurityContextRepository;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Hooks;

import java.util.List;

/**
 * Security 自动配置（WebFlux 响应式）
 * <p>
 * 适用于 Auth 服务 + 所有下游业务服务（ai-system / ai-agent / ai-safety 等）。
 * 所有请求均通过 Gateway 转发，Gateway 已完成 JWT 验证并注入 {@code X-User-*} 请求头，
 * 因此本配置仅使用 {@link HeaderAuthenticationFilter} 从请求头读取身份信息。
 * </p>
 * <p>
 * ⚠️ Gateway 不使用此配置，网关在自身模块中独立定义 SecurityWebFilterChain（RSA 公钥验证 JWT）。
 * </p>
 *
 * @author guanxiangkai
 * @since 1.0.0
 */
@Slf4j
@AutoConfiguration
@EnableWebFluxSecurity
@EnableReactiveMethodSecurity
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.REACTIVE)
@EnableConfigurationProperties({TrustedForwardProperties.class, SecurityPermitProperties.class})
public class SecurityAutoConfiguration {

    @Autowired
    private SecurityPermitProperties permitProperties;

    /**
     * 启用 Reactor 自动上下文传播。
     * <p>
     * 必须在应用启动时调用，否则 {@link UserContextThreadLocalAccessor} 注册虽然生效，
     * 但 Reactor 在 publishOn/subscribeOn 切换线程时不会自动 capture → restore ThreadLocal，
     * 导致 {@link io.github.guanxiangkai.web.plus.security.util.SecurityUtils#getUserId()} 返回 null。
     * </p>
     */
    @PostConstruct
    public void enableReactorContextPropagation() {
        Hooks.enableAutomaticContextPropagation();
        ContextRegistry.getInstance().registerThreadLocalAccessor(
                new CurrentUserThreadLocalAccessor());
        log.info("Reactor 自动上下文传播已启用（UserContext ThreadLocal 跨线程传播）");
    }

    @Bean("headerSecurityWebFilterChain")
    @Order(-100)
    @ConditionalOnMissingClass("org.springframework.cloud.gateway.filter.GlobalFilter")
    public SecurityWebFilterChain securityWebFilterChain(
            ServerHttpSecurity http,
            TrustedForwardProperties trustedForwardProperties,
            ObjectProvider<UserAuthorizationProvider> userAuthorizationProvider,
            CustomAuthenticationEntryPoint authenticationEntryPoint,
            CustomAccessDeniedHandler accessDeniedHandler) {

        // Gateway 拥有独立的 JWT 安全链；此 Header 认证链仅面向下游微服务。
        trustedForwardProperties.validateConfigured("下游服务身份透传");
        log.info("Security 配置：HeaderAuthenticationFilter（信任网关转发的请求头）");

        List<String> extraPatterns = permitProperties.getPermitPatterns();
        if (!extraPatterns.isEmpty()) {
            log.info("Security 额外放行路径：{}", extraPatterns);
        }

        return http
                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                // CORS 由 Gateway globalcors 统一处理，下游服务不再重复配置
                .cors(ServerHttpSecurity.CorsSpec::disable)
                .securityContextRepository(NoOpServerSecurityContextRepository.getInstance())
                .exceptionHandling(exception -> exception
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler))
                .authorizeExchange(auth -> {
                    auth.pathMatchers(HttpMethod.OPTIONS, "/**").permitAll();
                    auth.pathMatchers(
                            "/auth/login", "/auth/refresh", "/auth/jwks.json",
                            "/api/auth/login", "/api/auth/refresh", "/api/auth/jwks.json",
                            "/public/**",
                            "/swagger-ui/**", "/v3/api-docs/**",
                            "/doc.html", "/webjars/**",
                            "/favicon.ico", "/error",
                            "/actuator/health", "/actuator/info"
                    ).permitAll();
                    // 各服务通过 web-plus.security.permit-patterns 配置额外放行路径
                    for (String pattern : extraPatterns) {
                        String trimmed = pattern.trim();
                        // 支持 "METHOD /path" 或 "/path" 两种格式
                        int spaceIndex = trimmed.indexOf(' ');
                        if (spaceIndex > 0) {
                            String method = trimmed.substring(0, spaceIndex).trim();
                            String path = trimmed.substring(spaceIndex + 1).trim();
                            if (StringUtils.hasText(method) && StringUtils.hasText(path)) {
                                auth.pathMatchers(HttpMethod.valueOf(method.toUpperCase()), path).permitAll();
                                continue;
                            }
                        }
                        auth.pathMatchers(trimmed).permitAll();
                    }
                    // /internal/** 和 /actuator/** 其余端点需要认证，防止未授权调用
                    auth.anyExchange().authenticated();
                })
                .addFilterBefore(new HeaderAuthenticationFilter(trustedForwardProperties, userAuthorizationProvider), SecurityWebFiltersOrder.AUTHENTICATION)
                .build();
    }

    @Bean
    @ConditionalOnMissingBean
    public CustomAuthenticationEntryPoint customAuthenticationEntryPoint() {
        return new CustomAuthenticationEntryPoint();
    }

    @Bean
    @ConditionalOnMissingBean
    public CustomAccessDeniedHandler customAccessDeniedHandler() {
        return new CustomAccessDeniedHandler();
    }

    @Bean
    @ConditionalOnMissingBean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * 响应式当前用户提供者，读取 Header 认证过滤器写入的 Reactor/Spring Security 上下文。
     */
    @Bean
    @ConditionalOnMissingBean(CurrentUserProvider.class)
    public CurrentUserProvider currentUserProvider() {
        return new ReactiveCurrentUserProvider();
    }

    /**
     * 默认权限判定器，精确匹配网关下发的角色和权限；超级管理员直接放行。
     */
    @Bean
    @ConditionalOnMissingBean(PermissionResolver.class)
    public PermissionResolver permissionResolver() {
        return new PermissionResolver() {
            @Override
            public boolean hasPermission(
                    io.github.guanxiangkai.web.plus.core.context.CurrentUser user, String permission) {
                return user != null && (Boolean.TRUE.equals(user.superAdmin())
                        || user.permissions().contains(permission));
            }

            @Override
            public boolean hasRole(
                    io.github.guanxiangkai.web.plus.core.context.CurrentUser user, String role) {
                return user != null && (Boolean.TRUE.equals(user.superAdmin())
                        || user.roles().contains(role));
            }
        };
    }

    /**
     * 为网关 Header 认证链注册统一权限注解切面。
     *
     * <p>权限和当前用户由共享桥接 Bean 提供，不再为下游服务同时启动第二套 JWT 过滤链。</p>
     */
    @Bean
    @ConditionalOnClass(name = "org.aspectj.lang.ProceedingJoinPoint")
    @ConditionalOnMissingBean(AuthPermissionAspect.class)
    public AuthPermissionAspect authPermissionAspect(PermissionResolver permissionResolver,
                                                      CurrentUserProvider currentUserProvider) {
        return new AuthPermissionAspect(permissionResolver, currentUserProvider);
    }

    @Bean
    @ConditionalOnMissingBean(MethodSecurityExpressionHandler.class)
    public MethodSecurityExpressionHandler methodSecurityExpressionHandler() {
        DefaultMethodSecurityExpressionHandler handler = new DefaultMethodSecurityExpressionHandler();
        handler.setAuthorizationManagerFactory(new SuperAdminAuthorizationManagerFactory<>());
        return handler;
    }
}
