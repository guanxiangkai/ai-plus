package io.github.guanxiangkai.web.plus.license;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.JwtException;

import java.nio.charset.StandardCharsets;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * PS256 许可证的签发与本地验签。
 *
 * <p>实例只保存验签公钥和固定许可证上下文，不发起网络请求、不缓存时钟结果；在线 nonce 的生成、
 * 保存与单次使用由调用方负责。</p>
 */
public final class LicenseTokens {
    private static final String TYPE = "ai-plus-license+jwt";
    private static final String CLAIM_INSTANCE = "instance";
    private static final String CLAIM_MODE = "mode";
    private static final String CLAIM_FEATURES = "features";
    private static final String CLAIM_NONCE = "nonce";
    private static final int MAX_TOKEN_LENGTH = 64 * 1024;
    private static final int MAX_HEADER_BYTES = 1024;
    private static final Duration MAX_ONLINE_LIFETIME = Duration.ofMinutes(15);
    private static final Pattern HEADER = Pattern.compile(
            "\\A\\s*\\{\\s*(?:\\\"alg\\\"\\s*:\\s*\\\"PS256\\\"\\s*,\\s*\\\"typ\\\"\\s*:\\s*\\\"ai-plus-license\\+jwt\\\"|"
                    + "\\\"typ\\\"\\s*:\\s*\\\"ai-plus-license\\+jwt\\\"\\s*,\\s*\\\"alg\\\"\\s*:\\s*\\\"PS256\\\")\\s*}\\s*\\z");

    private final PublicKey key;
    private final String issuer;
    private final String subject;
    private final String product;
    private final String instance;
    private final LicenseMode mode;
    private final Clock clock;

    /**
     * 创建绑定到单一产品实例与许可证模式的验签器。
     */
    public LicenseTokens(
            PublicKey key,
            String issuer,
            String subject,
            String product,
            String instance,
            LicenseMode mode,
            Clock clock) {
        LicenseKeys.requireRsa3072(Objects.requireNonNull(key, "key 不能为空"));
        this.key = key;
        this.issuer = requiredContext(issuer, "issuer");
        this.subject = requiredContext(subject, "subject");
        this.product = requiredContext(product, "product");
        this.instance = requiredContext(instance, "instance");
        this.mode = Objects.requireNonNull(mode, "mode 不能为空");
        this.clock = Objects.requireNonNull(clock, "clock 不能为空");
    }

    /**
     * 使用至少 3072 位 RSA 私钥签发固定为 PS256 的紧凑 JWT。
     *
     * @param claims 完整许可证声明
     * @param key 仅签发端持有的 PKCS#8 RSA 私钥
     * @return 紧凑 JWT
     */
    public static String issue(LicenseClaims claims, PrivateKey key) {
        Objects.requireNonNull(claims, "claims 不能为空");
        LicenseKeys.requireRsa3072(Objects.requireNonNull(key, "key 不能为空"));
        try {
            String token = Jwts.builder()
                    .header().type(TYPE).and()
                    .id(claims.id())
                    .issuer(claims.issuer())
                    .subject(claims.subject())
                    .audience().add(claims.product()).and()
                    .issuedAt(Date.from(claims.issuedAt()))
                    .notBefore(Date.from(claims.notBefore()))
                    .expiration(Date.from(claims.expiresAt()))
                    .claim(CLAIM_INSTANCE, claims.instance())
                    .claim(CLAIM_MODE, claims.mode().name())
                    .claim(CLAIM_FEATURES, List.copyOf(claims.features()))
                    .claim(CLAIM_NONCE, claims.nonce())
                    .signWith(key, Jwts.SIG.PS256)
                    .compact();
            if (token.length() > MAX_TOKEN_LENGTH) {
                throw new LicenseException("许可证令牌长度无效");
            }
            return token;
        } catch (LicenseException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new LicenseException("许可证签发失败");
        }
    }

