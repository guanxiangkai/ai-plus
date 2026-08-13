package io.github.guanxiangkai.jpa.plus.audit.spi;

import io.github.guanxiangkai.jpa.plus.audit.event.AuditEvent;

/**
 * 审计事件错误处理器 SPI
 *
 * <p>在异步模式（{@code jpa-plus.audit.async.enabled=true}）下，
 * 当事件处理抛出异常时回调此接口。</p>
 *
 * <h3>默认行为</h3>
 * <p>框架提供内置实现 {@link LoggingAuditEventErrorHandler}，打印 WARN 级别日志。
 * 用户可实现此接口并注册为 Spring Bean 来覆盖默认行为（如告警、重试等）。</p>
 *
 * <h3>示例：钉钉告警</h3>
 * <pre>{@code
 * @Bean
 * public AuditEventErrorHandler dingTalkErrorHandler(DingTalkClient client) {
 *     return (event, error) -> client.sendAlert(
 *         "审计事件发布失败: entity=" + event.entity() + ", op=" + event.operation(), error);
 * }
 * }</pre>
 *
 * @author guanxiangkai
 * @since 1.0.0
 */
@FunctionalInterface
public interface AuditEventErrorHandler {

    /**
     * 处理审计事件发布失败
     *
     * @param event 发布失败的审计事件
     * @param error 导致失败的异常
     */
    void onError(AuditEvent event, Throwable error);
}

