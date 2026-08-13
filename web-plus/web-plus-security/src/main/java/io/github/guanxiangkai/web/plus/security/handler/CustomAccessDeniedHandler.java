package io.github.guanxiangkai.web.plus.security.handler;

import cn.hutool.json.JSONUtil;
import io.github.guanxiangkai.web.plus.core.model.ApiResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.server.authorization.ServerAccessDeniedHandler;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;

/**
 * 自定义访问拒绝处理器（WebFlux 响应式版本）
 * <p>
 * 实现 {@link ServerAccessDeniedHandler}，处理权限不足（403）的请求，
 * 统一返回 {@link ApiResponse} JSON 格式，序列化复用 Hutool {@link JSONUtil}。
 * </p>
 *
 * @author guanxiangkai
 * @since 1.0.0
 */
@Slf4j
public class CustomAccessDeniedHandler implements ServerAccessDeniedHandler {

    @Override
    public Mono<Void> handle(ServerWebExchange exchange, AccessDeniedException denied) {
        log.warn("权限不足的请求: {} - {}", exchange.getRequest().getURI().getPath(), denied.getMessage());

        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(HttpStatus.FORBIDDEN);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);

        ApiResponse<?> apiResponse = ApiResponse.fail(ApiResponse.FORBIDDEN_CODE, "权限不足，拒绝访问");
        byte[] bytes = JSONUtil.toJsonStr(apiResponse).getBytes(StandardCharsets.UTF_8);

        DataBuffer buffer = response.bufferFactory().wrap(bytes);
        return response.writeWith(Mono.just(buffer));
    }
}
