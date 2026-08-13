package io.github.guanxiangkai.web.plus.log.spi;

import io.github.guanxiangkai.web.plus.log.entity.BaseLog;
import io.github.guanxiangkai.web.plus.log.model.FieldChange;

/**
 * 数据变更日志持久化策略 SPI
 *
 * <h3>字段名约定（实体字段名与此一致时由桥接器自动填充）</h3>
 * <pre>
 * 公共（BaseLog setter）: traceId, userId, tenantId, status, logTime
 * 专属（反射写入）       : operationId, entityType, entityId, changeType,
 *                         fieldChanges（JSON 序列化后的 List&lt;{@link FieldChange}&gt;）
 * </pre>
 *
 * <h3>关联原理</h3>
 * <p>
 * {@code @OperationLog} 切面在方法执行前将操作上下文写入
 * {@link io.github.guanxiangkai.web.plus.log.context.OperationLogContext}，
 * {@link io.github.guanxiangkai.web.plus.log.bridge.JpaPlusDataAuditEventBridge} 监听到
 * jpa-plus {@code DataAuditEvent} 后通过 {@code operationId} 与操作日志关联，
 * 填充实体后调用此 SPI 持久化。
 * </p>
 *
 * <h3>推荐实现示例</h3>
 * <pre>{@code
 * // 1. 配置实体类（application.yml）
 * //    web-plus.log.data-change-entity-class: com.example.SysDataChangeLog
 *
 * // 2. 定义实体
 * @Entity @Table(name = "sys_data_change_log")
 * public class SysDataChangeLog extends BaseLog {
 *     private String operationId;
 *     private String entityType;
 *     private String entityId;
 *     private String changeType;    // INSERT / UPDATE / DELETE
 *     @Column(columnDefinition = "TEXT") private String fieldChanges;  // JSON
 * }
 *
 * // 3. 实现 Handler SPI
 * @Component @RequiredArgsConstructor
 * public class SysDataChangeLogHandler implements DataChangeHandler {
 *     private final SysDataChangeLogRepository repo;
 *     @Async @Override
 *     public void handle(BaseLog entity) { repo.save((SysDataChangeLog) entity); }
 * }
 * }</pre>
 *
 * @author guanxiangkai
 * @see io.github.guanxiangkai.web.plus.log.context.OperationLogContext
 * @see FieldChange
 * @since 1.0.0
 */
@FunctionalInterface
public interface DataChangeHandler {
    /**
     * @param entity 已填充字段的日志实体；未配置实体类时为 {@code null}
     */
    void handle(BaseLog entity);
}
