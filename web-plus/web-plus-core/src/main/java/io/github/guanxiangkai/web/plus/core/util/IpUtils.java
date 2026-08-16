package io.github.guanxiangkai.web.plus.core.util;

import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.util.StringUtils;

import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.regex.Pattern;

/**
 * IP 工具类（WebFlux 版）
 *
 * @author guanxiangkai
 * @since 1.0.0
 */
public final class IpUtils {

    private static final String[] IP_HEADERS = {
            "X-Forwarded-For", "X-Real-IP", "Proxy-Client-IP",
            "WL-Proxy-Client-IP", "HTTP_X_FORWARDED_FOR", "HTTP_CLIENT_IP"
    };

    private static final String UNKNOWN = "unknown";
    private static final Pattern IPV4_LITERAL = Pattern.compile(
            "^(25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)(\\.(25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)){3}$"
    );
    private static final Pattern IPV6_LITERAL = Pattern.compile("^[0-9A-Fa-f:.]+(?:%[-0-9A-Za-z._]+)?$");

    private IpUtils() {
    }

    /**
     * 从 WebFlux 请求中解析客户端 IP。
     *
     * <p>该兼容入口仅在连接对端属于内网或本机时读取转发头；安全边界代码应改用
     * {@link #getClientIp(ServerHttpRequest, Collection)} 并显式列出可信代理。</p>
     *
     * @param request 当前请求
     * @return 已规范化的客户端 IP；无法解析时返回 {@code unknown}
     */
    public static String getClientIp(ServerHttpRequest request) {
        return getClientIp(request.getHeaders(), request.getRemoteAddress());
    }

    /**
     * 从 WebFlux 请求中按显式可信代理列表解析客户端 IP。
     *
     * <p>转发链按从右到左的顺序剥离可信代理，返回最接近代理边界的非可信地址，
     * 避免攻击者在链首插入伪造地址。直连对端不在可信列表时完全忽略转发头。</p>
     *
     * @param request         当前请求
     * @param trustedProxyIps 可以提供转发头的精确代理 IP 列表
     * @return 已规范化的客户端 IP；无法解析时返回 {@code unknown}
     */
    public static String getClientIp(ServerHttpRequest request, Collection<String> trustedProxyIps) {
        return getClientIp(request.getHeaders(), request.getRemoteAddress(), trustedProxyIps);
    }

    /**
     * 兼容手动传参的客户端 IP 解析方法。
     *
     * <p>该重载只在连接对端属于内网或本机时沿用转发头，供已有调用方迁移使用；
     * 新代码必须使用显式可信代理列表重载。</p>
     *
     * @param headers       请求头
     * @param remoteAddress 原始远端地址，可以为空；仅当连接对端位于内网或本机时才信任转发头
     * @return 已规范化的客户端 IP；无法解析时返回 {@code unknown}
     */
    public static String getClientIp(HttpHeaders headers, @Nullable InetSocketAddress remoteAddress) {
        String remoteIp = Optional.ofNullable(remoteAddress)
                .map(addr -> addr.getAddress() != null ? addr.getAddress().getHostAddress() : null)
                .map(IpUtils::normalizeIpLiteral)
                .orElse(null);
        if (shouldTrustForwardedHeaders(remoteIp)) {
            return getForwardedIps(headers).stream()
                    .map(IpUtils::normalizeIpLiteral)
                    .filter(StringUtils::hasText)
                    .findFirst()
                    .orElse(remoteIp);
        }
        return StringUtils.hasText(remoteIp) ? remoteIp : UNKNOWN;
    }

    /**
     * 按显式可信代理列表解析客户端 IP。
     *
     * @param headers         请求头
     * @param remoteAddress   TCP 连接对端，可以为空
     * @param trustedProxyIps 可以提供转发头的精确代理 IP 列表
     * @return 已规范化的客户端 IP；无法解析时返回 {@code unknown}
     */
    public static String getClientIp(HttpHeaders headers,
                                     @Nullable InetSocketAddress remoteAddress,
                                     Collection<String> trustedProxyIps) {
        String remoteIp = Optional.ofNullable(remoteAddress)
                .map(address -> address.getAddress() != null ? address.getAddress().getHostAddress() : null)
                .map(IpUtils::normalizeIpLiteral)
                .orElse(null);
        if (!StringUtils.hasText(remoteIp)) {
            return UNKNOWN;
        }

        Set<String> trusted = normalizeTrustedProxies(trustedProxyIps);
        if (!trusted.contains(remoteIp)) {
            return remoteIp;
        }

        List<String> forwardedIps = getForwardedIps(headers);
        for (int index = forwardedIps.size() - 1; index >= 0; index--) {
            String forwardedIp = normalizeIpLiteral(forwardedIps.get(index));
            if (!StringUtils.hasText(forwardedIp)) {
                return remoteIp;
            }
            if (!trusted.contains(forwardedIp)) {
                return forwardedIp;
            }
        }
        return remoteIp;
    }

