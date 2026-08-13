package io.github.guanxiangkai.web.plus.core.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.RuntimeHintsRegistrar;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.task.TaskExecutionAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ImportRuntimeHints;
import org.springframework.context.annotation.Primary;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.core.task.support.TaskExecutorAdapter;
import org.springframework.scheduling.annotation.AsyncConfigurer;
import org.springframework.scheduling.annotation.EnableAsync;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * 虚拟线程配置
 * <p>
 * GraalVM JDK 25 + Spring Boot 4 特性：
 * 1. Virtual Threads（JDK 21+）- 轻量级线程
 * 2. 继承 Spring Boot 自动配置，添加虚拟线程支持
 * 3. 支持百万级并发
 * 4. GraalVM Native Image 优化
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
@Slf4j
@EnableAsync
@AutoConfiguration(after = TaskExecutionAutoConfiguration.class)
@ImportRuntimeHints(VirtualThreadConfig.VirtualThreadHints.class)
@ConditionalOnProperty(name = "spring.threads.virtual.enabled", havingValue = "true", matchIfMissing = true)
public class VirtualThreadConfig implements AsyncConfigurer {

    /**
     * 共享的虚拟线程执行器实例。
     * <p>
     * ⚠️ 必须在字段层面预初始化，而不能在 {@code applicationTaskExecutor()} 和
     * {@code getAsyncExecutor()} 内各自 new：{@code @AutoConfiguration} 默认
     * {@code proxyBeanMethods = false}，对 {@code @Bean} 方法的直接调用不经过
     * CGLIB 代理，每次都会创建新实例，导致 Spring 容器持有的 Bean 与
     * {@link org.springframework.scheduling.annotation.AsyncConfigurer} 使用的
     * 执行器不是同一个对象。
     * </p>
     */
    private final AsyncTaskExecutor virtualThreadExecutor =
            new TaskExecutorAdapter(Executors.newVirtualThreadPerTaskExecutor());

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

    /**
     * GraalVM Native Image 运行时提示
     */
    static class VirtualThreadHints implements RuntimeHintsRegistrar {
        @Override
        public void registerHints(RuntimeHints hints, ClassLoader classLoader) {
            // 注册虚拟线程相关类
            hints.reflection()
                    .registerType(Thread.class, hint -> hint
                            .withMembers(
                                    org.springframework.aot.hint.MemberCategory.INVOKE_PUBLIC_METHODS
                            ));

            hints.reflection()
                    .registerType(Executors.class, hint -> hint
                            .withMembers(
                                    org.springframework.aot.hint.MemberCategory.INVOKE_PUBLIC_METHODS
                            ));
        }
    }
}
