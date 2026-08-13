package io.github.guanxiangkai.jpa.plus.audit.event;

import io.github.guanxiangkai.jpa.plus.audit.snapshot.AuditSnapshot;
import io.github.guanxiangkai.jpa.plus.core.model.OperationType;

import java.time.Instant;

/**
 * 数据级审计事件（不可变值对象）
 *
 * <p>由 {@link io.github.guanxiangkai.jpa.plus.audit.interceptor.AuditInterceptor} 在
 * SAVE / DELETE 操作完成后自动发布，记录实体变更信息。</p>
 *
 * <p>建议在事务提交后处理，
 * 避免在事务内做耗时 IO 操作。</p>
 *
 * <p>记录被操作实体、操作类型、操作时间戳以及可选的字段变更快照。</p>
 *
 * @author guanxiangkai
 * @since 1.0.0
 */
public record DataAuditEvent(
        Object entity,
        OperationType operation,
        Instant timestamp,
        AuditSnapshot snapshot
) implements AuditEvent {

    /**
     * 使用当前时间构造，含快照
     */
    public DataAuditEvent(Object entity, OperationType operation, AuditSnapshot snapshot) {
        this(entity, operation, Instant.now(), snapshot);
    }

    /**
     * 是否包含变更快照
     */
    public boolean hasSnapshot() {
        return snapshot != null && snapshot.hasChanges();
    }
}
