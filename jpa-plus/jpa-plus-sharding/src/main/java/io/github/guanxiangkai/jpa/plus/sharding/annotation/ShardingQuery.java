package io.github.guanxiangkai.jpa.plus.sharding.annotation;

import java.lang.annotation.*;

/**
 * 分片查询注解
 *
 * <p>标注在 Repository 方法上，声明该查询方法需要通过分片路由到指定数据源。
 * 框架将在执行前解析分片键表达式，计算目标库表，并设置 {@code JpaPlusContext.withDS()} 数据源上下文。</p>
 *
 * <h3>使用示例</h3>
 * <pre>{@code
 * public interface OrderRepository extends JpaPlusRepository<Order, Long> {
 *
 *     // 按 userId 路由查询：userId 来自方法参数
 *     @ShardingQuery(logicTable = "t_order", keyExpression = "#userId")
 *     List<Order> findByUserId(@Param("userId") Long userId);
 *
 *     // 按 orderId 路由：Spring Data 会将参数绑定到 SpEL 上下文
 *     @ShardingQuery(logicTable = "t_order", keyExpression = "#orderId")
 *     Optional<Order> findByOrderId(@Param("orderId") Long orderId);
 * }
 * }</pre>
 *
 * <h3>路由原理</h3>
 * <ol>
 *   <li>解析 {@code keyExpression} SpEL 表达式，从方法参数中取出分片键值</li>
 *   <li>调用 {@link io.github.guanxiangkai.jpa.plus.sharding.router.ShardingRouter#routeByKey(String, Object)} 计算目标库</li>
 *   <li>在目标数据源上下文中执行原始查询</li>
 * </ol>
 *
 * @author guanxiangkai
 * @since 1.0.0
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface ShardingQuery {

    /**
     * 逻辑表名（与 {@code @Table(name=...)} 或 {@code ShardingRule.logicTableName} 一致）
     */
    String logicTable();

    /**
     * 分片键表达式（SpEL），从方法参数中取值
     *
     * <p>参数通过 {@code @Param("name")} 注解绑定到 SpEL 上下文，
     * 例如方法参数 {@code @Param("userId") Long userId}，表达式写 {@code "#userId"}。</p>
     */
    String keyExpression();
}

