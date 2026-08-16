package io.github.guanxiangkai.web.plus.core.properties;

import io.github.guanxiangkai.web.plus.core.util.IpUtils;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;
import java.util.Objects;

/**
 * 客户端 IP 信任配置。
 *
 * @param trustedProxyIps 可以提供可信转发头的精确代理 IP 列表；默认不信任任何代理
 */
@ConfigurationProperties(prefix = "web-plus.client-ip")
public record ClientIpProperties(List<String> trustedProxyIps) {

    /**
     * 规范化并校验可信代理列表。
     *
     * @param trustedProxyIps 精确代理 IP 列表
     */
    public ClientIpProperties {
        if (trustedProxyIps == null) {
            trustedProxyIps = List.of();
        } else {
            trustedProxyIps = trustedProxyIps.stream()
                    .filter(Objects::nonNull)
                    .map(String::strip)
                    .filter(value -> !value.isEmpty())
                    .map(value -> {
                        String normalized = IpUtils.normalizeIpLiteral(value);
                        if (normalized == null) {
                            throw new IllegalArgumentException("可信代理仅支持合法 IP 字面量: " + value);
                        }
                        return normalized;
                    })
                    .distinct()
                    .toList();
        }
    }
}
