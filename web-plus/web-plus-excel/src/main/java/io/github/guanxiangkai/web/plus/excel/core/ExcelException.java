package io.github.guanxiangkai.web.plus.excel.core;

import java.io.Serial;

/**
 * Excel 操作异常
 *
 * @author guanxiangkai
 * @since 1.0.0
 */
public class ExcelException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    public ExcelException(String message) {
        super(message);
    }

    public ExcelException(String message, Throwable cause) {
        super(message, cause);
    }
}

