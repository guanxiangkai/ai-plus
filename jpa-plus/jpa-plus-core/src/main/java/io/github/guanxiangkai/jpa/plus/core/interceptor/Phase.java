package io.github.guanxiangkai.jpa.plus.core.interceptor;

/**
 * 拦截器执行阶段枚举
 *
 * <p>决定拦截器在核心执行逻辑的前后执行位置。</p>
 *
 * @author guanxiangkai
 * @since 1.0.0
 */
public enum Phase {
    /**
     * 前置拦截 —— 在核心执行之前（权限注入、条件改写等）
     */
    BEFORE,
    /**
     * 后置拦截 —— 在核心执行之后（审计日志、脱敏处理等）
     */
    AFTER
}
