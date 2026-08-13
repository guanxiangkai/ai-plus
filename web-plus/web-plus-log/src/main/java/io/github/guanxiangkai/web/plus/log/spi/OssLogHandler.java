package io.github.guanxiangkai.web.plus.log.spi;

import io.github.guanxiangkai.web.plus.log.entity.BaseLog;

/**
 * OSS 上传日志持久化策略 SPI
 *
 * <h3>适用范围</h3>
 * <p>
 * 仅处理文件上传场景。下载通常由浏览器或外部对象存储直连完成，框架层无法稳定拦截，
 * 因此不再拆分 {@code UploadLogHandler} / {@code DownloadLogHandler} /
 * {@code FileOperationLogHandler}，统一收敛为本接口。
 * </p>
 *
 * <h3>字段名约定（实体字段名与此一致时由框架自动填充）</h3>
 * <pre>
 * 公共（BaseLog setter）: traceId, userId, username, tenantId, clientIp, status, message, logTime
 * 专属（反射写入）       : operationId, operation, fileId, originalName, storedName,
 *                         fileType, fileSize, bucketName, storagePath, costMs, errorMessage
 * </pre>
 *
 * <p>
 * 其中 {@code operation} 固定写入 {@code UPLOAD}，用于标识文件上传操作。
 * </p>
 *
 * <h3>推荐实现示例</h3>
 * <pre>{@code
 * @Entity @Table(name = "sys_oss_log")
 * public class SysOssLog extends BaseLog {
 *     private String operationId;
 *     private String operation;   // 固定为 UPLOAD
 *     private String fileId;
 *     private String originalName;
 *     private String storedName;
 *     private String fileType;
 *     private Long fileSize;
 *     private String bucketName;
 *     private String storagePath;
 *     private Long costMs;
 *     private String errorMessage;
 * }
 *
 * @Component @RequiredArgsConstructor
 * public class SysOssLogHandler implements OssLogHandler {
 *     private final SysOssLogRepository repo;
 *
 *     @Async @Override
 *     public void handle(BaseLog entity) {
 *         repo.save((SysOssLog) entity);
 *     }
 * }
 * }</pre>
 *
 * @author guanxiangkai
 * @since 1.0.0
 */
@FunctionalInterface
public interface OssLogHandler {
    /**
     * @param entity 已填充字段的日志实体；未配置实体类时为 {@code null}
     */
    void handle(BaseLog entity);
}
