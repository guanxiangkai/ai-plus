package io.github.guanxiangkai.jpa.plus.sharding.rule;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 分片规则注册表
 *
 * <p>管理逻辑表名与 {@link ShardingRule} 的映射关系，支持运行时动态注册。</p>
 *
 * <p>通常在应用启动时由 {@link io.github.guanxiangkai.jpa.plus.sharding.autoconfigure.ShardingAutoConfiguration}
 * 读取配置并批量注册；业务代码也可通过 Spring 注入后在运行时调用 {@link #register} 追加规则。</p>
 *
 * <h3>线程安全</h3>
 * <p>内部使用 {@link ConcurrentHashMap}，{@link #register} 和 {@link #find} 均为线程安全。</p>
 *
 * @author guanxiangkai
 * @since 1.0.0
 */
public class ShardingRuleRegistry {

    private final Map<String, ShardingRule> rules = new ConcurrentHashMap<>();

    /**
     * 注册一条分片规则
     *
     * @param rule 分片规则，{@code rule.logicTableName()} 作为 key
     * @throws IllegalArgumentException 若规则为 {@code null}
     */
    public void register(ShardingRule rule) {
        if (rule == null) throw new IllegalArgumentException("ShardingRule must not be null");
        rules.put(rule.logicTableName(), rule);
    }

    /**
     * 批量注册分片规则
     *
     * @param ruleList 规则列表
     */
    public void registerAll(Collection<ShardingRule> ruleList) {
        if (ruleList != null) {
            ruleList.forEach(this::register);
        }
    }

    /**
     * 按逻辑表名查找分片规则
     *
     * @param logicTableName 逻辑表名
     * @return 对应的分片规则，未找到则返回 {@code null}
     */
    public ShardingRule find(String logicTableName) {
        return rules.get(logicTableName);
    }

    /**
     * 判断指定逻辑表是否已注册分片规则
     */
    public boolean contains(String logicTableName) {
        return rules.containsKey(logicTableName);
    }

    /**
     * 获取全部已注册规则的只读视图
     */
    public Map<String, ShardingRule> all() {
        return Collections.unmodifiableMap(rules);
    }

    /**
     * 注销指定逻辑表的分片规则（用于动态刷新场景）
     */
    public void unregister(String logicTableName) {
        rules.remove(logicTableName);
    }
}

