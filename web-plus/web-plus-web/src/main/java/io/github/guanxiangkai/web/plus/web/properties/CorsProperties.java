package io.github.guanxiangkai.web.plus.web.properties;

import io.github.guanxiangkai.web.plus.core.constant.WebPlusConstants;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * CORS 跨域配置属性
 *
 * @author guanxiangkai
 * @since 1.0.0
 */
@ConfigurationProperties(prefix = "web-plus.cors")
public record CorsProperties(
        Boolean enabled,
        List<String> allowedOrigins,
        List<String> allowedOriginPatterns,
        List<String> allowedMethods,
        List<String> allowedHeaders,
        List<String> exposedHeaders,
        Boolean allowCredentials,
        Long maxAge
) {
    public CorsProperties {
        if (enabled == null) enabled = true;
        if (allowedOrigins == null) allowedOrigins = List.of();
        if (allowedOriginPatterns == null) allowedOriginPatterns = List.of();
        if (allowedMethods == null) allowedMethods = List.of("GET", "POST", "PUT",
                "PATCH", "DELETE", "OPTIONS");
        if (allowedHeaders == null) allowedHeaders = List.of("*");
        if (exposedHeaders == null) exposedHeaders = List.of(
                "Authorization", WebPlusConstants.TRACE_ID_HEADER, "Content-Disposition");
        if (allowCredentials == null) allowCredentials = false;
        if (maxAge == null) maxAge = 1800L;
        if (allowCredentials
                && (containsWildcard(allowedOrigins) || containsWildcard(allowedOriginPatterns))) {
            throw new IllegalStateException(
                    "[web-plus] web-plus.cors.allow-credentials=true 时，"
                            + "allowed-origins / allowed-origin-patterns 不能包含通配符 *，"
                            + "请改为显式来源列表。");
        }
    }

    private static boolean containsWildcard(List<String> values) {
        return values != null && values.stream().anyMatch("*"::equals);
    }
}