    /**
     * 验证 JWT 签名、固定上下文、完整声明和当前时间。
     *
     * @param token 不超过 64KiB 的紧凑 JWS
     * @param expectedNonce 本次在线请求保存的 nonce；离线模式必须传入空字符串
     * @return 经签名且通过上下文校验的声明
     * @throws LicenseException 令牌不符合许可证契约时抛出
     */
    public LicenseClaims verify(String token, String expectedNonce) {
        validateTokenHeader(token);
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(key)
                    .clock(() -> Date.from(clock.instant()))
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            LicenseClaims verified = toClaims(claims);
            validateContext(verified, expectedNonce);
            return verified;
        } catch (JwtException | IllegalArgumentException exception) {
            throw new LicenseException("许可证校验失败");
        }
    }

    private LicenseClaims toClaims(Claims claims) {
        String tokenIssuer = requiredString(claims, Claims.ISSUER);
        String tokenSubject = requiredString(claims, Claims.SUBJECT);
        String tokenId = requiredString(claims, Claims.ID);
        String tokenInstance = requiredString(claims, CLAIM_INSTANCE);
        String tokenMode = requiredString(claims, CLAIM_MODE);
        String tokenNonce = requiredString(claims, CLAIM_NONCE);
        Set<String> audiences = claims.getAudience();
        if (audiences == null || audiences.size() != 1) {
            throw new LicenseException("许可证 audience 无效");
        }
        String tokenProduct = audiences.iterator().next();
        if (tokenProduct == null || tokenProduct.isBlank()) {
            throw new LicenseException("许可证 audience 无效");
        }
        Object rawFeatures = claims.get(CLAIM_FEATURES);
        if (!(rawFeatures instanceof List<?> values)) {
            throw new LicenseException("许可证 features 无效");
        }
        var features = new java.util.LinkedHashSet<String>();
        for (Object value : values) {
            if (!(value instanceof String feature)) {
                throw new LicenseException("许可证 features 无效");
            }
            if (!features.add(feature)) {
                throw new LicenseException("许可证 features 无效");
            }
        }
        Instant issuedAt = requiredDate(claims.getIssuedAt(), "iat");
        Instant notBefore = requiredDate(claims.getNotBefore(), "nbf");
        Instant expiresAt = requiredDate(claims.getExpiration(), "exp");
        try {
            return new LicenseClaims(tokenId, tokenIssuer, tokenSubject, tokenProduct, tokenInstance,
                    LicenseMode.valueOf(tokenMode), issuedAt, notBefore, expiresAt, features, tokenNonce);
        } catch (IllegalArgumentException exception) {
            throw new LicenseException("许可证声明无效");
        }
    }

    private void validateContext(LicenseClaims claims, String expectedNonce) {
        Instant now = clock.instant();
        if (!issuer.equals(claims.issuer()) || !subject.equals(claims.subject())
                || !product.equals(claims.product()) || !instance.equals(claims.instance()) || mode != claims.mode()) {
            throw new LicenseException("许可证上下文不匹配");
        }
        if (claims.issuedAt().isAfter(now) || claims.notBefore().isAfter(now) || !now.isBefore(claims.expiresAt())) {
            throw new LicenseException("许可证不在有效期内");
        }
        if (mode == LicenseMode.ONLINE) {
            if (expectedNonce == null || !claims.nonce().equals(expectedNonce)) {
                throw new LicenseException("许可证 nonce 不匹配");
            }
            if (Duration.between(claims.issuedAt(), claims.expiresAt()).compareTo(MAX_ONLINE_LIFETIME) > 0) {
                throw new LicenseException("在线许可证有效期超过上限");
            }
        } else if (!"".equals(expectedNonce)) {
            throw new LicenseException("离线许可证 nonce 无效");
        }
    }

    private static String requiredContext(String value, String name) {
        if (value == null || value.isBlank() || value.length() > 256) {
            throw new IllegalArgumentException(name + " 必须为不超过 256 个字符的非空文本");
        }
        return value;
    }

    private static String requiredString(Claims claims, String name) {
        Object value = claims.get(name);
        if (!(value instanceof String text)) {
            throw new LicenseException("许可证 " + name + " 无效");
        }
        return text;
    }

    private static Instant requiredDate(Date value, String name) {
        if (value == null) {
            throw new LicenseException("许可证 " + name + " 缺失");
        }
        return value.toInstant();
    }

    private static void validateTokenHeader(String token) {
        if (token == null || token.isBlank() || token.length() > MAX_TOKEN_LENGTH) {
            throw new LicenseException("许可证令牌长度无效");
        }
        for (int index = 0; index < token.length(); index++) {
            char character = token.charAt(index);
            if (!(character >= 'A' && character <= 'Z') && !(character >= 'a' && character <= 'z')
                    && !(character >= '0' && character <= '9') && character != '-' && character != '_' && character != '.') {
                throw new LicenseException("许可证令牌编码无效");
            }
        }
        int firstDot = token.indexOf('.');
        int secondDot = firstDot < 0 ? -1 : token.indexOf('.', firstDot + 1);
        if (firstDot <= 0 || secondDot <= firstDot + 1 || secondDot == token.length() - 1
                || token.indexOf('.', secondDot + 1) >= 0) {
            throw new LicenseException("许可证必须是紧凑 JWS");
        }
        String encodedHeader = token.substring(0, firstDot);
        if (encodedHeader.length() > MAX_HEADER_BYTES * 2) {
            throw new LicenseException("许可证头部过长");
        }
        try {
            byte[] header = Base64.getUrlDecoder().decode(encodedHeader);
            if (header.length > MAX_HEADER_BYTES || !HEADER.matcher(new String(header, StandardCharsets.UTF_8)).matches()) {
                throw new LicenseException("许可证头部无效");
            }
        } catch (IllegalArgumentException exception) {
            throw new LicenseException("许可证头部编码无效");
        }
    }
}
