package io.github.guanxiangkai.web.plus.error.exception;

import io.github.guanxiangkai.web.plus.error.enums.WebErrorCode;

/**
 * 远程服务内部错误异常（HTTP 502）
 *
 * @author guanxiangkai
 * @since 1.0.0
 */
public class RemoteServiceException extends RemoteCallException {

    public RemoteServiceException(String serviceName, String message) {
        super(WebErrorCode.REMOTE_SERVICE_ERROR.getCode(),
                "服务 [" + serviceName + "] 内部错误: " + message,
                WebErrorCode.REMOTE_SERVICE_ERROR.getHttpStatus());
    }

    public RemoteServiceException(String serviceName, String message, Throwable cause) {
        super(WebErrorCode.REMOTE_SERVICE_ERROR.getCode(),
                "服务 [" + serviceName + "] 内部错误: " + message,
                WebErrorCode.REMOTE_SERVICE_ERROR.getHttpStatus());
        initCause(cause);
    }
}
