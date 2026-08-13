package io.github.guanxiangkai.jpa.plus.core.interceptor;

import io.github.guanxiangkai.jpa.plus.core.model.DataInvocation;
import io.github.guanxiangkai.jpa.plus.core.model.OperationType;
import io.github.guanxiangkai.jpa.plus.core.spi.Ordered;

/**
 * 数据拦截器接口
 *
 * <p>所有拦截器必须实现此接口。框架通过以下属性控制拦截器行为：
 * <ul>
 *   <li>{@link #order()} —— 执行优先级，值越小越先执行</li>
 *   <li>{@link #phase()} —— 执行阶段，{@link Phase#BEFORE} 在核心逻辑前，{@link Phase#AFTER} 在核心逻辑后</li>
 *   <li>{@link #supports(OperationType)} —— 操作类型过滤，仅对匹配的操作生效</li>
 * </ul>
 * </p>
 *
 * <p><b>设计模式：</b>责任链模式（Chain of Responsibility） + 策略模式（Strategy）</p>
 *
 * @author guanxiangkai
 * @see InterceptorChain
 * @see Phase
 * @since 1.0.0
 */
public interface DataInterceptor extends Ordered {

    /**
     * 拦截器阶段（BEFORE / AFTER）
     */
    Phase phase();

    /**
     * 是否支持指定操作类型
     */
    boolean supports(OperationType type);

    /**
     * 执行拦截逻辑
     *
     * @param invocation 数据调用
     * @param chain      拦截器链
     * @return 执行结果
     * @throws Throwable 执行异常
     */
    Object intercept(DataInvocation invocation, Chain chain) throws Throwable;
}
