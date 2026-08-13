package io.github.guanxiangkai.jpa.plus.interceptor.logicdelete.enums;

/**
 * 逻辑删除值策略接口（可扩展枚举模式）
 *
 * <p>内置实现见 {@link LogicDeleteType}。
 * 用户可自定义枚举实现此接口，支持任意删除标识方案。</p>
 *
 * <h3>扩展示例</h3>
 * <pre>{@code
 * public enum MyDeleteType implements LogicDeleteValue {
 *     // 用时间戳标记删除（null=未删除，非null=删除时间）
 *     TIMESTAMP {
 *         @Override public Object deletedValue()    { return System.currentTimeMillis(); }
 *         @Override public Object notDeletedValue()  { return null; }
 *         @Override public Class<?> javaType()       { return Long.class; }
 *     };
 * }
 * }</pre>
 *
 * @author guanxiangkai
 * @since 1.0.0
 */
public interface LogicDeleteValue {

    /**
     * 已删除值（DELETE 时写入）
     */
    Object deletedValue();

    /**
     * 未删除值（查询条件 / 保存时默认值）
     */
    Object notDeletedValue();

    /**
     * 值的 Java 类型（用于 AST 条件构建时的类型信息）
     */
    Class<?> javaType();
}

