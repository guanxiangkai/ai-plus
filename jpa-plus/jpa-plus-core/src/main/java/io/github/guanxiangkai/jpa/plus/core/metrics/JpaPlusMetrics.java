package io.github.guanxiangkai.jpa.plus.core.metrics;

import io.github.guanxiangkai.jpa.plus.core.model.OperationType;

/**
 * JPA Plus 可观测性指标接口
 *
 * <p>框架在以下三个关键路径上调用此接口采集耗时数据：</p>
 * <ol>
 *   <li>{@link #recordChainExecution} —— 整条拦截器链的执行耗时</li>
 *   <li>{@link #recordFieldEngineBefore} —— 字段引擎保存前处理耗时（加密、ID 生成、填充等）</li>
 *   <li>{@link #recordFieldEngineAfter} —— 字段引擎查询后处理耗时（解密、脱敏、字典等）</li>
 * </ol>
 *
 * <p>默认实现为 {@link #NOOP}（空操作），当 {@code micrometer-core} 在 classpath 时，
 * 自动装配 {@code MicrometerJpaPlusMetrics}，实现向 Micrometer {@code MeterRegistry} 上报指标。</p>
 *
 * <p><b>设计理念：</b>零 Micrometer 编译依赖 —— 此接口完全不引用 Micrometer 类，
 * 保证 {@code jpa-plus-core} 可在无 Micrometer 的环境中正常运行。</p>
 *
 * @author guanxiangkai
 * @since 1.0.0
 */
public interface JpaPlusMetrics {

    /**
     * 空操作单例（无 Micrometer 时的默认实现，零开销）
     */
    JpaPlusMetrics NOOP = new NoopJpaPlusMetrics();

    /**
     * 记录一次拦截器链执行的耗时
     *
     * @param type    操作类型（SAVE / QUERY / DELETE）
     * @param nanos   纳秒耗时
     * @param success 是否成功（抛出异常时为 {@code false}）
     */
    void recordChainExecution(OperationType type, long nanos, boolean success);

    /**
     * 记录字段引擎保存前处理（beforeSave）耗时
     *
     * @param entityClass 实体类
     * @param nanos       纳秒耗时
     */
    void recordFieldEngineBefore(Class<?> entityClass, long nanos);

    /**
     * 记录字段引擎查询后处理（afterQuery）耗时
     *
     * @param entityClass 实体类
     * @param nanos       纳秒耗时
     */
    void recordFieldEngineAfter(Class<?> entityClass, long nanos);
}

