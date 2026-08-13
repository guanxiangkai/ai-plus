package io.github.guanxiangkai.web.plus.core.enums;

import java.util.Map;

/**
 * HTTP 状态码枚举
 * <p>
 * 实现 {@link BaseEnum}&lt;Integer&gt;，提供 O(1) code 查找及语义化分类判断。
 * </p>
 *
 * @author guanxiangkai
 * @since 1.0.0
 */
public enum HttpStatus implements BaseEnum<Integer> {

    // ==================== 2xx Success ====================
    OK(200, "请求成功"),
    CREATED(201, "资源创建成功"),
    ACCEPTED(202, "请求已接受"),
    NO_CONTENT(204, "请求成功但无返回内容"),

    // ==================== 3xx Redirection ====================
    MOVED_PERMANENTLY(301, "资源已永久移动"),
    FOUND(302, "资源临时移动"),
    NOT_MODIFIED(304, "资源未修改"),

    // ==================== 4xx Client Error ====================
    BAD_REQUEST(400, "请求参数错误"),
    UNAUTHORIZED(401, "未授权，请先登录"),
    FORBIDDEN(403, "禁止访问，权限不足"),
    NOT_FOUND(404, "请求的资源不存在"),
    METHOD_NOT_ALLOWED(405, "请求方法不允许"),
    NOT_ACCEPTABLE(406, "不可接受的请求"),
    REQUEST_TIMEOUT(408, "请求超时"),
    CONFLICT(409, "请求冲突"),
    GONE(410, "资源已被永久删除"),
    PAYLOAD_TOO_LARGE(413, "请求体过大"),
    UNSUPPORTED_MEDIA_TYPE(415, "不支持的媒体类型"),
    UNPROCESSABLE_ENTITY(422, "请求格式正确但语义错误"),
    TOO_MANY_REQUESTS(429, "请求过于频繁，请稍后再试"),

    // ==================== 5xx Server Error ====================
    INTERNAL_SERVER_ERROR(500, "服务器内部错误"),
    NOT_IMPLEMENTED(501, "功能未实现"),
    BAD_GATEWAY(502, "网关错误"),
    SERVICE_UNAVAILABLE(503, "服务暂时不可用"),
    GATEWAY_TIMEOUT(504, "网关超时");

    private static final Map<Integer, HttpStatus> CODE_MAP =
            BaseEnum.createCodeMap(HttpStatus.class, HttpStatus::getCode);

    private final Integer code;
    private final String description;

    HttpStatus(Integer code, String description) {
        this.code = code;
        this.description = description;
    }

    /**
     * 根据状态码获取枚举（O(1)）
     */
    public static HttpStatus fromCode(Integer code) {
        return CODE_MAP.get(code);
    }

    @Override
    public Integer getCode() {
        return code;
    }

    @Override
    public String getDescription() {
        return description;
    }

    /**
     * 是否为成功状态码（2xx）
     */
    public boolean isSuccess() {
        return code >= 200 && code < 300;
    }

    /**
     * 是否为重定向状态码（3xx）
     */
    public boolean isRedirection() {
        return code >= 300 && code < 400;
    }

    /**
     * 是否为客户端错误状态码（4xx）
     */
    public boolean isClientError() {
        return code >= 400 && code < 500;
    }

    /**
     * 是否为服务器错误状态码（5xx）
     */
    public boolean isServerError() {
        return code >= 500 && code < 600;
    }

    /**
     * 是否为错误状态码
     */
    public boolean isError() {
        return isClientError() || isServerError();
    }

    /**
     * 获取状态码分类
     */
    public StatusCategory getCategory() {
        return switch (code / 100) {
            case 2 -> StatusCategory.SUCCESS;
            case 3 -> StatusCategory.REDIRECTION;
            case 4 -> StatusCategory.CLIENT_ERROR;
            case 5 -> StatusCategory.SERVER_ERROR;
            default -> StatusCategory.UNKNOWN;
        };
    }

    /**
     * 状态码分类
     */
    public enum StatusCategory {
        SUCCESS, REDIRECTION, CLIENT_ERROR, SERVER_ERROR, UNKNOWN
    }
}

