package io.github.guanxiangkai.jpa.plus.audit.interceptor;

import io.github.guanxiangkai.jpa.plus.audit.event.AuditEventPublisher;
import io.github.guanxiangkai.jpa.plus.audit.event.DataAuditEvent;
import io.github.guanxiangkai.jpa.plus.core.interceptor.Chain;
import io.github.guanxiangkai.jpa.plus.core.interceptor.DataInterceptor;
import io.github.guanxiangkai.jpa.plus.core.interceptor.Phase;
import io.github.guanxiangkai.jpa.plus.core.model.DataInvocation;
import io.github.guanxiangkai.jpa.plus.core.model.DeleteInvocation;
import io.github.guanxiangkai.jpa.plus.core.model.OperationType;
import io.github.guanxiangkai.jpa.plus.core.model.SaveInvocation;
import lombok.extern.slf4j.Slf4j;

/**
 * 审计拦截器
 *
 * <p>在数据操作（SAVE / DELETE）完成后，通过 {@link AuditEventPublisher}
 * 发布 {@link DataAuditEvent}。</p>
 *
 * <ul>
 *   <li>同步模式（默认）：{@link io.github.guanxiangkai.jpa.plus.audit.event.SyncAuditEventPublisher}
 *       直接委托 Spring ApplicationEventPublisher，建议在事务提交后处理</li>
 *   <li>异步模式：配置 {@code jpa-plus.audit.async.enabled=true} 后自动切换为
 *       {@link io.github.guanxiangkai.jpa.plus.audit.event.AsyncAuditEventPublisher}（虚拟线程）</li>
 * </ul>
 *
 * @author guanxiangkai
 * @since 1.0.0
 */
@Slf4j
public class AuditInterceptor implements DataInterceptor {

    private final AuditEventPublisher eventPublisher;

    public AuditInterceptor(AuditEventPublisher eventPublisher) {
        this.eventPublisher = eventPublisher;
    }

    @Override
    public int order() {
        return 600;
    }

    @Override
    public Phase phase() {
        return Phase.AFTER;
    }

    @Override
    public boolean supports(OperationType type) {
        return type == OperationType.SAVE || type == OperationType.DELETE;
    }

    @Override
    public Object intercept(DataInvocation invocation, Chain chain) throws Throwable {
        Object result = chain.proceed(invocation);
        Object entity = switch (invocation) {
            case SaveInvocation si -> si.entity();
            case DeleteInvocation di -> di.entity();
            default -> null;
        };
        if (entity != null) {
            try {
                eventPublisher.publish(new DataAuditEvent(entity, invocation.type(), null));
            } catch (Exception e) {
                log.warn("[jpa-plus] Failed to publish AuditEvent for entity={}, operation={}",
                        entity.getClass().getSimpleName(), invocation.type(), e);
            }
        }
        return result;
    }
}
