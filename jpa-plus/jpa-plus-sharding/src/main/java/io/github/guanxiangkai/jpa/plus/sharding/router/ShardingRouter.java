package io.github.guanxiangkai.jpa.plus.sharding.router;

import io.github.guanxiangkai.jpa.plus.sharding.model.ShardingTarget;

/**
 * 分片路由门面接口
 *
 * <p>整合 {@link io.github.guanxiangkai.jpa.plus.sharding.rule.ShardingRuleRegistry}、
 * {@link io.github.guanxiangkai.jpa.plus.sharding.spi.ShardingKeyExtractor} 和
 * {@link io.github.guanxiangkai.jpa.plus.sharding.spi.ShardingAlgorithm}，
 * 对外提供统一的路由入口。</p>
 *
 * <h3>使用示例</h3>
 * <pre>{@code
 * // 在业务代码中直接使用（通常由 ShardingInterceptor 自动调用，无需手动路由）
 * ShardingTarget target = shardingRouter.route("order", orderEntity);
 * // target.db()    → 目标数据源名称，如 "order_db_2"
 * // target.table() → 物理表名，如 "order_3"
 * }</pre>
 *
 * <p><b>设计模式：</b>门面模式（Facade）</p>
 *
 * @author guanxiangkai
 * @since 1.0.0
 */
public interface ShardingRouter {

    /**
     * 根据逻辑表名和实体对象计算分片路由目标
     *
     * @param logicTableName 逻辑表名
     * @param entity         实体对象（用于提取分片键）
     * @return 分片路由结果
     * @throws IllegalArgumentException 若逻辑表未注册分片规则，或分片键值为 {@code null}
     */
    ShardingTarget route(String logicTableName, Object entity);

    /**
     * 根据逻辑表名和显式分片键值计算分片路由目标（无实体场景）
     *
     * @param logicTableName 逻辑表名
     * @param shardingKey    分片键值
     * @return 分片路由结果
     * @throws IllegalArgumentException 若逻辑表未注册分片规则
     */
    ShardingTarget routeByKey(String logicTableName, Object shardingKey);

    /**
     * 判断指定逻辑表是否已注册分片规则
     */
    boolean isSharded(String logicTableName);
}

