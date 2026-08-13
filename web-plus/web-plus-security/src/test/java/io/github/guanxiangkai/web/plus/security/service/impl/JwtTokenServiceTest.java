package io.github.guanxiangkai.web.plus.security.service.impl;

import io.github.guanxiangkai.web.plus.core.context.CurrentUser;
import io.github.guanxiangkai.web.plus.security.properties.AuthProperties;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtTokenServiceTest {

    @Test
    void rejectsMissingWeakAndShortSecrets() {
        assertThatThrownBy(() -> new JwtTokenService(properties(null, List.of("127.0.0.1"))))
                .hasMessageContaining("显式配置 web-plus.auth.jwt-secret");

        assertThatThrownBy(() -> new JwtTokenService(properties("default-secret-0123456789abcdef0123456789", List.of("127.0.0.1"))))
                .hasMessageContaining("弱密钥");

        assertThatThrownBy(() -> new JwtTokenService(properties("short-secret", List.of("127.0.0.1"))))
                .hasMessageContaining("长度不足 32 字节");
    }

    @Test
    void createsAndParsesAccessTokensWithConfiguredSecret() {
        JwtTokenService tokenService = new JwtTokenService(
                properties("c6Y8!rV2z@pM5#tQ9$wX3&kL7*eN1%aD4^hJ", List.of("127.0.0.1"))
        );

        CurrentUser user = new CurrentUser(
                "u1001",
                "alice",
                "tenant-a",
                "dept-1",
                Set.of("dept-1"),
                Set.of("admin"),
                Set.of("sys:user:list"),
                true,
                "web",
                System.currentTimeMillis(),
                Map.of("custom", "value")
        );
        String token = tokenService.createAccessToken(user);

        CurrentUser parsed = tokenService.parseCurrentUser(token).orElseThrow();
        assertThat(parsed.userId()).isEqualTo("u1001");
        assertThat(parsed.nickname()).isEqualTo("alice");
        assertThat(parsed.superAdmin()).isTrue();
        assertThat(parsed.roles()).isEmpty();
        assertThat(parsed.permissions()).isEmpty();
        assertThat(tokenService.parseToken(token).orElseThrow())
                .containsEntry("nickname", "alice")
                .doesNotContainKeys("username", "roles", "permissions", "deptIds");
    }

    @Test
    void rejectsNonAccessTokensAsCurrentUserCredentials() {
        JwtTokenService tokenService = new JwtTokenService(
                properties("c6Y8!rV2z@pM5#tQ9$wX3&kL7*eN1%aD4^hJ", List.of("127.0.0.1"))
        );

        SecretKey secretKey = Keys.hmacShaKeyFor("c6Y8!rV2z@pM5#tQ9$wX3&kL7*eN1%aD4^hJ".getBytes(StandardCharsets.UTF_8));
        Date now = new Date();
        String refreshToken = Jwts.builder()
                .subject("u1001")
                .claim("type", "refresh")
                .issuedAt(now)
                .expiration(new Date(now.getTime() + 60_000L))
                .signWith(secretKey)
                .compact();

        assertThat(tokenService.parseToken(refreshToken).orElseThrow())
                .containsEntry("type", "refresh");
        assertThat(tokenService.parseCurrentUser(refreshToken)).isEmpty();
    }

    private AuthProperties properties(String jwtSecret, List<String> trustedIps) {
        return new AuthProperties(
                true,
                "JWT",
                "Authorization",
                "Bearer ",
                jwtSecret,
                7_200_000L,
                false,
                List.of("/public/**"),
                true,
                trustedIps,
                List.of()
        );
    }
}
