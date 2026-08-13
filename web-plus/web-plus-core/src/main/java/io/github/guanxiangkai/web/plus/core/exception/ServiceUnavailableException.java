package io.github.guanxiangkai.web.plus.core.exception;

import java.io.Serial;

/**
 * 服务不可用异常
 * <p>
 * 当下游微服务调用失败（降级、超时、熔断）时抛出此异常，
 * 明确区分"业务错误"（{@link BusinessException}）与"基础设施故障"。
 * HTTP 状态码映射：503 Service Unavailable。
 * </p>
 *
 * @author guanxiangkai
 * @since 1.0.0
 */
public class ServiceUnavailableException extends BaseException {

    @Serial
    private static final long serialVersionUID = 1L;

    public ServiceUnavailableException(String serviceName) {
        super(503, serviceName + " 服务暂时不可用，请稍后重试");
    }

    public ServiceUnavailableException(String serviceName, Throwable cause) {
        super(503, serviceName + " 服务暂时不可用，请稍后重试", cause);
    }

    public ServiceUnavailableException(String serviceName, String operation) {
        super(503, serviceName + " 服务暂时不可用，操作 [" + operation + "] 无法完成，请稍后重试");
    }
}
