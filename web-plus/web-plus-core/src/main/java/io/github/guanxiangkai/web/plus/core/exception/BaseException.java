package io.github.guanxiangkai.web.plus.core.exception;

import java.io.Serial;

/**
 * 业务异常基类
 * <p>
 * GraalVM JDK 25 特性：
 * - @Serial 注解（JDK 14+）
 * - 构造函数链式调用
 * - 支持 Native Image 序列化
 * </p>
 *
 * @author guanxiangkai
 * @since 1.0.0
 */
public class BaseException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 错误码
     */
    private final int code;

    /**
     * 错误数据
     */
    private final Object data;

    public BaseException(String message) {
        this(400, message);
    }

    public BaseException(int code, String message) {
        this(code, message, null);
    }

    public BaseException(int code, String message, Object data) {
        super(message);
        this.code = code;
        this.data = data;
    }

    public BaseException(int code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
        this.data = null;
    }

    public int getCode() {
        return code;
    }

    public Object getData() {
        return data;
    }

    /**
     * 转换为 API 响应
     */
    public <T> io.github.guanxiangkai.web.plus.core.model.ApiResponse<T> toApiResponse() {
        return io.github.guanxiangkai.web.plus.core.model.ApiResponse.fail(code, getMessage(), (T) data);
    }

    // ==================== 常用异常内部类 ====================

    /**
     * 业务异常
     */
    public static class BusinessException extends BaseException {
        @Serial
        private static final long serialVersionUID = 1L;

        public BusinessException(String message) {
            super(400, message);
        }

        public BusinessException(int code, String message) {
            super(code, message);
        }
    }

    /**
     * 未授权异常
     */
    public static class UnauthorizedException extends BaseException {
        @Serial
        private static final long serialVersionUID = 1L;

        public UnauthorizedException(String message) {
            super(401, message);
        }
    }

    /**
     * 禁止访问异常
     */
    public static class ForbiddenException extends BaseException {
        @Serial
        private static final long serialVersionUID = 1L;

        public ForbiddenException(String message) {
            super(403, message);
        }
    }

    /**
     * 资源不存在异常
     */
    public static class NotFoundException extends BaseException {
        @Serial
        private static final long serialVersionUID = 1L;

        public NotFoundException(String message) {
            super(404, message);
        }
    }
}

