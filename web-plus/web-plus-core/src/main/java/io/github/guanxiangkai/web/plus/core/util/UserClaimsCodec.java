package io.github.guanxiangkai.web.plus.core.util;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * Encodes user claims for HTTP header forwarding.
 */
public final class UserClaimsCodec {

    public static final String BASE64URL_ENCODING = "base64url";

    private UserClaimsCodec() {
    }

    public static String encode(String claimsJson) {
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(claimsJson.getBytes(StandardCharsets.UTF_8));
    }

    public static String decode(String rawClaims, String encoding) {
        if (BASE64URL_ENCODING.equalsIgnoreCase(encoding)) {
            return new String(Base64.getUrlDecoder().decode(rawClaims), StandardCharsets.UTF_8);
        }
        String trimmed = rawClaims.trim();
        if (trimmed.startsWith("{") || trimmed.startsWith("[")) {
            return rawClaims;
        }
        try {
            return new String(Base64.getUrlDecoder().decode(rawClaims), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException ignored) {
            return rawClaims;
        }
    }
}
