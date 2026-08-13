package io.github.guanxiangkai.jpa.plus.core.model;

/**
 * 操作类型枚举
 *
 * <p>定义 JPA-Plus 框架支持的三种基本数据操作类型，
 * 拦截器通过 {@link #QUERY}、{@link #SAVE}、{@link #DELETE} 判断是否需要介入。</p>
 *
 * @author guanxiangkai
 * @since 1.0.0
 */
public enum OperationType {
    /**
     * 查询操作 —— 触发 Flush 策略、权限/租户条件注入、脱敏等
     */
    QUERY,
    /**
     * 保存操作（新增/更新）—— 触发字段加密、敏感词检测、版本自增等
     */
    SAVE,
    /**
     * 删除操作 —— 触发逻辑删除改写
     */
    DELETE
}
