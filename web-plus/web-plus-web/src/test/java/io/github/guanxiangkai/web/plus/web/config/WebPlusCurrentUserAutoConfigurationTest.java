package io.github.guanxiangkai.web.plus.web.config;

import io.github.guanxiangkai.web.plus.core.constants.AuthConstants;
import io.github.guanxiangkai.web.plus.core.context.CurrentUser;
import io.github.guanxiangkai.web.plus.core.context.CurrentUserHolder;
import io.github.guanxiangkai.web.plus.core.properties.TrustedForwardProperties;
import io.github.guanxiangkai.web.plus.core.spi.CurrentUserProvider;
import io.github.guanxiangkai.web.plus.security.authorization.AuthorizationScope;
import io.github.guanxiangkai.web.plus.security.context.UserContext;
import io.github.guanxiangkai.web.plus.security.context.UserContextHolder;
import io.github.guanxiangkai.web.plus.security.filter.HeaderAuthenticationFilter;
import io.github.guanxiangkai.web.plus.security.spi.PermissionResolver;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;
import reactor.util.context.Context;

import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class WebPlusCurrentUserAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(WebPlusCurrentUserAutoConfiguration.class));

    @Test
    void shouldProvidePrimaryBridgeProviderWhenExistingProviderExists() {
        contextRunner
                .withBean("defaultCurrentUserProvider", CurrentUserProvider.class, () -> new CurrentUserProvider() {
                    @Override
                    public Optional<CurrentUser> getCurrentUser() {
                        return Optional.empty();
                    }

                    @Override
                    public Mono<Optional<CurrentUser>> getCurrentUserMono() {
                        return Mono.just(Optional.empty());
                    }
                })
                .run(context -> {
                    CurrentUserProvider provider = context.getBean(CurrentUserProvider.class);

                    assertThat(provider)
                            .isInstanceOf(WebPlusCurrentUserAutoConfiguration.ReactiveBridgeCurrentUserProvider.class);
                    assertThat(context.getBeansOfType(CurrentUserProvider.class)).hasSize(2);
                });
    }

    @Test
    void shouldReadCurrentUserFromReactorContext() {
        contextRunner.run(context -> {
            CurrentUserProvider provider = context.getBean(CurrentUserProvider.class);
            CurrentUser expected = new CurrentUser(
                    "user-123",
                    "alice",
                    "tenant-456",
                    "dept-1",
                    Set.of("dept-1", "dept-2"),
                    Set.of("ADMIN"),
                    Set.of("agent:query"),
                    false,
                    "WEB",
                    System.currentTimeMillis(),
                    Map.of("nickname", "alice")
            );

            Optional<CurrentUser> resolved = provider.getCurrentUserMono()
                    .contextWrite(Context.of(CurrentUserHolder.REACTOR_CONTEXT_KEY, expected))
                    .block();

            assertThat(resolved).contains(expected);
        });
    }

    @Test
    void shouldBridgeHeaderAuthenticationToWebPlusCurrentUserContext() {
        TrustedForwardProperties properties = new TrustedForwardProperties();
        properties.setToken("test-trusted-token");
        HeaderAuthenticationFilter filter = new HeaderAuthenticationFilter(properties,
                (userId, superAdmin) -> new AuthorizationScope(
                        Set.of("ADMIN"),
                        Set.of("system:user:list"),
                        Set.of("dept-1", "dept-2")
                ));
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/system/user/list")
                        .header(properties.getHeaderName(), properties.getToken())
                        .header(AuthConstants.HeaderConstants.USER_ID, "user-123")
                        .header(AuthConstants.HeaderConstants.TENANT_ID, "tenant-456")
                        .header(AuthConstants.HeaderConstants.USER_CLAIMS,
                                """
                                        {
                                          "nickname":"alice",
                                          "deptId":"dept-1",
                                          "superAdmin":true,
                                          "deviceType":"WEB"
                                        }
                                        """)
                        .build()
        );

        WebFilterChain chain = ignored -> Mono.deferContextual(ctx -> {
            CurrentUser currentUser = ctx.get(CurrentUserHolder.REACTOR_CONTEXT_KEY);

            assertThat(currentUser.userId()).isEqualTo("user-123");
            assertThat(currentUser.nickname()).isEqualTo("alice");
            assertThat(currentUser.tenantId()).isEqualTo("tenant-456");
            assertThat(currentUser.deptId()).isEqualTo("dept-1");
            assertThat(currentUser.deptIds()).containsExactlyInAnyOrder("dept-1", "dept-2");
            assertThat(currentUser.roles()).containsExactly("ADMIN");
            assertThat(currentUser.permissions()).containsExactly("system:user:list");
            assertThat(currentUser.superAdmin()).isTrue();
            assertThat(currentUser.deviceType()).isEqualTo("WEB");
            return Mono.empty();
        });

        filter.filter(exchange, chain).block();
    }

    @Test
    void shouldFallbackToUserContextHolderForSynchronousCalls() {
        contextRunner.run(context -> {
            CurrentUserProvider provider = context.getBean(CurrentUserProvider.class);
            UserContextHolder.set(new UserContext(
                    "user-123",
                    "tenant-456",
                    false,
                    "dept-1",
                    Set.of("dept-1"),
                    Set.of("ADMIN"),
                    Set.of("agent:query"),
                    Map.of("nickname", "alice")
            ));
            try {
                Optional<CurrentUser> resolved = provider.getCurrentUser();

                assertThat(resolved).isPresent();
                assertThat(resolved.orElseThrow().userId()).isEqualTo("user-123");
                assertThat(resolved.orElseThrow().roles()).containsExactly("ADMIN");
                assertThat(resolved.orElseThrow().permissions()).containsExactly("agent:query");
            } finally {
                UserContextHolder.clear();
            }
        });
    }

    @Test
    void shouldKeepExactPermissionAndRoleMatchingForNormalUsers() {
        contextRunner.run(context -> {
            PermissionResolver resolver = context.getBean(PermissionResolver.class);
            CurrentUser user = new CurrentUser(
                    "user-123",
                    "alice",
                    "tenant-456",
                    "dept-1",
                    Set.of(),
                    Set.of("USER"),
                    Set.of("agent:query"),
                    false,
                    "WEB",
                    System.currentTimeMillis(),
                    Map.of("superAdmin", false)
            );

            assertThat(resolver.hasPermission(user, "agent:query")).isTrue();
            assertThat(resolver.hasPermission(user, "system:user:list")).isFalse();
            assertThat(resolver.hasRole(user, "USER")).isTrue();
            assertThat(resolver.hasRole(user, "ADMIN")).isFalse();
        });
    }
}
