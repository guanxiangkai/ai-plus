package io.github.guanxiangkai.web.plus.log.spi;

import io.github.guanxiangkai.web.plus.log.entity.BaseLog;

/**
 * 定时任务日志持久化策略 SPI
 *
 * <h3>字段名约定（实体字段名与此一致时由切面自动填充）</h3>
 * <pre>
 * 公共（BaseLog setter）: traceId, userId, username, tenantId, status, message, logTime
 * 专属（反射写入）       : jobName, jobGroup, cronExpression, triggerType, description,
 *                         params, startTime, endTime, costMs, executorNode, errorMessage
 * </pre>
 *
 * <h3>推荐实现示例</h3>
 * <pre>{@code
 * @Entity @Table(name = "sys_schedule_log")
 * public class SysScheduleLog extends BaseLog {
 *     private String jobName;
 *     private String jobGroup;
 *     private String cronExpression;
 *     private String description;
 *     @Column(columnDefinition = "TEXT") private String params;
 *     private LocalDateTime startTime;
 *     private LocalDateTime endTime;
 *     private Long   costMs;
 *     private String executorNode;
 *     @Column(columnDefinition = "TEXT") private String errorMessage;
 * }
 *
 * @Component @RequiredArgsConstructor
 * public class SysScheduleLogHandler implements ScheduleLogHandler {
 *     private final SysScheduleLogRepository repo;
 *     @Async @Override
 *     public void handle(BaseLog entity) { repo.save((SysScheduleLog) entity); }
 * }
 * }</pre>
 *
 * @author guanxiangkai
 * @since 1.0.0
 */
@FunctionalInterface
public interface ScheduleLogHandler {
    void handle(BaseLog entity);
}
