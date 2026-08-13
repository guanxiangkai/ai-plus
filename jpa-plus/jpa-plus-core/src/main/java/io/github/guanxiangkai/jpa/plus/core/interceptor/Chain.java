package io.github.guanxiangkai.jpa.plus.core.interceptor;

import io.github.guanxiangkai.jpa.plus.core.model.DataInvocation;

/**
 * 拦截器链传递接口
 *
 * <p>每个拦截器在 {@link DataInterceptor#intercept} 中通过调用
 * {@code chain.proceed(invocation)} 将控制权传递给下一个拦截器。
 * 若不调用 {@code proceed}，则链中断（可用于权限拒绝等场景）。</p>
 *
 * <p><b>设计模式：</b>责任链模式（Chain of Responsibility）</p>
 *
 * @author guanxiangkai
 * @since 1.0.0
 */
@FunctionalInterface
public interface Chain {

    /**
     * 将调用传递给链中的下一个拦截器
     *
     * @param invocation 数据调用
     * @return 执行结果
     * @throws Throwable 执行异常
     */
    Object proceed(DataInvocation invocation) throws Throwable;
}
