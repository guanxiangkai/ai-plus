package io.github.guanxiangkai.web.plus.license;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 已签名许可证中唯一有效的业务声明。
 *
 * <p>在线许可证的 nonce 由调用方为每个请求生成并保存，内核只接受受限的 URL 安全随机值；
 * 离线许可证必须使用空字符串。集合在构造时防御复制。</p>
 */
public record LicenseClaims(
        String id,
        String issuer,
        String subject,
        String product,
        String instance,
        LicenseMode mode,
        Instant issuedAt,
        Instant notBefore,
        Instant expiresAt,
        Set<String> features,
        String nonce) {

    private static final int MAX_TEXT_LENGTH = 256;
    private static final int MAX_FEATURES = 64;
    private static final int MAX_FEATURE_LENGTH = 128;
    private static final Duration MAX_ONLINE_LIFETIME = Duration.ofMinutes(15);
    private static final Pattern NONCE_PATTERN = Pattern.compile("[A-Za-z0-9_-]{16,256}");

    public LicenseClaims {
        id = requiredText(id, "id", MAX_TEXT_LENGTH);
        issuer = requiredText(issuer, "issuer", MAX_TEXT_LENGTH);
        subject = requiredText(subject, "subject", MAX_TEXT_LENGTH);
        product = requiredText(product, "product", MAX_TEXT_LENGTH);
        instance = requiredText(instance, "instance", MAX_TEXT_LENGTH);
        mode = Objects.requireNonNull(mode, "mode 不能为空");
        issuedAt = Objects.requireNonNull(issuedAt, "issuedAt 不能为空");
        notBefore = Objects.requireNonNull(notBefore, "notBefore 不能为空");
        expiresAt = Objects.requireNonNull(expiresAt, "expiresAt 不能为空");
        if (issuedAt.isAfter(notBefore) || !notBefore.isBefore(expiresAt)) {
            throw new IllegalArgumentException("许可证时间顺序无效");
        }
        features = copyFeatures(features);
        nonce = validateNonce(mode, nonce);
        if (mode == LicenseMode.ONLINE && Duration.between(issuedAt, expiresAt).compareTo(MAX_ONLINE_LIFETIME) > 0) {
            throw new IllegalArgumentException("在线许可证有效期不得超过 15 分钟");
        }
    }

    private static String requiredText(String value, String name, int maxLength) {
        if (value == null || value.isBlank() || value.length() > maxLength) {
            throw new IllegalArgumentException(name + " 必须为不超过 " + maxLength + " 个字符的非空文本");
        }
        return value;
    }

    private static Set<String> copyFeatures(Set<String> values) {
        if (values == null || values.size() > MAX_FEATURES) {
            throw new IllegalArgumentException("features 数量无效");
        }
        var copied = new LinkedHashSet<String>();
        for (String feature : values) {
            copied.add(requiredText(feature, "feature", MAX_FEATURE_LENGTH));
        }
        return Set.copyOf(copied);
    }

    private static String validateNonce(LicenseMode mode, String value) {
        if (value == null) {
            throw new IllegalArgumentException("nonce 不能为空");
        }
        if (mode == LicenseMode.OFFLINE && value.isEmpty()) {
            return value;
        }
        if (mode == LicenseMode.ONLINE && NONCE_PATTERN.matcher(value).matches()) {
            return value;
        }
        throw new IllegalArgumentException("nonce 与许可证模式不匹配");
    }
}
