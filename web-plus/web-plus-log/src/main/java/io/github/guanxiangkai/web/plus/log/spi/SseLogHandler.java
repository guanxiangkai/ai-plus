package io.github.guanxiangkai.web.plus.log.spi;

import io.github.guanxiangkai.web.plus.log.entity.BaseLog;

/**
 * SSE 消息推送日志持久化策略 SPI
 *
 * <h3>字段名约定（实体字段名与此一致时由切面自动填充）</h3>
 * <pre>
 * 公共（BaseLog setter）: traceId, userId, username, tenantId, status, message, logTime
 * 专属（反射写入）       : messageType, targetType, targetUserId, description,
 *                         content, costMs, failReason, operationId
 * </pre>
 *
 * <h3>推荐实现示例</h3>
 * <pre>{@code
 * @Entity @Table(name = "sys_sse_log")
 * public class SysSseLog extends BaseLog {
 *     private String messageType;
 *     private String targetType;
 *     private String targetUserId;
 *     private String description;
 *     @Column(columnDefinition = "TEXT") private String content;
 *     private Long   costMs;
 *     private String failReason;
 *     private String operationId;
 * }
 *
 * @Component @RequiredArgsConstructor
 * public class SysSseLogHandler implements SseLogHandler {
 *     private final SysSseLogRepository repo;
 *     @Async @Override
 *     public void handle(BaseLog entity) { repo.save((SysSseLog) entity); }
 * }
 * }</pre>
 *
 * @author guanxiangkai
 * @since 1.0.0
 */
@FunctionalInterface
public interface SseLogHandler {
    void handle(BaseLog entity);
}
