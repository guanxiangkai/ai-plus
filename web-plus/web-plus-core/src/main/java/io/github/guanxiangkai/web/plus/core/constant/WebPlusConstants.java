package io.github.guanxiangkai.web.plus.core.constant;

import java.util.Set;

/**
 * Web Plus 框架常量定义
 *
 * @author guanxiangkai
 * @since 1.0.0
 */
public final class WebPlusConstants {

    /**
     * 默认 Token 请求头
     */
    public static final String TOKEN_HEADER = "Authorization";

    // ── 请求头 ──────────────────────────────────────────────────
    /**
     * Bearer 前缀
     */
    public static final String TOKEN_PREFIX = "Bearer ";
    /**
     * 链路追踪 ID 请求头
     */
    public static final String TRACE_ID_HEADER = "X-Trace-Id";
    /**
     * 用户 ID 透传请求头（网关透传场景）
     */
    public static final String USER_ID_HEADER = "X-User-Id";
    /**
     * 租户 ID 透传请求头
     */
    public static final String TENANT_ID_HEADER = "X-Tenant-Id";
    /**
     * 用户 Claims 透传请求头，网关默认写入 UTF-8 Base64URL 编码后的 JSON
     */
    public static final String USER_CLAIMS_HEADER = "X-User-Claims";
    /**
     * 用户 Claims 编码方式透传请求头
     */
    public static final String USER_CLAIMS_ENCODING_HEADER = "X-User-Claims-Encoding";
    /**
     * MDC 中的 TraceId Key
     */
    public static final String MDC_TRACE_ID = "traceId";

    // ── MDC Key ─────────────────────────────────────────────────
    /**
     * MDC 中的 UserId Key
     */
    public static final String MDC_USER_ID = "userId";
    /**
     * MDC 中的 TenantId Key
     */
    public static final String MDC_TENANT_ID = "tenantId";
    // ── 文件导入 ─────────────────────────────────────────────────
    /**
     * 支持导入的文件后缀（Excel + Word）
     */
    public static final Set<String> IMPORT_EXTENSIONS = Set.of(".xlsx", ".xls", ".doc", ".docx");

    private WebPlusConstants() {
    }
}
