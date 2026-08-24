package io.github.guanxiangkai.web.plus.core.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.task.TaskExecutionAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.core.task.SimpleAsyncTaskExecutor;
import org.springframework.core.task.TaskDecorator;
import org.springframework.core.task.support.ContextPropagatingTaskDecorator;
import org.springframework.scheduling.annotation.AsyncConfigurer;
import org.springframework.scheduling.annotation.EnableAsync;

import java.util.concurrent.Executor;

/**
 * 虚拟线程配置
 * <p>
 * GraalVM JDK 25 + Spring Boot 4 特性：
 * 1. Virtual Threads（JDK 21+）- 轻量级线程
 * 2. 继承 Spring Boot 自动配置，添加虚拟线程支持
 * 3. 按任务创建轻量虚拟线程
 * 4. 使用 Spring Framework 原生虚拟线程执行器生命周期
 * </p>
 * <p>
 * 优势：
 * - 线程创建成本极低（几乎为零）
 * - 阻塞操作不浪费 OS 线程
 * - 无需配置线程池大小
 * - 自动调度和管理
 * - 与 Spring Boot 自动配置完美协作
 * </p>
 * <p>
 * 配置方式：
 * - 默认启用：无需配置
 * - 禁用虚拟线程：spring.threads.virtual.enabled=false
 * </p>
 *
 * @author guanxiangkai
 * @since 1.0.0
 */
@EnableAsync
@AutoConfiguration(after = TaskExecutionAutoConfiguration.class)
@ConditionalOnProperty(name = "spring.threads.virtual.enabled", havingValue = "true", matchIfMissing = true)
public class VirtualThreadConfig implements AsyncConfigurer {

    /**
     * 共享的虚拟线程执行器实例。
     * <p>
     * ⚠️ 必须在构造阶段只创建一次，而不能在 {@code applicationTaskExecutor()} 和
     * {@code getAsyncExecutor()} 内各自创建：{@code @AutoConfiguration} 默认
     * {@code proxyBeanMethods = false}，对 {@code @Bean} 方法的直接调用不经过
     * CGLIB 代理，每次都会创建新实例，导致 Spring 容器持有的 Bean 与
     * {@link org.springframework.scheduling.annotation.AsyncConfigurer} 使用的
     * 执行器不是同一个对象。构造阶段同时绑定上下文任务装饰器。
     * </p>
     */
    private final AsyncTaskExecutor virtualThreadExecutor;

    /** 创建使用默认 Micrometer 上下文装饰器的独立配置实例。 */
    public VirtualThreadConfig() {
        this(new ContextPropagatingTaskDecorator());
    }

    /**
     * 创建统一使用上下文任务装饰器的虚拟线程执行器配置。
     *
     * @param taskDecorator Web Plus 上下文任务装饰器
     */
    @Autowired
    public VirtualThreadConfig(
            @Qualifier(ContextPropagationAutoConfiguration.TASK_DECORATOR_BEAN_NAME)
            TaskDecorator taskDecorator) {
        SimpleAsyncTaskExecutor executor = new SimpleAsyncTaskExecutor("web-plus-virtual-");
        executor.setVirtualThreads(true);
        executor.setTaskDecorator(taskDecorator);
        this.virtualThreadExecutor = executor;
    }

    /**
     * 虚拟线程异步任务执行器
     * <p>
     * 覆盖 Spring Boot 默认的 TaskExecutor，所有 {@code @Async} 方法将使用虚拟线程执行。
     * </p>
     */
    @Bean(name = "applicationTaskExecutor")
    @Primary
    public AsyncTaskExecutor applicationTaskExecutor() {
        return virtualThreadExecutor;
    }

    @Override
    public Executor getAsyncExecutor() {
        return virtualThreadExecutor;
    }
}
