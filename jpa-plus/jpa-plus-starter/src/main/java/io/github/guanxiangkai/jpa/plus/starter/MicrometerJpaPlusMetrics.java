package io.github.guanxiangkai.jpa.plus.starter;

import io.github.guanxiangkai.jpa.plus.core.metrics.JpaPlusMetrics;
import io.github.guanxiangkai.jpa.plus.core.model.OperationType;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * 基于 Micrometer 的 JPA Plus 指标实现
 *
 * <p>当 {@code micrometer-core} 在 classpath 时由 {@code JpaPlusAutoConfiguration} 自动装配。
 * 向 Micrometer {@link MeterRegistry} 注册以下 {@link Timer}：</p>
 *
 * <table border="1">
 *   <tr><th>指标名</th><th>Tags</th><th>说明</th></tr>
 *   <tr><td>{@code <prefix>.chain.execution}</td><td>operation, success</td><td>拦截器链整体耗时</td></tr>
 *   <tr><td>{@code <prefix>.field.before_save}</td><td>entity</td><td>字段引擎 beforeSave 耗时</td></tr>
 *   <tr><td>{@code <prefix>.field.after_query}</td><td>entity</td><td>字段引擎 afterQuery 耗时</td></tr>
 * </table>
 *
 * <p>默认指标前缀为 {@code jpa.plus}，可通过 {@code jpa-plus.metrics.prefix} 自定义。</p>
 *
 * @author guanxiangkai
 * @since 1.0.0
 */
class MicrometerJpaPlusMetrics implements JpaPlusMetrics {

    private final MeterRegistry registry;
    private final String prefix;

    /**
     * 缓存 Timer 实例，避免每次调用都查询 MeterRegistry 内部 map。
     * 键格式：{@code "C:{operation}:{success}"} / {@code "B:{entity}"} / {@code "A:{entity}"}
     */
    private final ConcurrentHashMap<String, Timer> timerCache = new ConcurrentHashMap<>();

    MicrometerJpaPlusMetrics(MeterRegistry registry, String prefix) {
        this.registry = registry;
        this.prefix = (prefix != null && !prefix.isBlank()) ? prefix.trim() : "jpa.plus";
    }

    @Override
    public void recordChainExecution(OperationType type, long nanos, boolean success) {
        String key = "C:" + type.name() + ":" + success;
        timerCache.computeIfAbsent(key, _ ->
                Timer.builder(prefix + ".chain.execution")
                        .description("JPA Plus interceptor chain execution time")
                        .tag("operation", type.name())
                        .tag("success", String.valueOf(success))
                        .register(registry)
        ).record(nanos, TimeUnit.NANOSECONDS);
    }

    @Override
    public void recordFieldEngineBefore(Class<?> entityClass, long nanos) {
        String key = "B:" + entityClass.getSimpleName();
        timerCache.computeIfAbsent(key, _ ->
                Timer.builder(prefix + ".field.before_save")
                        .description("JPA Plus field engine beforeSave processing time")
                        .tag("entity", entityClass.getSimpleName())
                        .register(registry)
        ).record(nanos, TimeUnit.NANOSECONDS);
    }

    @Override
    public void recordFieldEngineAfter(Class<?> entityClass, long nanos) {
        String key = "A:" + entityClass.getSimpleName();
        timerCache.computeIfAbsent(key, _ ->
                Timer.builder(prefix + ".field.after_query")
                        .description("JPA Plus field engine afterQuery processing time")
                        .tag("entity", entityClass.getSimpleName())
                        .register(registry)
        ).record(nanos, TimeUnit.NANOSECONDS);
    }
}


