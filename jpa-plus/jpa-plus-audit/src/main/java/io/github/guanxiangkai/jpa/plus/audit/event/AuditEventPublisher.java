package io.github.guanxiangkai.jpa.plus.audit.event;

/**
 * 审计事件发布者接口
 *
 * <p>框架提供两种实现：</p>
 * <ul>
 *   <li>{@link SyncAuditEventPublisher} —— 同步发布（默认），委托给 Spring {@code ApplicationEventPublisher}</li>
 *   <li>{@link AsyncAuditEventPublisher} —— 异步发布，基于 JDK 25 虚拟线程，启用后不阻塞业务线程</li>
 * </ul>
 *
 * <p>通过 {@code jpa-plus.audit.async.enabled=true} 切换为异步模式。</p>
 *
 * @author guanxiangkai
 * @since 1.0.0
 */
@FunctionalInterface
public interface AuditEventPublisher {

    /**
     * 发布审计事件
     *
     * @param event 审计事件（包含实体、操作类型、时间戳）
     */
    void publish(AuditEvent event);
}

