package io.github.guanxiangkai.web.plus.core.net;

import io.github.guanxiangkai.web.plus.core.util.IpUtils;
import org.springframework.http.HttpHeaders;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.util.StringUtils;

import java.net.InetAddress;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

/**
 * 仅通过显式可信代理恢复客户端 IP 的解析器。
 *
 * <p>解析器只接受 {@code X-Forwarded-For}。当 TCP 连接对端不在可信代理名单时，所有转发头
 * 都会被忽略；当链条含有空值、{@code unknown}、主机名或非法字面量时，也会回退至 TCP 对端。
 * 对有效链条从右至左剥离可信代理，首个非可信地址即为客户端地址。</p>
 *
 * @author guanxiangkai
 */
public final class TrustedProxyClientIpResolver implements ClientIpResolver {

    private static final String FORWARDED_FOR = "X-Forwarded-For";
    private static final String UNKNOWN = "unknown";
    private final List<TrustedProxy> trustedProxies;

    /**
     * 创建解析器。
     *
     * @param trustedProxies 显式可信代理的 IP 或 CIDR；空集合表示永不信任转发头
     * @throws IllegalArgumentException 配置包含非 IP 字面量或非法 CIDR 时抛出
     */
    public TrustedProxyClientIpResolver(Collection<String> trustedProxies) {
        Objects.requireNonNull(trustedProxies, "trustedProxies");
        this.trustedProxies = trustedProxies.stream().map(TrustedProxy::parse).toList();
    }

    @Override
    public String resolve(ServerHttpRequest request) {
        String peerIp = IpUtils.getClientIp(request);
        if (!isTrusted(peerIp)) {
            return peerIp;
        }

        List<String> forwardedChain = parseForwardedChain(request.getHeaders());
        if (forwardedChain.isEmpty()) {
            return peerIp;
        }
        for (int index = forwardedChain.size() - 1; index >= 0; index--) {
            if (!isTrusted(forwardedChain.get(index))) {
                return forwardedChain.get(index);
            }
        }
        return peerIp;
    }

    private List<String> parseForwardedChain(HttpHeaders headers) {
        List<String> headerLines = headers.get(FORWARDED_FOR);
        if (headerLines == null || headerLines.isEmpty()) {
            return List.of();
        }
        List<String> chain = new ArrayList<>();
        for (String headerLine : headerLines) {
            if (!StringUtils.hasText(headerLine)) {
                return List.of();
            }
            String[] values = headerLine.split(",", -1);
            for (String value : values) {
                String normalized = IpUtils.normalizeIpLiteral(value);
                if (normalized == null) {
                    return List.of();
                }
                chain.add(normalized);
            }
        }
        return chain;
    }

    private boolean isTrusted(String address) {
        if (!StringUtils.hasText(address) || UNKNOWN.equals(address)) {
            return false;
        }
        return trustedProxies.stream().anyMatch(proxy -> proxy.matches(address));
    }

    private record TrustedProxy(byte[] network, int prefixLength) {

        static TrustedProxy parse(String configuredValue) {
            if (!StringUtils.hasText(configuredValue)) {
                throw new IllegalArgumentException("可信代理必须是 IP 字面量或 CIDR");
            }
            String value = configuredValue.strip();
            int slash = value.indexOf('/');
            if (slash != value.lastIndexOf('/')) {
                throw new IllegalArgumentException("可信代理 CIDR 格式非法: " + configuredValue);
            }
            String addressText = slash >= 0 ? value.substring(0, slash) : value;
            String normalizedAddress = IpUtils.normalizeIpLiteral(addressText);
            if (normalizedAddress == null) {
                throw new IllegalArgumentException("可信代理必须使用 IP 字面量: " + configuredValue);
            }
            InetAddress address;
            try {
                address = InetAddress.ofLiteral(normalizedAddress);
            } catch (IllegalArgumentException exception) {
                throw new IllegalArgumentException("可信代理必须使用 IP 字面量: " + configuredValue, exception);
            }
            int addressBits = address.getAddress().length * Byte.SIZE;
            int prefixLength = slash < 0 ? addressBits : parsePrefix(value.substring(slash + 1), addressBits, configuredValue);
            return new TrustedProxy(address.getAddress(), prefixLength);
        }

        private static int parsePrefix(String prefixText, int addressBits, String configuredValue) {
            try {
                int prefixLength = Integer.parseInt(prefixText);
                if (prefixLength < 0 || prefixLength > addressBits) {
                    throw new IllegalArgumentException("可信代理 CIDR 前缀超出地址范围: " + configuredValue);
                }
                return prefixLength;
            } catch (NumberFormatException exception) {
                throw new IllegalArgumentException("可信代理 CIDR 前缀非法: " + configuredValue, exception);
            }
        }

        boolean matches(String candidate) {
            InetAddress address;
            try {
                address = InetAddress.ofLiteral(candidate);
            } catch (IllegalArgumentException ignored) {
                return false;
            }
            byte[] candidateBytes = address.getAddress();
            if (candidateBytes.length != network.length) {
                return false;
            }
            int wholeBytes = prefixLength / Byte.SIZE;
            for (int index = 0; index < wholeBytes; index++) {
                if (network[index] != candidateBytes[index]) {
                    return false;
                }
            }
            int remainingBits = prefixLength % Byte.SIZE;
            if (remainingBits == 0) {
                return true;
            }
            int mask = 0xFF << (Byte.SIZE - remainingBits);
            return (network[wholeBytes] & mask) == (candidateBytes[wholeBytes] & mask);
        }
    }
}
