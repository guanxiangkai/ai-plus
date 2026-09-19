package io.github.guanxiangkai.web.plus.license.web;

import io.github.guanxiangkai.web.plus.license.LicenseException;
import io.github.guanxiangkai.web.plus.license.runtime.LicenseGuard;
import java.nio.charset.StandardCharsets;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

/** 只读取内存授权状态；请求线程不读密钥、不调用授权服务。 */
public final class LicenseWebFilter implements WebFilter, Ordered {
    private static final byte[] REJECTED = "{\"code\":\"LICENSE_REQUIRED\"}".getBytes(StandardCharsets.UTF_8);
    private final LicenseGuard guard;

    /** 使用已完成启动校验的统一关口。 */
    public LicenseWebFilter(LicenseGuard guard) { this.guard = guard; }

    @Override public int getOrder() { return Ordered.HIGHEST_PRECEDENCE + 20; }

    @Override public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        return Mono.defer(() -> {
            try { guard.current(); }
            catch (LicenseException denied) {
                exchange.getResponse().setStatusCode(HttpStatus.FORBIDDEN);
                exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
                exchange.getResponse().getHeaders().setCacheControl("no-store");
                return exchange.getResponse().writeWith(Mono.just(exchange.getResponse().bufferFactory().wrap(REJECTED)));
            }
            return chain.filter(exchange);
        });
    }
}
