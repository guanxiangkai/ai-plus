package io.github.guanxiangkai.web.plus.error.exception;

import io.github.guanxiangkai.web.plus.error.enums.WebErrorCode;

/**
 * 权限不足异常
 */
public class PermissionDeniedException extends WebPlusException {
    public PermissionDeniedException() {
        super(WebErrorCode.ACCESS_DENIED);
    }

    public PermissionDeniedException(String message) {
        super(WebErrorCode.ACCESS_DENIED.getCode(), message, 403);
    }
}

