package io.github.guanxiangkai.jpa.plus.sharding.annotation;

import java.lang.annotation.*;

/**
 * 分片键注解
 *
 * <p>标注在实体字段上，声明该字段为分库分表路由的分片键。
 * {@link io.github.guanxiangkai.jpa.plus.sharding.spi.ShardingKeyExtractor} 会优先解析此注解；
 * 若未标注，则回退到 {@code ShardingRule} 中配置的 {@code shardingKeyField} 字段名。</p>
 *
 * <h3>使用示例</h3>
 * <pre>{@code
 * @Entity
 * public class Order {
 *
 *     @Sharding
 *     private Long userId;   // 按用户 ID 分片
 *
 *     private Long orderId;
 * }
 * }</pre>
 *
 * <h3>设计原则</h3>
 * <ul>
 *   <li>一个实体只应标注一个 {@code @Sharding} 字段；多个字段均标注时取第一个</li>
 *   <li>分片键字段类型应为 {@code Long}、{@code String} 或其包装类</li>
 *   <li>分片键值为 {@code null} 时，{@link io.github.guanxiangkai.jpa.plus.sharding.router.ShardingRouter}
 *       会抛出 {@link IllegalArgumentException}</li>
 * </ul>
 *
 * @author guanxiangkai
 * @since 1.0.0
 */
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Sharding {
}