    /**
     * 提取转发链中的 IP 列表。
     *
     * @param headers 请求头
     * @return 按客户端到代理顺序排列的非空 IP 文本
     */
    public static List<String> getForwardedIps(HttpHeaders headers) {
        for (String header : IP_HEADERS) {
            List<String> values = headers.get(header);
            if (values != null && !values.isEmpty()) {
                List<String> forwardedIps = values.stream()
                        .filter(StringUtils::hasText)
                        .flatMap(value -> Arrays.stream(value.split(",")))
                        .map(String::trim)
                        .filter(StringUtils::hasText)
                        .filter(value -> !UNKNOWN.equalsIgnoreCase(value))
                        .toList();
                if (!forwardedIps.isEmpty()) {
                    return forwardedIps;
                }
            }
        }
        return List.of();
    }

    /**
     * 判断 IP 文本是否表示内网、本机或链路本地地址。
     *
     * @param ip 待判断的 IP 文本，可以为空
     * @return 是内网地址时返回 {@code true}
     */
    public static boolean isIntranet(@Nullable String ip) {
        String normalizedIp = normalizeIpLiteral(ip);
        if (!StringUtils.hasText(normalizedIp)) return false;
        try {
            InetAddress address = InetAddress.getByName(normalizedIp);
            if (address.isAnyLocalAddress() || address.isLoopbackAddress() || address.isSiteLocalAddress()) {
                return true;
            }
            if (address instanceof Inet6Address inet6Address) {
                byte[] bytes = inet6Address.getAddress();
                int first = bytes[0] & 0xFF;
                int second = bytes[1] & 0xFF;
                if ((first & 0xFE) == 0xFC) {
                    return true;
                }
                return first == 0xFE && (second & 0xC0) == 0x80;
            }
        } catch (UnknownHostException ignored) {
            return false;
        }
        if (normalizedIp.startsWith("10.") || normalizedIp.startsWith("192.168.")) {
            return true;
        }
        if (normalizedIp.startsWith("172.")) {
            String[] parts = normalizedIp.split("\\.");
            if (parts.length >= 2) {
                try {
                    int second = Integer.parseInt(parts[1]);
                    return second >= 16 && second <= 31;
                } catch (NumberFormatException ignored) {
                    return false;
                }
            }
            return false;
        }
        return false;
    }

    private static boolean shouldTrustForwardedHeaders(@Nullable String remoteIp) {
        return StringUtils.hasText(remoteIp) && isIntranet(remoteIp);
    }

    private static Set<String> normalizeTrustedProxies(Collection<String> trustedProxyIps) {
        if (trustedProxyIps == null || trustedProxyIps.isEmpty()) {
            return Set.of();
        }
        return trustedProxyIps.stream()
                .map(IpUtils::normalizeIpLiteral)
                .filter(StringUtils::hasText)
                .collect(Collectors.toUnmodifiableSet());
    }

    /**
     * 规范化 IPv4 或 IPv6 字面量，且不会对主机名执行 DNS 解析。
     *
     * @param ip 待规范化的 IP 文本，可以为空
     * @return 规范化地址；输入不是 IP 字面量时返回 {@code null}
     */
    public static @Nullable String normalizeIpLiteral(@Nullable String ip) {
        if (!StringUtils.hasText(ip)) {
            return null;
        }
        String candidate = ip.strip();
        if (candidate.startsWith("[") && candidate.endsWith("]") && candidate.length() > 2) {
            candidate = candidate.substring(1, candidate.length() - 1);
        }
        if (!looksLikeIpLiteral(candidate)) {
            return null;
        }
        try {
            InetAddress address = InetAddress.getByName(candidate);
            return address.getHostAddress();
        } catch (UnknownHostException ignored) {
            return null;
        }
    }

    private static boolean looksLikeIpLiteral(String value) {
        return IPV4_LITERAL.matcher(value).matches()
                || (value.contains(":") && IPV6_LITERAL.matcher(value).matches());
    }
}
