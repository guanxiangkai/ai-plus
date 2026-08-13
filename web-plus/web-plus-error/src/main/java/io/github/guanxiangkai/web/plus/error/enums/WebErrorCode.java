package io.github.guanxiangkai.web.plus.error.enums;

/**
 * Web Plus 统一错误码枚举
 * <p>
 * 错误码分段：
 * <ul>
 *   <li>A0001-A0999 —— 认证与鉴权</li>
 *   <li>B0001-B0999 —— 通用业务错误</li>
 *   <li>F0001-F0999 —— 文件处理错误</li>
 *   <li>V0001-V0999 —— 参数校验错误</li>
 *   <li>S0001-S0999 —— 系统内部错误</li>
 * </ul>
 * </p>
 *
 * @author guanxiangkai
 * @since 1.0.0
 */
public enum WebErrorCode {

    // ── 认证与鉴权 A0xxx ───────────────────────────────────────
    UNAUTHORIZED("A0001", "未登录或登录已过期", 401),
    TOKEN_INVALID("A0002", "Token 无效", 401),
    TOKEN_EXPIRED("A0003", "Token 已过期", 401),
    ACCESS_DENIED("A0004", "无访问权限", 403),
    ACCOUNT_DISABLED("A0005", "账号已被禁用", 403),
    ACCOUNT_LOCKED("A0006", "账号已被锁定", 403),
    LOGIN_FAILED("A0007", "登录失败，用户名或密码错误", 400),
    LOGIN_LIMIT("A0008", "登录失败次数过多，请稍后再试", 429),
    CAPTCHA_ERROR("A0009", "验证码错误或已过期", 400),

    // ── 通用业务错误 B0xxx ────────────────────────────────────
    BIZ_ERROR("B0001", "业务处理失败", 400),
    DATA_NOT_FOUND("B0002", "数据不存在", 404),
    DATA_EXISTS("B0003", "数据已存在", 400),
    DATA_STATUS_ERROR("B0004", "数据状态异常", 400),
    OPERATION_NOT_ALLOWED("B0005", "不允许的操作", 403),
    DUPLICATE_SUBMIT("B0006", "重复提交，请勿重复操作", 429),

    // ── 文件错误 F0xxx ────────────────────────────────────────
    FILE_UPLOAD_FAILED("F0001", "文件上传失败", 500),
    FILE_SIZE_EXCEED("F0002", "文件大小超出限制", 400),
    FILE_TYPE_NOT_ALLOWED("F0003", "不支持的文件类型", 400),
    FILE_NOT_FOUND("F0004", "文件不存在", 404),
    FILE_DOWNLOAD_FAILED("F0005", "文件下载失败", 500),
    FILE_ACCESS_DENIED("F0006", "无文件访问权限", 403),
    FILE_TOKEN_EXPIRED("F0007", "文件访问令牌已过期", 401),

    // ── 参数校验错误 V0xxx ────────────────────────────────────
    PARAM_INVALID("V0001", "请求参数不合法", 400),
    PARAM_MISSING("V0002", "缺少必要参数", 400),
    PARAM_TYPE_ERROR("V0003", "参数类型错误", 400),
    REQUEST_METHOD_NOT_ALLOWED("V0004", "请求方法不支持", 405),
    REQUEST_MEDIA_TYPE_NOT_SUPPORTED("V0005", "不支持的媒体类型", 415),

    // ── 系统内部错误 S0xxx ────────────────────────────────────
    SYSTEM_ERROR("S0001", "系统内部错误", 500),
    SERVICE_UNAVAILABLE("S0002", "服务暂不可用", 503),
    REMOTE_CALL_ERROR("S0003", "远程调用异常", 500),
    DB_ERROR("S0004", "数据库操作异常", 500),
    TIMEOUT("S0005", "请求超时", 504),
    REMOTE_TIMEOUT("S0006", "远程调用超时", 504),
    REMOTE_SERVICE_ERROR("S0007", "远程服务内部错误", 502),
    CIRCUIT_BREAKER_OPEN("S0008", "服务熔断中，请稍后重试", 503);

    private final String code;
    private final String message;
    private final int httpStatus;

    WebErrorCode(String code, String message, int httpStatus) {
        this.code = code;
        this.message = message;
        this.httpStatus = httpStatus;
    }

    public String getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }

    public int getHttpStatus() {
        return httpStatus;
    }
}

