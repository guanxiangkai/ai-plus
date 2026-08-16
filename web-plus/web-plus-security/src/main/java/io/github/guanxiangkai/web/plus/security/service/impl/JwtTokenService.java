package io.github.guanxiangkai.web.plus.security.service.impl;

import io.github.guanxiangkai.web.plus.core.context.CurrentUser;
import io.github.guanxiangkai.web.plus.security.properties.AuthProperties;
import io.github.guanxiangkai.web.plus.security.service.TokenService;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * JWT 无状态 Token 服务实现
 * <p>
 * 使用 JJWT 0.13.x API 签发和解析 JWT Token。
 * 密钥来自配置 {@code web-plus.auth.jwt-secret}。
 * </p>
 *
 * @author guanxiangkai
 * @since 1.0.0
 */
@Slf4j
public class JwtTokenService implements TokenService {

    private static final String TOKEN_TYPE_CLAIM = "type";
    private static final String ACCESS_TOKEN_TYPE = "access";
    private static final Set<String> WEAK_SECRET_MARKERS = Set.of("default", "example", "change", "production");

    private final AuthProperties properties;
    private final SecretKey secretKey;

    public JwtTokenService(AuthProperties properties) {
        this.properties = properties;
        String secret = properties.jwtSecret();
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException(
                    "[web-plus] JWT 模式要求显式配置 web-plus.auth.jwt-secret，且密钥长度必须至少 32 字节。");
        }
        byte[] secretBytes = secret.getBytes(StandardCharsets.UTF_8);
        if (secretBytes.length < 32) {
            throw new IllegalStateException(
                    "[web-plus] web-plus.auth.jwt-secret 长度不足 32 字节（当前 " + secretBytes.length +
                    " 字节），无法满足 HMAC-SHA256 安全要求，请配置更长的密钥。");
        }
        if (containsWeakMarker(secret)) {
            throw new IllegalStateException(
                    "[web-plus] web-plus.auth.jwt-secret 包含默认值或示例值标记，属于弱密钥。请配置仅在部署环境可见的随机强密钥。");
        }
        this.secretKey = Keys.hmacShaKeyFor(secretBytes);
    }

    private static final Set<String> STANDARD_CLAIM_KEYS = Set.of(
            "sub", TOKEN_TYPE_CLAIM, "nickname", "tenantId", "deptId",
            "deptIds", "roles", "permissions", "posts",
            "superAdmin", "deviceType", "loginTime", "iat", "exp");

    private static String firstText(Object... values) {
        for (Object value : values) {
            if (value != null && !value.toString().isBlank()) {
                return value.toString();
            }
        }
        return null;
    }

    @Override
    public Optional<Map<String, Object>> parseToken(String token) {
        return parseClaims(token).<Map<String, Object>>map(HashMap::new);
    }

    private Optional<Claims> parseClaims(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(secretKey)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            return Optional.of(claims);
        } catch (JwtException | IllegalArgumentException e) {
            log.debug("Token 解析失败: exception={}", e.getClass().getSimpleName());
            return Optional.empty();
        }
    }

    @Override
    public String createAccessToken(CurrentUser user) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + properties.accessTokenExpireMs());

        return Jwts.builder()
                .subject(user.userId())
                .claim(TOKEN_TYPE_CLAIM, ACCESS_TOKEN_TYPE)
                .claim("nickname", user.nickname())
                .claim("tenantId", user.tenantId())
                .claim("deptId", user.deptId())
                .claim("superAdmin", user.superAdmin())
                .claim("deviceType", user.deviceType())
                .claim("loginTime", user.loginTime())
                .issuedAt(now)
                .expiration(expiry)
                .signWith(secretKey)
                .compact();
    }

    @Override
    public Optional<CurrentUser> parseCurrentUser(String token) {
        return parseClaims(token)
                .filter(this::isAccessTokenClaims)
                .map(claims -> {
            String userId = (String) claims.get("sub");
            long loginTime = claims.get("loginTime") instanceof Number n
                    ? n.longValue() : System.currentTimeMillis();

            Map<String, Object> extra = claims.entrySet().stream()
                    .filter(e -> !STANDARD_CLAIM_KEYS.contains(e.getKey()))
                    .collect(java.util.stream.Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

            return new CurrentUser(
                    userId,
                    firstText(claims.get("nickname")),
                    (String) claims.get("tenantId"),
                    (String) claims.get("deptId"),
                    Set.of(),
                    Set.of(),
                    Set.of(),
                    Boolean.TRUE.equals(claims.get("superAdmin")),
                    (String) claims.get("deviceType"),
                    loginTime,
                    extra
            );
        });
    }

    private boolean isAccessTokenClaims(Map<String, Object> claims) {
        Object type = claims.get(TOKEN_TYPE_CLAIM);
        boolean accessToken = type != null && ACCESS_TOKEN_TYPE.equals(type.toString());
        if (!accessToken) {
            log.debug("拒绝将非 access token 用作登录态凭证: type={}", type);
        }
        return accessToken;
    }

    private boolean containsWeakMarker(String secret) {
        String normalizedSecret = secret.toLowerCase(Locale.ROOT);
        return WEAK_SECRET_MARKERS.stream().anyMatch(normalizedSecret::contains);
    }

}
