package io.github.guanxiangkai.web.plus.core.constants;

/**
 * 操作类型常量
 * <p>
 * 用于 {@code @OperationLog} 的操作类型标识。
 * 在 {@code @OperationLog(typeCode = OperationTypes.QUERY, ...)} 中引用。
 * </p>
 *
 * @author guanxiangkai
 * @since 1.0.0
 */
public final class OperationTypes {

    /**
     * 查询
     */
    public static final String QUERY = "QUERY";
    /**
     * 新增
     */
    public static final String INSERT = "INSERT";
    /**
     * 修改
     */
    public static final String UPDATE = "UPDATE";
    /**
     * 删除
     */
    public static final String DELETE = "DELETE";
    /**
     * 导入
     */
    public static final String IMPORT = "IMPORT";
    /**
     * 导出
     */
    public static final String EXPORT = "EXPORT";
    /**
     * 数据同步
     */
    public static final String DATA_SYNC = "DATA_SYNC";
    /**
     * 文件上传
     */
    public static final String UPLOAD = "UPLOAD";
    /**
     * 文件下载
     */
    public static final String DOWNLOAD = "DOWNLOAD";
    /**
     * 文件删除
     */
    public static final String FILE_DELETE = "FILE_DELETE";
    /**
     * 登录
     */
    public static final String LOGIN = "LOGIN";
    /**
     * 登出
     */
    public static final String LOGOUT = "LOGOUT";
    /**
     * Token 刷新
     */
    public static final String TOKEN_REFRESH = "TOKEN_REFRESH";
    /**
     * 定时任务
     */
    public static final String SCHEDULE = "SCHEDULE";
    /**
     * 后台任务
     */
    public static final String TASK = "TASK";
    /**
     * AI 大模型调用
     */
    public static final String AI_CALL = "AI_CALL";
    /**
     * SSE 推送
     */
    public static final String SSE_SEND = "SSE_SEND";
    /**
     * 其他
     */
    public static final String OTHER = "OTHER";
    private OperationTypes() {
    }
}
