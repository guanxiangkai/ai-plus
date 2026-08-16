package io.github.guanxiangkai.web.plus.security.handler;

import cn.hutool.json.JSONUtil;
import io.github.guanxiangkai.web.plus.core.constants.AuthConstants;
import io.github.guanxiangkai.web.plus.core.model.ApiResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.server.ServerAuthenticationEntryPoint;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;

/**
 * 自定义认证入口点（WebFlux 响应式版本）
 * <p>
 * 实现 {@link ServerAuthenticationEntryPoint}，处理未认证（401）的请求，
 * 统一返回 {@link ApiResponse} JSON 格式，序列化复用 Hutool {@link JSONUtil}。
 * </p>
 *
 * @author guanxiangkai
 * @since 1.0.0
 */
@Slf4j
public class CustomAuthenticationEntryPoint implements ServerAuthenticationEntryPoint {

    static boolean hasAuthenticationMaterial(ServerWebExchange exchange) {
        HttpHeaders headers = exchange.getRequest().getHeaders();
        return hasText(headers, HttpHeaders.AUTHORIZATION)
                || hasText(headers, AuthConstants.HeaderConstants.USER_ID);
    }

    private static boolean hasText(HttpHeaders headers, String headerName) {
        return StringUtils.hasText(headers.getFirst(headerName));
    }

    @Override
    public Mono<Void> commence(ServerWebExchange exchange, AuthenticationException ex) {
        String path = exchange.getRequest().getURI().getPath();
        if (hasAuthenticationMaterial(exchange)) {
            log.warn("认证失败的请求: path={}, exception={}", path, ex.getClass().getSimpleName());
        } else {
            log.debug("未携带认证信息的请求: {}", path);
        }

        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(HttpStatus.UNAUTHORIZED);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);

        // 下游服务仅通过网关访问，CORS 由网关统一处理，此处不反射 Origin 头
        // 避免 reflected-origin + credentials:true 的安全漏洞（任意跨域读取响应）

        ApiResponse<?> apiResponse = ApiResponse.fail(ApiResponse.UNAUTHORIZED_CODE, "未认证，请先登录");
        byte[] bytes = JSONUtil.toJsonStr(apiResponse).getBytes(StandardCharsets.UTF_8);

        DataBuffer buffer = response.bufferFactory().wrap(bytes);
        return response.writeWith(Mono.just(buffer));
    }
}
