package io.github.guanxiangkai.web.plus.security.token;

import org.springframework.web.server.ServerWebExchange;

import java.util.Optional;

/**
 * 请求参数令牌解析结果。
 *
 * @param token 解析到的令牌；未提供令牌时为空
 * @param source 令牌来源
 * @param exchange 可继续传递给下游处理链的请求交换对象
 */
public record RequestParameterTokenResolution(
        Optional<String> token,
        RequestParameterTokenSource source,
        ServerWebExchange exchange) {

    /**
     * 创建未提供令牌的解析结果。
     *
     * @param exchange 可继续传递给下游处理链的请求交换对象
     * @return 未提供令牌的解析结果
     */
    public static RequestParameterTokenResolution absent(ServerWebExchange exchange) {
        return new RequestParameterTokenResolution(Optional.empty(), RequestParameterTokenSource.NONE, exchange);
    }
}
