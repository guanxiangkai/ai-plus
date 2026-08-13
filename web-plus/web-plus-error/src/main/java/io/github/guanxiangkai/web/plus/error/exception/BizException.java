package io.github.guanxiangkai.web.plus.error.exception;

import io.github.guanxiangkai.web.plus.error.enums.WebErrorCode;

/**
 * 业务异常 —— 由业务代码主动抛出，表示业务规则不满足
 *
 * @author guanxiangkai
 * @since 1.0.0
 */
public class BizException extends WebPlusException {

    public BizException(String message) {
        super(WebErrorCode.BIZ_ERROR.getCode(), message, 400);
    }

    public BizException(String code, String message) {
        super(code, message, 400);
    }

    public BizException(WebErrorCode errorCode) {
        super(errorCode);
    }

    public BizException(WebErrorCode errorCode, String message) {
        super(errorCode, message);
    }

    /**
     * 快捷工厂：数据不存在
     */
    public static BizException notFound(String entity) {
        return new BizException(WebErrorCode.DATA_NOT_FOUND.getCode(),
                entity + "不存在");
    }

    /**
     * 快捷工厂：数据已存在
     */
    public static BizException alreadyExists(String entity) {
        return new BizException(WebErrorCode.DATA_EXISTS.getCode(),
                entity + "已存在");
    }
}

