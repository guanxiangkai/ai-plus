package io.github.guanxiangkai.web.plus.core.net;

import org.springframework.http.server.reactive.ServerHttpRequest;

/**
 * 解析请求的客户端 IP。
 *
 * <p>实现必须将转发请求头视为不可信输入，并且只在连接对端属于显式配置的可信代理时使用它们。</p>
 *
 * @author guanxiangkai
 */
@FunctionalInterface
public interface ClientIpResolver {

    /**
     * 解析客户端 IP。
     *
     * @param request 当前 WebFlux 请求
     * @return 规范化的 IP 字面量；没有可用连接对端地址时返回 {@code unknown}
     */
    String resolve(ServerHttpRequest request);
}
