package io.github.guanxiangkai.web.plus.core.model;

import org.springframework.aot.hint.annotation.RegisterReflectionForBinding;

import java.io.Serializable;

/**
 * 统一 API 响应对象
 * <p>
 * GraalVM JDK 25 + Spring Boot 4 特性：
 * 1. Java Record（JDK 14+）- 不可变、简洁
 * 2. @RegisterReflectionForBinding - Spring AOT 支持
 * 3. 紧凑型构造器 - 自动参数验证
 * 4. 静态工厂方法 - Builder 模式替代
 * </p>
 *
 * @param <T> 响应数据类型
 * @author guanxiangkai
 * @since 1.0.0
 */
@RegisterReflectionForBinding
public record ApiResponse<T>(
        int code,
        String message,
        T data,
        long timestamp
) implements Serializable {

    // ==================== 响应码常量 ====================

    /**
     * 成功响应码
     */
    public static final int SUCCESS_CODE = 200;

    /**
     * 业务错误响应码
     */
    public static final int BUSINESS_ERROR_CODE = 400;

    /**
     * 未授权响应码
     */
    public static final int UNAUTHORIZED_CODE = 401;

    /**
     * 禁止访问响应码
     */
    public static final int FORBIDDEN_CODE = 403;

    /**
     * 系统错误响应码
     */
    public static final int SYSTEM_ERROR_CODE = 500;

    // ==================== 紧凑型构造器 ====================

    /**
     * 紧凑型构造器 - 自动设置时间戳和参数验证
     */
    public ApiResponse {
        if (timestamp == 0L) {
            timestamp = System.currentTimeMillis();
        }
        if (message == null) {
            message = "操作成功";
        }
    }

    // ==================== 静态工厂方法 - 成功响应 ====================

    /**
     * 成功响应（无数据）
     */
    public static <T> ApiResponse<T> ok() {
        return new ApiResponse<>(SUCCESS_CODE, "操作成功", null, 0L);
    }

    /**
     * 成功响应（携带数据）
     */
    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(SUCCESS_CODE, "操作成功", data, 0L);
    }

    /**
     * 成功响应（自定义消息）
     */
    public static <T> ApiResponse<T> ok(String message, T data) {
        return new ApiResponse<>(SUCCESS_CODE, message, data, 0L);
    }

    // ==================== 静态工厂方法 - 失败响应 ====================

    /**
     * 失败响应
     */
    public static <T> ApiResponse<T> fail() {
        return new ApiResponse<>(BUSINESS_ERROR_CODE, "操作失败", null, 0L);
    }

    /**
     * 失败响应（自定义消息）
     */
    public static <T> ApiResponse<T> fail(String message) {
        return new ApiResponse<>(BUSINESS_ERROR_CODE, message, null, 0L);
    }

    /**
     * 失败响应（自定义错误码和消息）
     */
    public static <T> ApiResponse<T> fail(int code, String message) {
        return new ApiResponse<>(code, message, null, 0L);
    }

    /**
     * 失败响应（携带数据）
     */
    public static <T> ApiResponse<T> fail(int code, String message, T data) {
        return new ApiResponse<>(code, message, data, 0L);
    }

    // ==================== 静态工厂方法 - 特殊响应 ====================

    /**
     * 未授权响应
     */
    public static <T> ApiResponse<T> unauthorized() {
        return new ApiResponse<>(UNAUTHORIZED_CODE, "未授权", null, 0L);
    }

    /**
     * 未授权响应（自定义消息）
     */
    public static <T> ApiResponse<T> unauthorized(String message) {
        return new ApiResponse<>(UNAUTHORIZED_CODE, message, null, 0L);
    }

    /**
     * 禁止访问响应
     */
    public static <T> ApiResponse<T> forbidden() {
        return new ApiResponse<>(FORBIDDEN_CODE, "禁止访问", null, 0L);
    }

    /**
     * 禁止访问响应（自定义消息）
     */
    public static <T> ApiResponse<T> forbidden(String message) {
        return new ApiResponse<>(FORBIDDEN_CODE, message, null, 0L);
    }

    /**
     * 系统错误响应
     */
    public static <T> ApiResponse<T> error() {
        return new ApiResponse<>(SYSTEM_ERROR_CODE, "系统错误", null, 0L);
    }

    /**
     * 系统错误响应（自定义消息）
     */
    public static <T> ApiResponse<T> error(String message) {
        return new ApiResponse<>(SYSTEM_ERROR_CODE, message, null, 0L);
    }

    // ==================== 便捷方法 ====================

    /**
     * 判断是否成功
     */
    public boolean isSuccess() {
        return code == SUCCESS_CODE;
    }

    /**
     * 判断是否失败
     */
    public boolean isFail() {
        return !isSuccess();
    }

    /**
     * 获取数据或默认值
     */
    public T getDataOrDefault(T defaultValue) {
        return data != null ? data : defaultValue;
    }

}
