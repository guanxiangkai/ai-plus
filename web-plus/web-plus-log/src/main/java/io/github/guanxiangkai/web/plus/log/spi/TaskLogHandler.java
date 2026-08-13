package io.github.guanxiangkai.web.plus.log.spi;

import io.github.guanxiangkai.web.plus.log.entity.BaseLog;

/**
 * 自定义任务日志持久化策略 SPI
 *
 * <h3>字段名约定（实体字段名与此一致时由切面自动填充）</h3>
 * <pre>
 * 公共（BaseLog setter）: traceId, userId, username, tenantId, status, message, logTime
 * 专属（反射写入）       : taskId, taskName, taskType, description, params,
 *                         operationId, startTime, endTime, costMs, errorMessage
 * </pre>
 *
 * <h3>推荐实现示例</h3>
 * <pre>{@code
 * @Entity @Table(name = "sys_task_log")
 * public class SysTaskLog extends BaseLog {
 *     private String taskName;
 *     private String taskType;
 *     private String description;
 *     @Column(columnDefinition = "TEXT") private String params;
 *     private String operationId;
 *     private LocalDateTime startTime;
 *     private LocalDateTime endTime;
 *     private Long   costMs;
 *     @Column(columnDefinition = "TEXT") private String errorMessage;
 * }
 *
 * @Component @RequiredArgsConstructor
 * public class SysTaskLogHandler implements TaskLogHandler {
 *     private final SysTaskLogRepository repo;
 *     @Async @Override
 *     public void handle(BaseLog entity) { repo.save((SysTaskLog) entity); }
 * }
 * }</pre>
 *
 * @author guanxiangkai
 * @since 1.0.0
 */
@FunctionalInterface
public interface TaskLogHandler {
    void handle(BaseLog entity);
}
