package io.github.guanxiangkai.jpa.plus.audit.event;

/**
 * 审计事件顶层接口（Sealed）
 *
 * <p>JPA Plus 审计体系的统一数据层事件类型，通过 {@link AuditEventPublisher} 发布。
 * 目前唯一的实现：</p>
 * <ul>
 *   <li>{@link DataAuditEvent} —— 由 {@link io.github.guanxiangkai.jpa.plus.audit.interceptor.AuditInterceptor}
 *       在 SAVE / DELETE 完成后自动发布，记录实体变更（含可选字段快照）。</li>
 * </ul>
 *
 * <p><b>设计模式：</b>观察者模式（Observer）+ Sealed 类型安全分发</p>
 *
 * @author guanxiangkai
 * @since 1.0.0
 */
public sealed interface AuditEvent permits DataAuditEvent {
}
