package io.github.guanxiangkai.jpa.plus.starter;

import io.github.guanxiangkai.jpa.plus.audit.event.AsyncAuditEventPublisher;
import io.github.guanxiangkai.jpa.plus.audit.event.AuditEventPublisher;
import io.github.guanxiangkai.jpa.plus.audit.event.SyncAuditEventPublisher;
import io.github.guanxiangkai.jpa.plus.audit.interceptor.AuditInterceptor;
import io.github.guanxiangkai.jpa.plus.audit.spi.AuditEventErrorHandler;
import io.github.guanxiangkai.jpa.plus.audit.spi.LoggingAuditEventErrorHandler;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;

/**
 * 审计模块自动装配
 *
 * <p>职责范围：<b>数据访问层</b>的实体变更审计事件生成与发布。</p>
 *
 * <pre>{@code
 * JPA SAVE/DELETE
 *      ↓
 * AuditInterceptor / SnapshotAuditInterceptor
 *      ↓ 生成
 * DataAuditEvent（含可选字段快照 AuditSnapshot）
 *      ↓
 * AuditEventPublisher（同步 or 虚拟线程异步）
 *      ↓
 * Spring ApplicationEventPublisher
 *      ↓
 * @EventListener / @TransactionalEventListener
 * }</pre>
 *
 * <h3>配置项</h3>
 * <pre>{@code
 * jpa-plus:
 *   audit:
 *     async:
 *       enabled: false   # 是否用虚拟线程异步发布审计事件（默认同步）
 * }</pre>
 *
 * <h3>SPI 扩展点</h3>
 * <table border="1">
 *   <tr><th>SPI 接口</th><th>作用</th><th>默认行为</th></tr>
 *   <tr><td>{@link AuditEventErrorHandler}</td><td>异步模式下事件发布失败回调</td><td>打印 WARN 日志</td></tr>
 * </table>
 *
 * @author guanxiangkai
 * @since 1.0.0
 */
@AutoConfiguration
public class JpaPlusAuditAutoConfiguration {

    // ─── 错误处理器 ───

    @Bean
    @ConditionalOnMissingBean(AuditEventErrorHandler.class)
    public AuditEventErrorHandler auditEventErrorHandler() {
        return new LoggingAuditEventErrorHandler();
    }

    // ─── 审计事件发布者（同步 / 异步二选一） ───

    @Bean
    @ConditionalOnMissingBean(AuditEventPublisher.class)
    public AuditEventPublisher auditEventPublisher(
            ApplicationEventPublisher applicationEventPublisher,
            AuditEventErrorHandler errorHandler,
            @Value("${jpa-plus.audit.async.enabled:false}") boolean asyncEnabled) {
        SyncAuditEventPublisher sync = new SyncAuditEventPublisher(applicationEventPublisher);
        if (asyncEnabled) {
            return new AsyncAuditEventPublisher(sync, errorHandler);
        }
        return sync;
    }

    // ─── 数据级审计拦截器（由 InterceptorChain 自动收集） ───

    @Bean
    @ConditionalOnMissingBean(AuditInterceptor.class)
    public AuditInterceptor auditInterceptor(AuditEventPublisher auditEventPublisher) {
        return new AuditInterceptor(auditEventPublisher);
    }
}

