package io.github.guanxiangkai.web.plus.security.filter;

import cn.hutool.json.JSONUtil;
import io.github.guanxiangkai.web.plus.core.constants.AuthConstants;
import io.github.guanxiangkai.web.plus.core.context.CurrentUser;
import io.github.guanxiangkai.web.plus.core.context.CurrentUserHolder;
import io.github.guanxiangkai.web.plus.core.properties.TrustedForwardProperties;
import io.github.guanxiangkai.web.plus.security.authorization.AuthorizationScope;
import io.github.guanxiangkai.web.plus.security.context.UserContext;
import io.github.guanxiangkai.web.plus.security.context.UserContextThreadLocalAccessor;
import org.junit.jupiter.api.Test;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class HeaderAuthenticationFilterTest {

    @Test
    void shouldAuthenticateInternalServiceWhenTrustedTokenAndExplicitPrincipalExist() {
        TrustedForwardProperties properties = trustedForwardProperties();
        HeaderAuthenticationFilter filter = new HeaderAuthenticationFilter(properties);
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/internal/user/findByUsername")
                        .header(properties.getHeaderName(), properties.getToken())
                        .header(AuthConstants.HeaderConstants.USER_ID,
                                AuthConstants.HeaderConstants.INTERNAL_SERVICE_USER_ID)
                        .build()
        );

        AtomicReference<String> principalRef = new AtomicReference<>();
        AtomicReference<Boolean> authenticatedRef = new AtomicReference<>();
        AtomicReference<UserContext> userContextRef = new AtomicReference<>();

        WebFilterChain chain = webExchange -> ReactiveSecurityContextHolder.getContext()
                .doOnNext(context -> {
                    principalRef.set(context.getAuthentication().getName());
                    authenticatedRef.set(context.getAuthentication().isAuthenticated());
                })
                .then(Mono.deferContextual(ctx -> {
                    userContextRef.set(ctx.get(UserContextThreadLocalAccessor.KEY));
                    return Mono.empty();
                }));

        filter.filter(exchange, chain).block();

        assertThat(principalRef.get()).isEqualTo(AuthConstants.HeaderConstants.INTERNAL_SERVICE_USER_ID);
        assertThat(authenticatedRef.get()).isTrue();
        assertThat(userContextRef.get()).isNotNull();
        assertThat(userContextRef.get().userId()).isEqualTo(AuthConstants.HeaderConstants.INTERNAL_SERVICE_USER_ID);
    }

    @Test
    void shouldNotCreateAnIdentityFromTrustedTokenAlone() {
        TrustedForwardProperties properties = trustedForwardProperties();
        HeaderAuthenticationFilter filter = new HeaderAuthenticationFilter(properties);
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.post("/auth/login")
                        .header(properties.getHeaderName(), properties.getToken())
                        .header(AuthConstants.HeaderConstants.VERIFIED_CLIENT_IP, "203.0.113.8")
                        .build()
        );
        AtomicBoolean chainInvoked = new AtomicBoolean(false);
        AtomicReference<String> principalRef = new AtomicReference<>();

        filter.filter(exchange, webExchange -> {
            chainInvoked.set(true);
            return ReactiveSecurityContextHolder.getContext()
                    .doOnNext(context -> principalRef.set(context.getAuthentication().getName()))
                    .then();
        }).block();

        assertThat(chainInvoked).isTrue();
        assertThat(principalRef.get()).isNull();
    }

    @Test
    void shouldPassThroughWhenNoTrustedHeadersProvided() {
        TrustedForwardProperties properties = trustedForwardProperties();
        HeaderAuthenticationFilter filter = new HeaderAuthenticationFilter(properties);
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/public/ping").build()
        );

        AtomicBoolean chainInvoked = new AtomicBoolean(false);
        AtomicReference<String> principalRef = new AtomicReference<>();

        WebFilterChain chain = webExchange -> {
            chainInvoked.set(true);
            return ReactiveSecurityContextHolder.getContext()
                    .doOnNext(context -> principalRef.set(context.getAuthentication().getName()))
                    .then();
        };

        filter.filter(exchange, chain).block();

        assertThat(chainInvoked).isTrue();
        assertThat(principalRef.get()).isNull();
    }

    @Test
    void shouldRejectForgedUserHeaderWithoutTrustedToken() {
        TrustedForwardProperties properties = trustedForwardProperties();
        HeaderAuthenticationFilter filter = new HeaderAuthenticationFilter(properties);
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/internal/user/findByUsername")
                        .header(AuthConstants.HeaderConstants.USER_ID, "1001")
                        .build()
        );

        AtomicBoolean chainInvoked = new AtomicBoolean(false);
        WebFilterChain chain = webExchange -> {
            chainInvoked.set(true);
            return Mono.empty();
        };

        filter.filter(exchange, chain).block();

        assertThat(chainInvoked).isFalse();
        assertThat(exchange.getResponse().getStatusCode()).isNotNull();
        assertThat(exchange.getResponse().getStatusCode().value()).isEqualTo(401);
    }

    @Test
    void shouldExposePermissionsAsGrantedAuthorities() {
        TrustedForwardProperties properties = trustedForwardProperties();
        HeaderAuthenticationFilter filter = new HeaderAuthenticationFilter(properties,
                (userId, superAdmin) -> new AuthorizationScope(
                        Set.of(),
                        Set.of("system:menu:list", "system:import-template:list"),
                        Set.of()
                ));
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/system/menu/page")
                        .header(properties.getHeaderName(), properties.getToken())
                        .header(AuthConstants.HeaderConstants.USER_ID, "1001")
                        .header(AuthConstants.HeaderConstants.USER_CLAIMS,
                                """
                                        {"superAdmin":false}
                                        """)
                        .build()
        );

        AtomicReference<Set<String>> authoritiesRef = new AtomicReference<>();
        AtomicReference<UserContext> userContextRef = new AtomicReference<>();

        WebFilterChain chain = webExchange -> ReactiveSecurityContextHolder.getContext()
                .doOnNext(context -> authoritiesRef.set(
                        context.getAuthentication().getAuthorities().stream()
                                .map(Object::toString)
                                .collect(Collectors.toSet())))
                .then(Mono.deferContextual(ctx -> {
                    userContextRef.set(ctx.get(UserContextThreadLocalAccessor.KEY));
                    return Mono.empty();
                }));

        filter.filter(exchange, chain).block();

        assertThat(authoritiesRef.get()).contains(
                "system:menu:list",
                "system:import-template:list"
        );
        assertThat(userContextRef.get().permissions()).containsExactlyInAnyOrder(
                "system:menu:list",
                "system:import-template:list"
        );
    }

    @Test
    void shouldDecodeBase64UrlClaimsAndPreserveChineseNickname() {
        TrustedForwardProperties properties = trustedForwardProperties();
        HeaderAuthenticationFilter filter = new HeaderAuthenticationFilter(properties);
        String claimsJson = JSONUtil.toJsonStr(Map.of(
                "nickname", "管理员",
                "superAdmin", false
        ));
        String encodedClaims = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(claimsJson.getBytes(StandardCharsets.UTF_8));
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/system/user/profile")
                        .header(properties.getHeaderName(), properties.getToken())
                        .header(AuthConstants.HeaderConstants.USER_ID, "1001")
                        .header(AuthConstants.HeaderConstants.USER_CLAIMS, encodedClaims)
                        .header(AuthConstants.HeaderConstants.USER_CLAIMS_ENCODING, "base64url")
                        .build()
        );

        AtomicReference<UserContext> userContextRef = new AtomicReference<>();
        AtomicReference<CurrentUser> currentUserRef = new AtomicReference<>();
        WebFilterChain chain = webExchange -> Mono.deferContextual(ctx -> {
            userContextRef.set(ctx.get(UserContextThreadLocalAccessor.KEY));
            currentUserRef.set(ctx.get(CurrentUserHolder.REACTOR_CONTEXT_KEY));
            return Mono.empty();
        });

        filter.filter(exchange, chain).block();

        assertThat(userContextRef.get()).isNotNull();
        assertThat(userContextRef.get().claims().get("nickname")).isEqualTo("管理员");
        assertThat(currentUserRef.get()).isNotNull();
        assertThat(currentUserRef.get().nickname()).isEqualTo("管理员");
    }

    private TrustedForwardProperties trustedForwardProperties() {
        TrustedForwardProperties properties = new TrustedForwardProperties();
        properties.setToken("test-trusted-token");
        return properties;
    }
}
