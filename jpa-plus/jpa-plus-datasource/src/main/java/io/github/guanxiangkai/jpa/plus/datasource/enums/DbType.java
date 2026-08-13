package io.github.guanxiangkai.jpa.plus.datasource.enums;

/**
 * 数据库类型接口（可扩展枚举模式）
 *
 * <p>内置实现见 {@link DatabaseType}。
 * 用户可自定义 {@code enum} 实现此接口，支持任意数据库类型。</p>
 *
 * @author guanxiangkai
 * @since 1.0.0
 */
public interface DbType {

    /**
     * JDBC 驱动类全限定名
     */
    String driverClassName();

    /**
     * 连接校验 SQL（连接池 keepalive 使用）
     */
    String validationQuery();

    /**
     * 数据库类型名称（默认返回枚举名）
     */
    default String typeName() {
        return this instanceof Enum<?> e ? e.name() : getClass().getSimpleName();
    }
}

