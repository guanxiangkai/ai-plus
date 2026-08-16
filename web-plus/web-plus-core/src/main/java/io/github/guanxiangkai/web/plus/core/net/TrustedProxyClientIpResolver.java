package io.github.guanxiangkai.web.plus.core.net;

import io.github.guanxiangkai.web.plus.core.constants.AuthConstants;
import io.github.guanxiangkai.web.plus.core.properties.ClientIpProperties;
import io.github.guanxiangkai.web.plus.core.properties.TrustedForwardProperties;
import io.github.guanxiangkai.web.plus.core.util.IpUtils;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.util.StringUtils;

import java.util.Objects;

/**
 * 仅从显式可信代理接收转发链的客户端 IP 解析器。
 */
public final class TrustedProxyClientIpResolver implements ClientIpResolver {

    private final ClientIpProperties properties;
    private final TrustedForwardProperties trustedForwardProperties;

    /**
     * 创建解析器。
     *
     * @param properties 客户端 IP 信任配置
     * @param trustedForwardProperties 网关可信转发令牌配置
     */
    public TrustedProxyClientIpResolver(ClientIpProperties properties,
                                        TrustedForwardProperties trustedForwardProperties) {
        this.properties = Objects.requireNonNull(properties, "客户端 IP 配置不能为空");
        this.trustedForwardProperties = Objects.requireNonNull(
                trustedForwardProperties, "可信转发配置不能为空");
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String resolve(ServerHttpRequest request) {
        String forwardedToken = request.getHeaders().getFirst(trustedForwardProperties.getHeaderName());
        String verifiedIp = IpUtils.normalizeIpLiteral(request.getHeaders().getFirst(
                AuthConstants.HeaderConstants.VERIFIED_CLIENT_IP));
        if (trustedForwardProperties.matches(forwardedToken) && StringUtils.hasText(verifiedIp)) {
            return verifiedIp;
        }
        return IpUtils.getClientIp(request, properties.trustedProxyIps());
    }
}
