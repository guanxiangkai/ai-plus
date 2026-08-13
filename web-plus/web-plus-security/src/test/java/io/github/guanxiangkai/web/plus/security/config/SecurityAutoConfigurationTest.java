package io.github.guanxiangkai.web.plus.security.config;

import io.github.guanxiangkai.web.plus.core.context.CurrentUser;
import io.github.guanxiangkai.web.plus.core.context.CurrentUserHolder;
import io.github.guanxiangkai.web.plus.security.spi.ReactiveCurrentUserProvider;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingClass;

import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Set;

import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import static org.assertj.core.api.Assertions.assertThat;

class SecurityAutoConfigurationTest {

    @Test
    void shouldOnlyAutoRegisterGatewayHeaderSecurity() throws Exception {
        String imports;
        try (var input = getClass().getClassLoader().getResourceAsStream(
                "META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports")) {
            assertThat(input).isNotNull();
            imports = new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }

        assertThat(imports)
                .contains(SecurityAutoConfiguration.class.getName())
                .doesNotContain("io.github.guanxiangkai.web.plus.security.config.WebPlusAuthAutoConfiguration");
    }

    @Test
    void shouldSkipHeaderSecurityChainWhenGatewayIsPresent() throws NoSuchMethodException {
        Method method = SecurityAutoConfiguration.class.getMethod(
                "securityWebFilterChain",
                org.springframework.security.config.web.server.ServerHttpSecurity.class,
                io.github.guanxiangkai.web.plus.core.properties.TrustedForwardProperties.class,
                org.springframework.beans.factory.ObjectProvider.class,
                io.github.guanxiangkai.web.plus.security.handler.CustomAuthenticationEntryPoint.class,
                io.github.guanxiangkai.web.plus.security.handler.CustomAccessDeniedHandler.class
        );

        ConditionalOnMissingClass annotation = method.getAnnotation(ConditionalOnMissingClass.class);

        assertThat(annotation).isNotNull();
        assertThat(annotation.value()).containsExactly("org.springframework.cloud.gateway.filter.GlobalFilter");
    }

    @Test
    void shouldCreatePermissionAspectOnlyWhenAspectjIsPresent() throws NoSuchMethodException {
        Method method = SecurityAutoConfiguration.class.getMethod(
                "authPermissionAspect",
                io.github.guanxiangkai.web.plus.security.spi.PermissionResolver.class,
                io.github.guanxiangkai.web.plus.core.spi.CurrentUserProvider.class
        );

        ConditionalOnClass annotation = method.getAnnotation(ConditionalOnClass.class);

        assertThat(annotation).isNotNull();
        assertThat(annotation.name()).containsExactly("org.aspectj.lang.ProceedingJoinPoint");
    }

    @Test
    void shouldProvideHeaderSecurityPermissionDependencies() {
        SecurityAutoConfiguration configuration = new SecurityAutoConfiguration();
        var currentUserProvider = configuration.currentUserProvider();
        var permissionResolver = configuration.permissionResolver();
        CurrentUser user = new CurrentUser(
                "user-1", "测试用户", "tenant-1", null,
                Set.of(), Set.of("operator"), Set.of("agent:definition:list"),
                false, null, 0L, Map.of()
        );
        CurrentUser superAdmin = new CurrentUser(
                "admin-1", "超级管理员", "tenant-1", null,
                Set.of(), Set.of(), Set.of(), true, null, 0L, Map.of()
        );

        assertThat(currentUserProvider).isInstanceOf(ReactiveCurrentUserProvider.class);
        assertThat(permissionResolver.hasRole(user, "operator")).isTrue();
        assertThat(permissionResolver.hasPermission(user, "agent:definition:list")).isTrue();
        assertThat(permissionResolver.hasPermission(user, "agent:definition:edit")).isFalse();
        assertThat(permissionResolver.hasPermission(superAdmin, "agent:definition:edit")).isTrue();
    }

    @Test
    void shouldRestoreCurrentUserForSynchronousPermissionChecks() {
        SecurityAutoConfiguration configuration = new SecurityAutoConfiguration();
        configuration.enableReactorContextPropagation();
        CurrentUser expected = CurrentUser.ofUserId("user-1");

        CurrentUser actual = Mono.defer(() -> Mono.justOrEmpty(CurrentUserHolder.get()))
                .subscribeOn(Schedulers.boundedElastic())
                .contextWrite(context -> context.put(CurrentUserHolder.REACTOR_CONTEXT_KEY, expected))
                .block();

        assertThat(actual).isEqualTo(expected);
        assertThat(CurrentUserHolder.get()).isNull();
    }
}
