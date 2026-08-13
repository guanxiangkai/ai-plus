package io.github.guanxiangkai.jpa.plus.audit.interceptor;

import io.github.guanxiangkai.jpa.plus.audit.annotation.AuditExclude;
import io.github.guanxiangkai.jpa.plus.audit.event.AuditEventPublisher;
import io.github.guanxiangkai.jpa.plus.audit.event.DataAuditEvent;
import io.github.guanxiangkai.jpa.plus.audit.snapshot.AuditSnapshot;
import io.github.guanxiangkai.jpa.plus.audit.snapshot.SnapshotSerializer;
import io.github.guanxiangkai.jpa.plus.core.interceptor.Chain;
import io.github.guanxiangkai.jpa.plus.core.interceptor.DataInterceptor;
import io.github.guanxiangkai.jpa.plus.core.interceptor.Phase;
import io.github.guanxiangkai.jpa.plus.core.model.DataInvocation;
import io.github.guanxiangkai.jpa.plus.core.model.DeleteInvocation;
import io.github.guanxiangkai.jpa.plus.core.model.OperationType;
import io.github.guanxiangkai.jpa.plus.core.model.SaveInvocation;
import jakarta.persistence.EntityManager;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;

/**
 * 轻量快照审计拦截器
 *
 * <p>在 SAVE / DELETE 操作执行前通过实体标识读取当前持久化状态作为 "before 快照"，
 * 操作完成后对比实体字段差异生成 {@link AuditSnapshot}，并附加到 {@link DataAuditEvent}
 * 一并发布。</p>
 *
 * <h3>字段过滤（{@code @AuditExclude}）</h3>
 * <p>在实体字段上标注 {@link AuditExclude} 可跳过该字段的快照采集，适用于加密、脱敏、大字段等。</p>
 *
 * <h3>自定义序列化（{@link SnapshotSerializer}）</h3>
 * <p>可通过构造函数传入自定义 {@link SnapshotSerializer}，控制字段值的存储格式：
 * 默认使用 {@link SnapshotSerializer#NOOP}（保留原始 Java 对象），
 * 也可注入 {@link io.github.guanxiangkai.jpa.plus.audit.snapshot.JacksonSnapshotSerializer} 将复杂类型转为 JSON 字符串。</p>
 *
 * <h3>使用方式</h3>
 * <pre>{@code
 * // 默认（无序列化）
 * // 注册为 Spring Bean
 * public SnapshotAuditInterceptor snapshotAuditInterceptor(
 *         AuditEventPublisher publisher, EntityManager em) {
 *     return new SnapshotAuditInterceptor(publisher, em);
 * }
 *
 * // 使用 Jackson 序列化
 * // 注册为 Spring Bean
 * public SnapshotAuditInterceptor snapshotAuditInterceptor(
 *         AuditEventPublisher publisher, EntityManager em, ObjectMapper objectMapper) {
 *     return new SnapshotAuditInterceptor(publisher, em,
 *             new JacksonSnapshotSerializer(objectMapper));
 * }
 * }</pre>
 *
 * @author guanxiangkai
 * @since 1.0.0
 */
@Slf4j
public class SnapshotAuditInterceptor implements DataInterceptor {

    private final AuditEventPublisher eventPublisher;
    private final AuditSnapshotCollector snapshotCollector;

    /**
     * 使用默认 NOOP 序列化器（字段值保持原始 Java 对象类型）
     */
    public SnapshotAuditInterceptor(AuditEventPublisher eventPublisher, EntityManager entityManager) {
        this(eventPublisher, entityManager, SnapshotSerializer.NOOP);
    }

    /**
     * 使用自定义序列化器
     *
     * @param serializer 字段值序列化策略（如 {@link io.github.guanxiangkai.jpa.plus.audit.snapshot.JacksonSnapshotSerializer}）
     */
    public SnapshotAuditInterceptor(AuditEventPublisher eventPublisher,
                                    EntityManager entityManager,
                                    SnapshotSerializer serializer) {
        this.eventPublisher = eventPublisher;
        this.snapshotCollector = new AuditSnapshotCollector(entityManager, serializer);
    }

    @Override
    public int order() {
        return 590;
    }

    @Override
    public Phase phase() {
        return Phase.BEFORE;
    }

    @Override
    public boolean supports(OperationType type) {
        return type == OperationType.SAVE || type == OperationType.DELETE;
    }

    @Override
    public Object intercept(DataInvocation invocation, Chain chain) throws Throwable {
        Object entity = switch (invocation) {
            case SaveInvocation si -> si.entity();
            case DeleteInvocation di -> di.entity();
            default -> null;
        };
        if (entity == null) return chain.proceed(invocation);
        Class<?> entityClass = invocation.entityClass();

        // P1-01: Read the "before" snapshot from an isolated persistence context so managed entities
        // do not see their own in-memory dirty state as the persisted state.
        Map<String, Object> beforeValues = snapshotCollector.loadPersistedSnapshot(entityClass, entity);

        Object result = chain.proceed(invocation);

        if (invocation.type() == OperationType.SAVE) {
            Object auditedEntity = resolveSaveAuditEntity(entity, result, entityClass);
            Map<String, Object> afterValues = snapshotCollector.captureValues(auditedEntity, entityClass);
            AuditSnapshot snapshot = snapshotCollector.diff(entityClass, beforeValues, afterValues);
            publish(auditedEntity, invocation.type(), snapshot, entityClass);
        } else {
            AuditSnapshot snapshot = snapshotCollector.deleteSnapshot(entityClass, beforeValues);
            publish(entity, invocation.type(), snapshot, entityClass);
        }
        return result;
    }

    private Object resolveSaveAuditEntity(Object originalEntity, Object result, Class<?> entityClass) {
        if (entityClass.isInstance(result)) {
            return result;
        }
        return originalEntity;
    }

    private void publish(Object entity, OperationType operationType, AuditSnapshot snapshot, Class<?> entityClass) {
        try {
            eventPublisher.publish(new DataAuditEvent(entity, operationType, snapshot));
        } catch (Exception e) {
            log.warn("[jpa-plus] Failed to publish SnapshotAuditEvent for entity={}, operation={}",
                    entityClass.getSimpleName(), operationType, e);
        }
    }
}
