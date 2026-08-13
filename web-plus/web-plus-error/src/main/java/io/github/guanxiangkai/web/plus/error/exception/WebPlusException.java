package io.github.guanxiangkai.web.plus.error.exception;

import io.github.guanxiangkai.web.plus.error.enums.WebErrorCode;

/**
 * Web Plus 框架基础异常
 *
 * @author guanxiangkai
 * @since 1.0.0
 */
public class WebPlusException extends RuntimeException {

    private final String code;
    private final int httpStatus;

    public WebPlusException(String code, String message, int httpStatus) {
        super(message);
        this.code = code;
        this.httpStatus = httpStatus;
    }

    public WebPlusException(WebErrorCode errorCode) {
        super(errorCode.getMessage());
        this.code = errorCode.getCode();
        this.httpStatus = errorCode.getHttpStatus();
    }

    public WebPlusException(WebErrorCode errorCode, String message) {
        super(message);
        this.code = errorCode.getCode();
        this.httpStatus = errorCode.getHttpStatus();
    }

    public WebPlusException(WebErrorCode errorCode, Throwable cause) {
        super(errorCode.getMessage(), cause);
        this.code = errorCode.getCode();
        this.httpStatus = errorCode.getHttpStatus();
    }

    public String getCode() {
        return code;
    }

    public int getHttpStatus() {
        return httpStatus;
    }
}

