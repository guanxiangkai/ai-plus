package io.github.guanxiangkai.web.plus.core.exception;

/**
 * core 层业务异常
 * <p>
 * 用于 `web-plus-core` 中的基础 CRUD / 转换 / 基础设施能力抛出可预期业务错误，
 * 上层 `web-plus-error` 可统一捕获并转换为标准 API 响应。
 * </p>
 *
 * @author guanxiangkai
 * @since 1.0.0
 */
public class CoreBizException extends RuntimeException {

    private final String code;
    private final int httpStatus;

    public CoreBizException(String message) {
        this("CORE_BIZ_ERROR", message, 400);
    }

    public CoreBizException(String code, String message, int httpStatus) {
        super(message);
        this.code = code;
        this.httpStatus = httpStatus;
    }

    public static CoreBizException notFound(String entityName) {
        return new CoreBizException("CORE_DATA_NOT_FOUND", entityName + "不存在", 404);
    }

    public static CoreBizException notFound(String entityName, String id) {
        return new CoreBizException("CORE_DATA_NOT_FOUND", entityName + "不存在（id=" + id + "）", 404);
    }

    public static CoreBizException unsupported(String message) {
        return new CoreBizException("CORE_UNSUPPORTED", message, 400);
    }

    public static CoreBizException invalid(String message) {
        return new CoreBizException("CORE_PARAM_INVALID", message, 400);
    }

    public String getCode() {
        return code;
    }

    public int getHttpStatus() {
        return httpStatus;
    }
}
