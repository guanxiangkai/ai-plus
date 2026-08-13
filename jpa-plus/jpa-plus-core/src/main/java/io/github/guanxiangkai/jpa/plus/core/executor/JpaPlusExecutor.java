package io.github.guanxiangkai.jpa.plus.core.executor;

import io.github.guanxiangkai.jpa.plus.core.model.DataInvocation;

/**
 * JPA-Plus 统一执行器接口
 *
 * <p>所有数据操作（查询、保存、删除）的唯一入口。
 * 通过统一入口保证拦截器链、字段引擎等增强能力对所有操作一致生效。</p>
 *
 * <p><b>设计模式：</b>门面模式（Facade） —— 为复杂的拦截器链 + 字段引擎提供简单的统一入口</p>
 *
 * @author guanxiangkai
 * @see DefaultJpaPlusExecutor
 * @since 1.0.0
 */
public interface JpaPlusExecutor {

    /**
     * 执行数据操作
     *
     * @param invocation 数据调用
     * @return 执行结果
     * @throws Throwable 执行异常
     */
    Object execute(DataInvocation invocation) throws Throwable;
}
