package io.github.guanxiangkai.web.plus.log.context;

import io.micrometer.context.ThreadLocalAccessor;

/**
 * Micrometer {@link ThreadLocalAccessor} for {@link OperationLogContext}
 *
 * <p>
 * 将 {@link OperationLogContext} 的 {@link InheritableThreadLocal} 桥接到 Reactor Context，
 * 使 jpa-plus {@code DataAuditEvent} 监听器在 WebFlux + 阻塞 JPA 场景下
 * 能通过 {@link OperationLogContext#current()} 读取当前操作上下文，从而与操作日志精确关联。
 * </p>
 *
 * <h3>注册方式</h3>
 * <p>
 * 当 {@code web-plus.log.context-propagation-enabled=true}（默认开启）时，
 * 框架自动调用 {@code Hooks.enableAutomaticContextPropagation()} 并将此 Accessor
 * 注册到 {@code ContextRegistry}，<b>无需业务方手动配置</b>。
 * </p>
 * <p>如需关闭自动注册，配置 {@code web-plus.log.context-propagation-enabled=false}，
 * 然后按需手动注册：</p>
 * <pre>{@code
 * @Bean
 * ApplicationRunner registerOperationLogContextAccessor(OperationLogContextAccessor accessor) {
 *     return args -> ContextRegistry.getInstance().registerThreadLocalAccessor(accessor);
 * }
 * }</pre>
 *
 * <h3>工作原理</h3>
 * <ul>
 *   <li>{@link io.github.guanxiangkai.web.plus.log.aspect.OperationLogAspect} 在方法入口通过 {@code contextWrite} 将上下文写入 Reactor Context</li>
 *   <li>当响应式链路切换到 {@code Schedulers.boundedElastic()} 线程（如 JPA 阻塞操作）时，
 *       Micrometer 自动调用 {@link #setValue(OperationLogContext.OperationContext)} 还原 ThreadLocal，
 *       使 jpa-plus 审计事件监听器可直接调用 {@link OperationLogContext#current()} 读取操作 ID</li>
 * </ul>
 *
 * @author guanxiangkai
 * @see OperationLogContext
 * @since 1.0.0
 */
public final class OperationLogContextAccessor implements ThreadLocalAccessor<OperationLogContext.OperationContext> {

    @Override
    public Object key() {
        return OperationLogContext.REACTOR_KEY;
    }

    @Override
    public OperationLogContext.OperationContext getValue() {
        return OperationLogContext.current();
    }

    @Override
    public void setValue(OperationLogContext.OperationContext value) {
        OperationLogContext.set(value);
    }

    /**
     * 清除 ThreadLocal（Micrometer 在上下文不含此 key 时调用）
     */
    @Override
    public void setValue() {
        OperationLogContext.clear();
    }

    @Override
    public void restore(OperationLogContext.OperationContext previousValue) {
        if (previousValue != null) {
            OperationLogContext.set(previousValue);
        } else {
            OperationLogContext.clear();
        }
    }
}

