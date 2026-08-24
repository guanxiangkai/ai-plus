package io.github.guanxiangkai.web.plus.core.config;

import io.github.guanxiangkai.web.plus.core.context.RequestContextThreadLocalAccessor;
import io.micrometer.context.ContextRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.core.task.TaskDecorator;
import org.springframework.core.task.support.ContextPropagatingTaskDecorator;
import reactor.core.publisher.Hooks;

/**
 * Web Plus 统一上下文传播自动配置。
 *
 * <p>应用启动时启用 Reactor 自动传播，并为 Reactor 与异步任务注册同一个请求上下文适配器。</p>
 *
 * @author guanxiangkai
 * @since 1.0.0
 */
@Slf4j
@AutoConfiguration(before = VirtualThreadConfig.class)
public class ContextPropagationAutoConfiguration {

    /** 供默认执行器和业务自定义线程池共同注入的任务装饰器 Bean 名。 */
    public static final String TASK_DECORATOR_BEAN_NAME = "webPlusContextTaskDecorator";

    /** 在任何 Reactor 订阅创建前启用自动上下文传播。 */
    public ContextPropagationAutoConfiguration() {
        Hooks.enableAutomaticContextPropagation();
        log.info("[web-plus] Reactor 自动上下文传播已启用");
    }

    /** 注册请求上下文与 MDC TraceId 的双向适配器。 */
    @Bean
    @ConditionalOnMissingBean(RequestContextThreadLocalAccessor.class)
    public RequestContextThreadLocalAccessor requestContextThreadLocalAccessor() {
        return new RequestContextThreadLocalAccessor();
    }

    /** 将默认或调用方覆盖的请求上下文适配器注册到全局 Context Registry。 */
    @Bean
    public SmartInitializingSingleton requestContextAccessorRegistrar(
            RequestContextThreadLocalAccessor accessor) {
        return () -> ContextRegistry.getInstance().registerThreadLocalAccessor(accessor);
    }

    /** 创建供虚拟线程与业务线程池复用的上下文任务装饰器。 */
    @Bean(TASK_DECORATOR_BEAN_NAME)
    @ConditionalOnMissingBean(name = TASK_DECORATOR_BEAN_NAME)
    public TaskDecorator contextTaskDecorator(RequestContextThreadLocalAccessor accessor) {
        return new ContextPropagatingTaskDecorator();
    }
}
