package io.github.guanxiangkai.web.plus.core.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * 客户端 IP 恢复的可信代理配置。
 *
 * @param trustedProxies 可读取转发头的代理 IP 或 CIDR；默认空列表
 * @author guanxiangkai
 */
@ConfigurationProperties(prefix = "web-plus.client-ip")
public record ClientIpProperties(List<String> trustedProxies) {

    public ClientIpProperties {
        trustedProxies = trustedProxies == null ? List.of() : List.copyOf(trustedProxies);
    }
}
