package io.github.guanxiangkai.jpa.plus.query.context;

/**
 * 查询上下文 —— 查询的完整描述（不可变值对象）
 *
 * <p>由两部分组成：
 * <ul>
 *   <li>{@link QueryMetadata} —— 不可变元数据（表结构、Join 图、SELECT 列、赋值列表）</li>
 *   <li>{@link QueryRuntime} —— 运行时状态（WHERE 条件、排序、分页），拦截器可通过 {@link #withRuntime} 替换</li>
 * </ul>
 * </p>
 *
 * <p><b>设计模式：</b>不可变值对象模式 —— record 保证线程安全</p>
 *
 * @param metadata 查询元数据（不可变）
 * @param runtime  查询运行时（可通过 wither 方法替换）
 * @author guanxiangkai
 * @since 1.0.0
 */
public record QueryContext(
        QueryMetadata metadata,
        QueryRuntime runtime
) {

    /**
     * 替换运行时生成新的上下文
     */
    public QueryContext withRuntime(QueryRuntime newRuntime) {
        return new QueryContext(metadata, newRuntime);
    }

    /**
     * 获取查询类型
     */
    public QueryType type() {
        return metadata.type();
    }
}
