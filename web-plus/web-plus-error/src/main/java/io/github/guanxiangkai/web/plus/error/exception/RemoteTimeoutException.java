package io.github.guanxiangkai.web.plus.error.exception;

import io.github.guanxiangkai.web.plus.error.enums.WebErrorCode;

/**
 * 远程调用超时异常（HTTP 504）
 *
 * @author guanxiangkai
 * @since 1.0.0
 */
public class RemoteTimeoutException extends RemoteCallException {

    public RemoteTimeoutException(String serviceName) {
        super(WebErrorCode.REMOTE_TIMEOUT.getCode(),
                "调用服务 [" + serviceName + "] 超时",
                WebErrorCode.REMOTE_TIMEOUT.getHttpStatus());
    }

    public RemoteTimeoutException(String serviceName, long timeoutMs) {
        super(WebErrorCode.REMOTE_TIMEOUT.getCode(),
                "调用服务 [" + serviceName + "] 超时（" + timeoutMs + "ms）",
                WebErrorCode.REMOTE_TIMEOUT.getHttpStatus());
    }
}
