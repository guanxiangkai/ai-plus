package io.github.guanxiangkai.jpa.plus.core.interceptor;

import io.github.guanxiangkai.jpa.plus.core.model.DataInvocation;
import io.github.guanxiangkai.jpa.plus.core.model.OperationType;
import lombok.extern.slf4j.Slf4j;

import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 拦截器链实现（预编译优化版）
 *
 * <p>在构造时按 {@link OperationType} 预编译拦截器链，避免运行时递归构建。核心优化：
 * <ul>
 *   <li><b>预编译</b>：启动时按操作类型预过滤拦截器，缓存为不可变列表</li>
 *   <li><b>EnumMap</b>：按操作类型数组寻址，热路径 O(1) 无哈希开销</li>
 *   <li><b>顺序遍历</b>：执行时线性遍历，无递归开销</li>
 * </ul>
 * </p>
 *
 * <p><b>设计模式：</b>
 * <ul>
 *   <li>责任链模式（Chain of Responsibility） —— 拦截器串联执行</li>
 *   <li>编译器模式（Compiler） —— 启动时编译为优化的执行结构</li>
 *   <li>策略模式（Strategy） —— 每种操作类型使用独立的预编译链</li>
 * </ul>
 * </p>
 *
 * @author guanxiangkai
 * @since 1.0.0
 */
@Slf4j
public class InterceptorChain {

    /**
     * 预编译的拦截器链缓存（按操作类型索引，EnumMap 数组寻址 O(1)）
     * <p>key: OperationType, value: 该类型预编译的拦截器链</p>
     */
    private final EnumMap<OperationType, CompiledChain> compiledChains;

    /**
     * 构造拦截器链并预编译
     *
     * @param interceptors 所有拦截器实例（由 SPI 加载）
     */
    public InterceptorChain(List<DataInterceptor> interceptors) {
        // P1-21: Guard against null before any dereference to give a meaningful error message.
        Objects.requireNonNull(interceptors, "interceptors list must not be null");
        log.debug("[jpa-plus] 开始预编译拦截器链，总拦截器数: {}", interceptors.size());

        // 按 order 全局排序（确保拦截器按优先级执行）
        List<DataInterceptor> sortedInterceptors = interceptors.stream()
                .sorted(Comparator.comparingInt(DataInterceptor::order))
                .toList();

        // 为每种操作类型预编译独立的拦截器链（EnumMap：数组索引，热路径 O(1)）
        EnumMap<OperationType, CompiledChain> map = new EnumMap<>(OperationType.class);
        for (OperationType type : OperationType.values()) {
            map.put(type, compileChain(sortedInterceptors, type));
        }
        this.compiledChains = map;

        log.info("[jpa-plus] 拦截器链预编译完成: {}",
                compiledChains.entrySet().stream()
                        .map(e -> String.format("%s[before=%d,after=%d]",
                                e.getKey(),
                                e.getValue().beforeInterceptors().size(),
                                e.getValue().afterInterceptors().size()))
                        .collect(Collectors.joining(", ")));
    }

    /**
     * 执行拦截器链（使用预编译链，零运行时开销）
     *
     * @param invocation    数据调用上下文
     * @param coreExecution 核心执行逻辑（不含拦截器的实际数据操作）
     * @return 执行结果
     * @throws Throwable 执行异常
     */
    public Object proceed(DataInvocation invocation, CoreExecution coreExecution) throws Throwable {
        // 直接使用预编译的拦截器链（无递归，无动态构建）
        CompiledChain chain = compiledChains.get(invocation.type());
        return chain.execute(invocation, coreExecution);
    }

    /**
     * 预编译指定操作类型的拦截器链
     *
     * @param allInterceptors 所有拦截器（已排序）
     * @param operationType   操作类型
     * @return 预编译的拦截器链
     */
    private CompiledChain compileChain(List<DataInterceptor> allInterceptors, OperationType operationType) {
        // 过滤出支持该操作类型的拦截器
        List<DataInterceptor> beforeList = allInterceptors.stream()
                .filter(i -> i.phase() == Phase.BEFORE && i.supports(operationType))
                .toList();

        List<DataInterceptor> afterList = allInterceptors.stream()
                .filter(i -> i.phase() == Phase.AFTER && i.supports(operationType))
                .toList();

        // 转为列表（List 不可变，比数组更安全）
        return new CompiledChain(beforeList, afterList);
    }

    /**
     * 核心执行逻辑接口（不含拦截器的实际数据操作）
     */
    @FunctionalInterface
    public interface CoreExecution {
        Object execute(DataInvocation invocation) throws Throwable;
    }

    /**
     * 预编译的拦截器链（不可变，线程安全）
     *
     * <p>使用 record 保证引用不可变；compact constructor 通过 {@link List#copyOf}
     * 防止外部修改底层列表。</p>
     *
     * @param beforeInterceptors BEFORE 阶段拦截器列表（已过滤、已排序）
     * @param afterInterceptors  AFTER 阶段拦截器列表（已过滤、已排序）
     */
    private record CompiledChain(List<DataInterceptor> beforeInterceptors,
                                 List<DataInterceptor> afterInterceptors) {

        CompiledChain {
            beforeInterceptors = List.copyOf(beforeInterceptors);
            afterInterceptors = List.copyOf(afterInterceptors);
        }

        /**
         * 执行预编译链
         *
         * @param invocation    数据调用上下文
         * @param coreExecution 核心执行逻辑
         * @return 执行结果
         * @throws Throwable 执行异常
         */
        Object execute(DataInvocation invocation, CoreExecution coreExecution) throws Throwable {
            return proceedBefore(0, invocation, coreExecution);
        }

        private Object proceedBefore(int index, DataInvocation invocation, CoreExecution coreExecution) throws Throwable {
            if (index >= beforeInterceptors.size()) {
                return executeCoreAndAfter(invocation, coreExecution);
            }

            DataInterceptor interceptor = beforeInterceptors.get(index);
            return interceptor.intercept(invocation, nextInvocation -> proceedBefore(index + 1, nextInvocation, coreExecution));
        }

        private Object executeCoreAndAfter(DataInvocation invocation, CoreExecution coreExecution) throws Throwable {
            // ── 核心执行：执行实际的数据库操作 ──
            Object result = coreExecution.execute(invocation);

            // ── AFTER 阶段：顺序执行所有后置拦截器 ──
            Object currentResult = result;
            for (DataInterceptor interceptor : afterInterceptors) {
                Object finalResult = currentResult;
                currentResult = interceptor.intercept(
                        invocation,
                        inv -> finalResult
                );
            }

            return currentResult;
        }
    }
}
