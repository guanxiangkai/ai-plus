package io.github.guanxiangkai.web.plus.error.exception;

import io.github.guanxiangkai.web.plus.error.enums.WebErrorCode;

/**
 * 参数校验异常
 */
public class ValidationException extends WebPlusException {
    private final Object details;

    public ValidationException(String message) {
        super(WebErrorCode.PARAM_INVALID.getCode(), message, 400);
        this.details = null;
    }

    public ValidationException(String message, Object details) {
        super(WebErrorCode.PARAM_INVALID.getCode(), message, 400);
        this.details = details;
    }

    public Object getDetails() {
        return details;
    }
}

