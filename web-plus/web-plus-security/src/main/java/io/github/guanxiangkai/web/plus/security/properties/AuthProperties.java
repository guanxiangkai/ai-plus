package io.github.guanxiangkai.web.plus.security.properties;

import io.github.guanxiangkai.web.plus.core.util.IpUtils;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;
import java.util.Objects;

/**
 * web-plus-security 配置属性
 *
 * @author guanxiangkai
 * @since 1.0.0
 */
@ConfigurationProperties(prefix = "web-plus.auth")
public record AuthProperties(
        Boolean enabled,
        String mode,
        String tokenHeader,
        String tokenPrefix,
        /**
         * JWT 模式下必须显式配置，且需为至少 32 字节的随机密钥。
         */
        String jwtSecret,
        Long accessTokenExpireMs,
        Boolean singleLogin,
        List<String> permitPaths,
        /**
         * 是否启用网关透传模式（允许下游服务信任网关注入的身份请求头）。
         * <p>
         * 默认 {@code false}（安全默认值）。启用前请确保配置了可信 IP 列表
         * {@code web-plus.auth.gateway-trusted-ips}，并由网关同时透传
         * {@code X-User-Id} 与 {@code X-User-Claims}。
         * </p>
         */
        Boolean gatewayPassthroughEnabled,
        /**
         * 允许透传 X-User-Id 请求头的受信 IP 列表（CIDR 不支持，仅精确匹配）。
         * <p>
         * 启用透传时必须显式配置此列表，否则透传会被拒绝，避免把未校验请求头当成已认证身份。
         * </p>
         */
        List<String> gatewayTrustedIps,
        /**
         * 允许转发网关请求的受信代理 IP 列表。
         * <p>
         * 仅当直连对端命中该列表时，才会继续检查转发链中的
         * {@code gatewayTrustedIps}，避免直接信任客户端伪造的转发头。
         * </p>
         */
        List<String> gatewayTrustedProxyIps
) {
    public AuthProperties {
        if (enabled == null) enabled = true;
        if (mode == null) mode = "JWT";
        if (tokenHeader == null) tokenHeader = "Authorization";
        if (tokenPrefix == null) tokenPrefix = "Bearer ";
        if (jwtSecret != null) jwtSecret = jwtSecret.strip();
        if (accessTokenExpireMs == null) accessTokenExpireMs = 7200_000L;   // 2h
        if (singleLogin == null) singleLogin = false;
        if (permitPaths == null) permitPaths = List.of(
                "/auth/login", "/api/auth/login",
                "/public/**", "/actuator/health/**", "/actuator/info",
                "/v3/api-docs/**", "/swagger-ui/**", "/doc.html", "/webjars/**"
        );
        // 网关透传默认关闭（安全默认值，避免 X-User-Id 被外部客户端伪造）
        if (gatewayPassthroughEnabled == null) gatewayPassthroughEnabled = false;
        if (gatewayTrustedIps == null) gatewayTrustedIps = List.of();
        if (gatewayTrustedProxyIps == null) gatewayTrustedProxyIps = List.of();
        gatewayTrustedIps = normalizeAddressList(gatewayTrustedIps);
        gatewayTrustedProxyIps = normalizeAddressList(gatewayTrustedProxyIps);
        if (gatewayPassthroughEnabled && gatewayTrustedIps.isEmpty()) {
            throw new IllegalArgumentException(
                    "[web-plus] 启用网关透传时必须至少配置一个 web-plus.auth.gateway-trusted-ips，" +
                            "避免信任未校验的 X-User-Id / X-User-Claims 请求头");
        }
    }

    private static List<String> normalizeAddressList(List<String> values) {
        return values.stream()
                .filter(Objects::nonNull)
                .map(String::strip)
                .filter(value -> !value.isEmpty())
                .map(value -> {
                    String normalizedIp = IpUtils.normalizeIpLiteral(value);
                    if (normalizedIp == null) {
                        throw new IllegalArgumentException("[web-plus] 仅支持显式配置合法 IP 字面量: " + value);
                    }
                    return normalizedIp;
                })
                .toList();
    }
}
