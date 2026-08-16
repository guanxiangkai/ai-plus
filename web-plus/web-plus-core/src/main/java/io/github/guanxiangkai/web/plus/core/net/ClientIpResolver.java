package io.github.guanxiangkai.web.plus.core.net;

import io.github.guanxiangkai.web.plus.core.util.IpUtils;
import org.springframework.http.server.reactive.ServerHttpRequest;

import java.util.List;

/**
 * 解析请求客户端 IP 的策略接口。
 *
 * <p>安全相关调用方应注入该接口，不应自行读取可由客户端伪造的转发请求头。</p>
 */
@FunctionalInterface
public interface ClientIpResolver {

    /**
     * 解析客户端 IP。
     *
     * @param request 当前 WebFlux 请求
     * @return 规范化 IP；无法解析时返回 {@code unknown}
     */
    String resolve(ServerHttpRequest request);

    /**
     * 创建只使用 TCP 连接对端、不信任任何转发请求头的安全默认策略。
     *
     * @return 直连对端解析策略
     */
    static ClientIpResolver directPeer() {
        return request -> IpUtils.getClientIp(request, List.of());
    }

}
