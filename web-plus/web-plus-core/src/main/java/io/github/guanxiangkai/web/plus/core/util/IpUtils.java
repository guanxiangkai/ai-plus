package io.github.guanxiangkai.web.plus.core.util;

import org.springframework.http.HttpHeaders;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.jspecify.annotations.Nullable;
import org.springframework.util.StringUtils;

import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
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
     * 从 WebFlux {@link ServerHttpRequest} 中获取客户端真实 IP
     */
    public static String getClientIp(ServerHttpRequest request) {
        return getClientIp(request.getHeaders(), request.getRemoteAddress());
    }

    /**
     * 通用方法（兼容 WebFlux / 手动传参）
     *
     * @param headers       请求头
     * @param remoteAddress 原始远端地址
     *                      仅当连接对端位于内网/本机时，才会信任转发头中的客户端 IP。
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
     * 提取转发链中的 IP 列表，按客户端 -> 代理的顺序返回。
     */
    public static List<String> getForwardedIps(HttpHeaders headers) {
        for (String header : IP_HEADERS) {
            String ip = headers.getFirst(header);
            if (StringUtils.hasText(ip) && !UNKNOWN.equalsIgnoreCase(ip)) {
                return Arrays.stream(ip.split(","))
                        .map(String::trim)
                        .filter(StringUtils::hasText)
                        .filter(value -> !UNKNOWN.equalsIgnoreCase(value))
                        .toList();
            }
        }
        return List.of();
    }

    /**
     * 是否内网 IP
     */
    public static boolean isIntranet(String ip) {
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

    @Nullable
    public static String normalizeIpLiteral(@Nullable String ip) {
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
