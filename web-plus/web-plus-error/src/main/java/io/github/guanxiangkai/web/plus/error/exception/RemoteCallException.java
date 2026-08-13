package io.github.guanxiangkai.web.plus.error.exception;

import io.github.guanxiangkai.web.plus.error.enums.WebErrorCode;

/**
 * 远程调用异常 —— 用于封装 RPC / HTTP 调用第三方服务失败
 *
 * @author guanxiangkai
 * @since 1.0.0
 */
public class RemoteCallException extends WebPlusException {

    /**
     * 目标服务名
     */
    private final String serviceName;

    public RemoteCallException(String message) {
        super(WebErrorCode.REMOTE_CALL_ERROR.getCode(), message,
                WebErrorCode.REMOTE_CALL_ERROR.getHttpStatus());
        this.serviceName = null;
    }

    public RemoteCallException(String serviceName, String message) {
        super(WebErrorCode.REMOTE_CALL_ERROR.getCode(),
                "调用服务 [" + serviceName + "] 失败: " + message,
                WebErrorCode.REMOTE_CALL_ERROR.getHttpStatus());
        this.serviceName = serviceName;
    }

    public RemoteCallException(String serviceName, String message, Throwable cause) {
        super(WebErrorCode.REMOTE_CALL_ERROR.getCode(),
                "调用服务 [" + serviceName + "] 失败: " + message,
                WebErrorCode.REMOTE_CALL_ERROR.getHttpStatus());
        this.serviceName = serviceName;
        initCause(cause);
    }

    protected RemoteCallException(String code, String message, int httpStatus) {
        super(code, message, httpStatus);
        this.serviceName = null;
    }

    public String getServiceName() {
        return serviceName;
    }
}

