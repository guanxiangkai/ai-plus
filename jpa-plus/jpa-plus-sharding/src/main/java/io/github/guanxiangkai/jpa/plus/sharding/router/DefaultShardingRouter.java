package io.github.guanxiangkai.jpa.plus.sharding.router;

import io.github.guanxiangkai.jpa.plus.sharding.model.ShardingTarget;
import io.github.guanxiangkai.jpa.plus.sharding.rule.ShardingRule;
import io.github.guanxiangkai.jpa.plus.sharding.rule.ShardingRuleRegistry;
import io.github.guanxiangkai.jpa.plus.sharding.spi.ShardingAlgorithm;
import io.github.guanxiangkai.jpa.plus.sharding.spi.ShardingKeyExtractor;

/**
 * 分片路由默认实现
 *
 * <p>按以下顺序执行路由：</p>
 * <ol>
 *   <li>从 {@link ShardingRuleRegistry} 查找逻辑表对应的 {@link ShardingRule}</li>
 *   <li>调用 {@link ShardingKeyExtractor} 从实体中提取分片键值</li>
 *   <li>调用 {@link ShardingAlgorithm} 根据分片键和规则计算目标库表</li>
 *   <li>返回 {@link ShardingTarget}</li>
 * </ol>
 *
 * <h3>跨分片写入防护</h3>
 * <p>本实现遵循 {@code cross-shard-policy = REJECT} 原则：
 * 一次请求只允许路由到单个分片，若需要跨分片操作，请在应用层拆分请求或引入 Seata。</p>
 *
 * @author guanxiangkai
 * @since 1.0.0
 */
public class DefaultShardingRouter implements ShardingRouter {

    private final ShardingRuleRegistry registry;
    private final ShardingKeyExtractor keyExtractor;
    private final ShardingAlgorithm algorithm;

    public DefaultShardingRouter(ShardingRuleRegistry registry,
                                 ShardingKeyExtractor keyExtractor,
                                 ShardingAlgorithm algorithm) {
        this.registry = registry;
        this.keyExtractor = keyExtractor;
        this.algorithm = algorithm;
    }

    @Override
    public ShardingTarget route(String logicTableName, Object entity) {
        ShardingRule rule = requireRule(logicTableName);
        Object shardingKey = keyExtractor.extract(entity, rule);
        if (shardingKey == null) {
            throw new IllegalArgumentException(
                    "Sharding key is null for entity [" + entity.getClass().getName() +
                            "] on logic table [" + logicTableName + "]. " +
                            "Ensure the @Sharding field is set before saving.");
        }
        return algorithm.route(logicTableName, shardingKey, rule);
    }

    @Override
    public ShardingTarget routeByKey(String logicTableName, Object shardingKey) {
        if (shardingKey == null) {
            throw new IllegalArgumentException("shardingKey must not be null for logic table [" + logicTableName + "]");
        }
        ShardingRule rule = requireRule(logicTableName);
        return algorithm.route(logicTableName, shardingKey, rule);
    }

    @Override
    public boolean isSharded(String logicTableName) {
        return registry.contains(logicTableName);
    }

    private ShardingRule requireRule(String logicTableName) {
        ShardingRule rule = registry.find(logicTableName);
        if (rule == null) {
            throw new IllegalArgumentException(
                    "No ShardingRule registered for logic table [" + logicTableName + "]. " +
                            "Register a rule via ShardingRuleRegistry.register() or configure it in application.yml.");
        }
        return rule;
    }
}

