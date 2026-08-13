package io.github.guanxiangkai.web.plus.protection.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.List;

/**
 * 服务侧 API 限流配置。
 *
 * @author guanxiangkai
 * @since 1.0.0
 */
@ConfigurationProperties(prefix = "web-plus.security.api-rate-limit")
public record ApiRateLimitProperties(
        boolean enabled,
        int limit,
        int burst,
        Duration window,
        List<String> includePaths,
        List<String> excludePaths
) {

    public ApiRateLimitProperties {
        if (limit <= 0) {
            limit = 120;
        }
        if (burst < 0) {
            burst = 0;
        }
        if (window == null || window.isNegative() || window.isZero()) {
            window = Duration.ofMinutes(1);
        }
        if (includePaths == null || includePaths.isEmpty()) {
            includePaths = List.of("/**");
        }
        if (excludePaths == null) {
            excludePaths = List.of(
                    "/auth/login",
                    "/auth/refresh",
                    "/internal/**",
                    "/actuator/health",
                    "/actuator/info",
                    "/sse/connect",
                    "/sse/ticket"
            );
        }
    }

    public int effectiveLimit() {
        return limit + burst;
    }
}
