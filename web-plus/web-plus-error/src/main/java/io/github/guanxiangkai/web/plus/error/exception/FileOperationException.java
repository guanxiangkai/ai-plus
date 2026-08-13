package io.github.guanxiangkai.web.plus.error.exception;

import io.github.guanxiangkai.web.plus.error.enums.WebErrorCode;

/**
 * 文件处理异常
 */
public class FileOperationException extends WebPlusException {
    public FileOperationException(WebErrorCode errorCode) {
        super(errorCode);
    }

    public FileOperationException(String message) {
        super(WebErrorCode.FILE_UPLOAD_FAILED.getCode(), message, 500);
    }

    public FileOperationException(WebErrorCode errorCode, String message) {
        super(errorCode.getCode(), message, errorCode.getHttpStatus());
    }

    public FileOperationException(WebErrorCode errorCode, Throwable cause) {
        super(errorCode, cause);
    }
}



