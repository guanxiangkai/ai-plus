package io.github.guanxiangkai.web.plus.error.exception;

import io.github.guanxiangkai.web.plus.error.enums.WebErrorCode;

/**
 * 服务熔断异常（HTTP 503）
 * <p>
 * 当目标服务触发熔断器保护时抛出此异常，消费方可据此返回降级响应。
 * </p>
 *
 * @author guanxiangkai
 * @since 1.0.0
 */
public class CircuitBreakerOpenException extends RemoteCallException {

    public CircuitBreakerOpenException(String serviceName) {
        super(WebErrorCode.CIRCUIT_BREAKER_OPEN.getCode(),
                "服务 [" + serviceName + "] 熔断中，请稍后重试",
                WebErrorCode.CIRCUIT_BREAKER_OPEN.getHttpStatus());
    }
}
