package io.github.guanxiangkai.jpa.plus.field.id.enums;

/**
 * ID 生成策略枚举
 *
 * <p>定义框架支持的主键生成方式，可在 {@code @AutoId} 注解中指定，
 * 也可通过全局配置 {@code jpa-plus.id-generator.type} 统一设置。</p>
 *
 * <h3>策略说明</h3>
 * <ul>
 *   <li>{@link #AUTO} —— 跟随全局配置（注解未显式指定时的默认值）</li>
 *   <li>{@link #SNOWFLAKE} —— 雪花算法（64 位分布式唯一 ID，推荐）</li>
 *   <li>{@link #UUID} —— UUID v4（32 位十六进制字符串）</li>
 *   <li>{@link #CUSTOM} —— 自定义策略（用户实现 {@code IdGenerator} 接口）</li>
 * </ul>
 *
 * @author guanxiangkai
 * @since 1.0.0
 */
public enum IdType {

    /**
     * 跟随全局配置（默认值）
     *
     * <p>当 {@code @AutoId} 注解未显式指定 type 时使用此值，
     * 框架会读取 {@code jpa-plus.id-generator.type} 配置项决定实际策略。
     * 如果全局配置也为 AUTO，则默认使用 {@link #SNOWFLAKE}。</p>
     */
    AUTO,

    /**
     * 雪花算法（Snowflake）
     *
     * <p>生成 64 位 Long 型分布式唯一 ID，结构：
     * {@code 1位符号 + 41位时间戳 + 5位数据中心 + 5位工作节点 + 12位序列号}。
     * 天然趋势递增，适合作为数据库主键（B+ 树友好）。</p>
     *
     * <p>配置项：</p>
     * <pre>{@code
     * jpa-plus.id-generator.snowflake.worker-id: 1
     * jpa-plus.id-generator.snowflake.datacenter-id: 1
     * jpa-plus.id-generator.snowflake.epoch: 1700000000000
     * }</pre>
     */
    SNOWFLAKE,

    /**
     * UUID（v4 随机）
     *
     * <p>生成 32 位无连字符的十六进制字符串（如 {@code 550e8400e29b41d4a716446655440000}）。
     * 全局唯一，无需中心化协调，但不适合作为聚簇索引主键（随机分布导致页分裂）。</p>
     *
     * <p>适用场景：对外暴露的业务 ID、文件名、Token 等。</p>
     */
    UUID,

    /**
     * 自定义策略
     *
     * <p>由用户实现 {@code IdGenerator} SPI 接口，注册为 Spring Bean 后自动生效。</p>
     *
     * <pre>{@code
     * @Component
     * public class MyIdGenerator implements IdGenerator {
     *     @Override
     *     public Object generate(Field field) {
     *         return myCustomLogic();
     *     }
     * }
     * }</pre>
     */
    CUSTOM
}

