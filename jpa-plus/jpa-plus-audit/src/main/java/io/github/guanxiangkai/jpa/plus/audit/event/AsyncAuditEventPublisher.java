package io.github.guanxiangkai.jpa.plus.audit.event;

import io.github.guanxiangkai.jpa.plus.audit.spi.AuditEventErrorHandler;
import lombok.extern.slf4j.Slf4j;

import java.util.Objects;

/**
 * 基于 JDK 25 虚拟线程的异步审计事件发布者
 *
 * <p>每次 {@link #publish} 调用都在一个新的虚拟线程中执行，业务线程立即返回，
 * 不阻塞事务提交路径。</p>
 *
 * <h3>背压保护</h3>
 * <p>使用 {@link java.util.concurrent.Semaphore} 限制同时在途的虚拟线程数量
 * （默认 {@code maxConcurrent = 1000}），防止批量导入等突发场景下无限制地创建虚拟线程
 * 耗尽堆内存。超过上限时，发布调用会阻塞业务线程直到有空闲槽。</p>
 *
 * <h3>错误处理</h3>
 * <p>虚拟线程内的异常由注入的 {@link AuditEventErrorHandler} 处理（默认打印 WARN 日志）。
 * 用户可实现 {@link AuditEventErrorHandler} 注册为 Bean 自定义错误处理（如告警、重试等）。</p>
 *
 * <h3>开启方式</h3>
 * <pre>{@code
 * # application.yml
 * jpa-plus:
 *   audit:
 *     async:
 *       enabled: true
 *       max-concurrent: 1000  # 最大并发虚拟线程数（默认 1000）
 * }</pre>
 *
 * <h3>注意事项</h3>
 * <ul>
 *   <li>事务提交后实体可能已 detached，异步处理时不应再触发懒加载</li>
 *   <li>虚拟线程由 JDK 25 托管调度，无需额外线程池配置</li>
 * </ul>
 *
 * @author guanxiangkai
 * @since 1.0.0
 */
@Slf4j
public class AsyncAuditEventPublisher implements AuditEventPublisher {

    private static final int DEFAULT_MAX_CONCURRENT = 1000;

    private final AuditEventPublisher delegate;
    private final AuditEventErrorHandler errorHandler;
    private final java.util.concurrent.Semaphore semaphore;

    public AsyncAuditEventPublisher(AuditEventPublisher delegate, AuditEventErrorHandler errorHandler) {
        this(delegate, errorHandler, DEFAULT_MAX_CONCURRENT);
    }

    public AsyncAuditEventPublisher(AuditEventPublisher delegate, AuditEventErrorHandler errorHandler,
                                    int maxConcurrent) {
        if (maxConcurrent <= 0) {
            throw new IllegalArgumentException("异步审计最大并发数必须大于 0");
        }
        this.delegate = Objects.requireNonNull(delegate, "审计事件发布器不能为空");
        this.errorHandler = Objects.requireNonNull(errorHandler, "审计异常处理器不能为空");
        this.semaphore = new java.util.concurrent.Semaphore(maxConcurrent);
    }

    @Override
    public void publish(AuditEvent event) {
        // 在启动虚拟线程前获取许可，避免批量审计事件无界创建在途任务。
        try {
            semaphore.acquire();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("[jpa-plus] 获取异步审计并发许可时被中断，当前事件未发布: {}", event);
            return;
        }
        Thread.ofVirtual()
                .name("jpa-plus-audit-", 0)
                .start(() -> {
                    try {
                        delegate.publish(event);
                    } catch (Throwable t) {
                        try {
                            errorHandler.onError(event, t);
                        } catch (Throwable ignored) {
                            log.error("[jpa-plus] 审计异常处理器执行失败", ignored);
                        }
                    } finally {
                        semaphore.release();
                    }
                });
    }
}
