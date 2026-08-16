package io.github.guanxiangkai.web.plus.security.filter;

import io.github.guanxiangkai.web.plus.core.constant.WebPlusConstants;
import io.github.guanxiangkai.web.plus.core.context.CurrentUser;
import io.github.guanxiangkai.web.plus.security.properties.AuthProperties;
import io.github.guanxiangkai.web.plus.security.service.TokenService;
import io.github.guanxiangkai.web.plus.security.spi.AuthorizationScope;
import io.github.guanxiangkai.web.plus.security.spi.AuthorizationScopeProvider;
import io.github.guanxiangkai.web.plus.security.spi.TokenRevocationStore;
import org.junit.jupiter.api.Test;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.WebFilterChain;
import tools.jackson.databind.ObjectMapper;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TokenAuthenticationFilterTest {

    private final TokenService tokenService = mock(TokenService.class);
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final TokenRevocationStore revocationStore = new TokenRevocationStore() {
        @Override
        public void revoke(String token, long ttlMs) {
        }

        @Override
        public boolean isRevoked(String token) {
            return false;
        }
    };

    @Test
    void jwtAuthenticationLoadsAuthorizationScopeFromProvider() {
        TokenService jwtTokenService = mock(TokenService.class);
        when(jwtTokenService.parseCurrentUser("token")).thenReturn(Optional.of(new CurrentUser(
                "admin",
                "admin",
                "tenant-a",
                "dept-1",
                Set.of(),
                Set.of(),
                Set.of(),
                true,
                "web",
                System.currentTimeMillis(),
                Map.of()
        )));
        AuthorizationScopeProvider scopeProvider = (userId, superAdmin) -> new AuthorizationScope(
                Set.of("admin"),
                Set.of("sys:user:list"),
                Set.of("dept-1")
        );
        TokenAuthenticationFilter filter = new TokenAuthenticationFilter(
                jwtTokenService,
                revocationStore,
                scopeProvider,
                properties(false, List.of(), List.of()),
                objectMapper
        );
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/")
                        .header("Authorization", "Bearer token")
        );

        CurrentUser authentication = execute(filter, exchange);

        assertThat(authentication).isNotNull();
        assertThat(authentication.superAdmin()).isTrue();
        assertThat(authentication.roles()).containsExactly("admin");
        assertThat(authentication.permissions()).containsExactly("sys:user:list");
        assertThat(authentication.deptIds()).containsExactly("dept-1");
    }

    @Test
    void gatewayPassthrough_acceptsTrustedGatewayThroughTrustedProxy() {
        TokenAuthenticationFilter filter = new TokenAuthenticationFilter(
                tokenService,
                revocationStore,
                properties(true, List.of("127.0.0.1"), List.of("203.0.113.2")),
                objectMapper
        );
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/")
                        .header(WebPlusConstants.USER_ID_HEADER, "admin")
                        .header(WebPlusConstants.USER_CLAIMS_HEADER, userClaims("admin"))
                        .header("X-Forwarded-For", "127.0.0.1")
                        .remoteAddress(new InetSocketAddress("203.0.113.2", 8080))
        );

        CurrentUser authentication = execute(filter, exchange);

        assertThat(authentication).isNotNull();
        assertThat(authentication.userId()).isEqualTo("admin");
    }

    @Test
    void gatewayPassthrough_decodesBase64UrlClaimsAndPreservesChineseNickname() {
        TokenAuthenticationFilter filter = new TokenAuthenticationFilter(
                tokenService,
                revocationStore,
                properties(true, List.of("203.0.113.1"), List.of()),
                objectMapper
        );
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/")
                        .header(WebPlusConstants.USER_ID_HEADER, "admin")
                        .header(WebPlusConstants.USER_CLAIMS_HEADER, encodedUserClaims("admin", "管理员"))
                        .header(WebPlusConstants.USER_CLAIMS_ENCODING_HEADER, "base64url")
                        .remoteAddress(new InetSocketAddress("203.0.113.1", 8080))
        );

        CurrentUser authentication = execute(filter, exchange);

        assertThat(authentication).isNotNull();
        assertThat(authentication.userId()).isEqualTo("admin");
        assertThat(authentication.nickname()).isEqualTo("管理员");
    }

    @Test
    void gatewayPassthrough_rejectsSpoofedForwardedHeadersFromUntrustedPeer() {
        TokenAuthenticationFilter filter = new TokenAuthenticationFilter(
                tokenService,
                revocationStore,
                properties(true, List.of("127.0.0.1"), List.of("203.0.113.3")),
                objectMapper
        );
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/")
                        .header(WebPlusConstants.USER_ID_HEADER, "admin")
                        .header(WebPlusConstants.USER_CLAIMS_HEADER, userClaims("admin"))
                        .header("X-Forwarded-For", "127.0.0.1")
                        .remoteAddress(new InetSocketAddress("203.0.113.2", 8080))
        );

        CurrentUser authentication = execute(filter, exchange);

        assertThat(authentication).isNull();
    }

    @Test
    void gatewayPassthrough_acceptsConfiguredPeerIp() {
        TokenAuthenticationFilter filter = new TokenAuthenticationFilter(
                tokenService,
                revocationStore,
                properties(true, List.of("203.0.113.1"), List.of()),
                objectMapper
        );
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/")
                        .header(WebPlusConstants.USER_ID_HEADER, "admin")
                        .header(WebPlusConstants.USER_CLAIMS_HEADER, userClaims("admin"))
                        .remoteAddress(new InetSocketAddress("203.0.113.1", 8080))
        );

        CurrentUser authentication = execute(filter, exchange);

        assertThat(authentication).isNotNull();
        assertThat(authentication.userId()).isEqualTo("admin");
    }

    @Test
    void gatewayPassthrough_rejectsMissingClaimsHeader() {
        TokenAuthenticationFilter filter = new TokenAuthenticationFilter(
                tokenService,
                revocationStore,
                properties(true, List.of("203.0.113.1"), List.of()),
                objectMapper
        );
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/")
                        .header(WebPlusConstants.USER_ID_HEADER, "admin")
                        .remoteAddress(new InetSocketAddress("203.0.113.1", 8080))
        );

        CurrentUser authentication = execute(filter, exchange);

        assertThat(authentication).isNull();
    }

    @Test
    void gatewayPassthrough_rejectsClaimsMismatch() {
        TokenAuthenticationFilter filter = new TokenAuthenticationFilter(
                tokenService,
                revocationStore,
                properties(true, List.of("203.0.113.1"), List.of()),
                objectMapper
        );
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/")
                        .header(WebPlusConstants.USER_ID_HEADER, "admin")
                        .header(WebPlusConstants.USER_CLAIMS_HEADER, userClaims("guest"))
                        .remoteAddress(new InetSocketAddress("203.0.113.1", 8080))
        );

        CurrentUser authentication = execute(filter, exchange);

        assertThat(authentication).isNull();
    }

    @Test
    void gatewayPassthrough_rejectsForwardedChainWhenTrustedGatewayIsNotClosestNonProxyHop() {
        TokenAuthenticationFilter filter = new TokenAuthenticationFilter(
                tokenService,
                revocationStore,
                properties(true, List.of("127.0.0.1"), List.of("203.0.113.2")),
                objectMapper
        );
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/")
                        .header(WebPlusConstants.USER_ID_HEADER, "admin")
                        .header(WebPlusConstants.USER_CLAIMS_HEADER, userClaims("admin"))
                        .header("X-Forwarded-For", "127.0.0.1, 198.51.100.200")
                        .remoteAddress(new InetSocketAddress("203.0.113.2", 8080))
        );

        CurrentUser authentication = execute(filter, exchange);

        assertThat(authentication).isNull();
    }

    @Test
    void gatewayPassthrough_acceptsIpv6LoopbackConfiguredInShortForm() throws Exception {
        TokenAuthenticationFilter filter = new TokenAuthenticationFilter(
                tokenService,
                revocationStore,
                properties(true, List.of("::1"), List.of()),
                objectMapper
        );
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/")
                        .header(WebPlusConstants.USER_ID_HEADER, "admin")
                        .header(WebPlusConstants.USER_CLAIMS_HEADER, userClaims("admin"))
                        .remoteAddress(new InetSocketAddress(InetAddress.getByName("::1"), 8080))
        );

        CurrentUser authentication = execute(filter, exchange);

        assertThat(authentication).isNotNull();
        assertThat(authentication.userId()).isEqualTo("admin");
    }

    @Test
    void gatewayPassthrough_ignoresAuthorizationClaimsAndLoadsScopeFromProvider() {
        AuthorizationScopeProvider scopeProvider = (userId, superAdmin) -> new AuthorizationScope(
                Set.of("admin"),
                Set.of("sys:user:list"),
                Set.of("dept-1")
        );
        TokenAuthenticationFilter filter = new TokenAuthenticationFilter(
                tokenService,
                revocationStore,
                scopeProvider,
                properties(true, List.of("203.0.113.1"), List.of()),
                objectMapper
        );
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/")
                        .header(WebPlusConstants.USER_ID_HEADER, "admin")
                        .header(WebPlusConstants.USER_CLAIMS_HEADER,
                                "{\"userId\":\"admin\",\"permissions\":{\"bad\":true}}")
                        .remoteAddress(new InetSocketAddress("203.0.113.1", 8080))
        );

        CurrentUser authentication = execute(filter, exchange);

        assertThat(authentication).isNotNull();
        assertThat(authentication.roles()).containsExactly("admin");
        assertThat(authentication.permissions()).containsExactly("sys:user:list");
        assertThat(authentication.deptIds()).containsExactly("dept-1");
    }

    private CurrentUser execute(TokenAuthenticationFilter filter, MockServerWebExchange exchange) {
        AtomicReference<CurrentUser> authenticationRef = new AtomicReference<>();
        WebFilterChain chain = ignored -> reactor.core.publisher.Mono.deferContextual(ctx -> {
                    authenticationRef.set(ctx.getOrDefault(
                            io.github.guanxiangkai.web.plus.security.context.CurrentUserThreadLocalAccessor.KEY, null));
                    return reactor.core.publisher.Mono.just(Boolean.TRUE);
                })
                .switchIfEmpty(reactor.core.publisher.Mono.fromRunnable(() -> authenticationRef.set(null)))
                .then();
        filter.filter(exchange, chain).block();
        return authenticationRef.get();
    }

    private AuthProperties properties(boolean passthroughEnabled,
                                      List<String> trustedIps,
                                      List<String> trustedProxyIps) {
        return new AuthProperties(
                true,
                "JWT",
                "Authorization",
                "Bearer ",
                "0123456789abcdef0123456789abcdef",
                7_200_000L,
                false,
                List.of("/public/**"),
                passthroughEnabled,
                trustedIps,
                trustedProxyIps
        );
    }

    private String userClaims(String userId) {
        try {
            return objectMapper.writeValueAsString(new CurrentUser(
                    userId,
                    userId,
                    "tenant-a",
                    "dept-1",
                    java.util.Set.of("dept-1"),
                    java.util.Set.of("admin"),
                    java.util.Set.of("sys:user:list"),
                    false,
                    "web",
                    System.currentTimeMillis(),
                    java.util.Map.of("source", "gateway")
            ));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private String encodedUserClaims(String userId, String nickname) {
        try {
            String json = objectMapper.writeValueAsString(new CurrentUser(
                    userId,
                    nickname,
                    "tenant-a",
                    "dept-1",
                    java.util.Set.of("dept-1"),
                    java.util.Set.of("admin"),
                    java.util.Set.of("sys:user:list"),
                    false,
                    "web",
                    System.currentTimeMillis(),
                    java.util.Map.of("source", "gateway")
            ));
            return Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(json.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
