package io.github.guanxiangkai.jpa.plus.sharding.autoconfigure;

import io.github.guanxiangkai.jpa.plus.sharding.spi.CrossShardQueryExecutor;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.util.ArrayList;
import java.util.List;

/**
 * 分库分表配置属性
 *
 * <p>配置前缀：{@code jpa-plus.sharding}</p>
 *
 * <h3>配置示例</h3>
 * <pre>{@code
 * jpa-plus:
 *   sharding:
 *     enabled: true
 *     cross-shard-policy: REJECT   # REJECT（默认）/ BEST_EFFORT / SEATA
 *     rules:
 *       - logic-table-name: order
 *         db-count: 4
 *         table-count: 8
 *         db-pattern: "order_db_{index}"
 *         table-pattern: "order_{index}"
 *         sharding-key-field: userId   # 可选，优先使用分片键注解字段
 *       - logic-table-name: user
 *         db-count: 2
 *         table-count: 4
 *         db-pattern: "user_db_{index}"
 *         table-pattern: "user_{index}"
 * }</pre>
 *
 * @author guanxiangkai
 * @since 1.0.0
 */
@Validated
@ConfigurationProperties(prefix = "jpa-plus.sharding")
public class ShardingProperties {

    /**
     * 单个分库或分表维度的最大值。
     *
     * <p>限制单维度可以阻断明显错误的配置，同时把跨分片查询的物理目标数量
     * 控制在可观测、可运维的范围内。</p>
     */
    public static final int MAX_SHARD_DIMENSION = 256;

    /**
     * 单条规则默认允许的物理分片总数。
     */
    public static final int DEFAULT_MAX_TOTAL_SHARDS = 256;

    /**
     * 单条规则允许配置的物理分片总数上限。
     */
    public static final int MAX_TOTAL_SHARDS = 4_096;

    /**
     * 是否启用分库分表模块（默认 true，有 ShardingRule 配置时才真正生效）
     */
    private boolean enabled = true;

    /**
     * 跨分片写入策略
     *
     * <ul>
     *   <li>{@code REJECT}（默认）—— 禁止跨分片写入，单次操作只能路由到一个分片</li>
     *   <li>{@code BEST_EFFORT} —— 尽力提交，不保证原子性（适合可接受最终一致的场景）</li>
     *   <li>{@code SEATA} —— 交由 Seata 管理分布式事务（需引入 seata-spring-boot-starter）</li>
     * </ul>
     */
    private CrossShardPolicy crossShardPolicy = CrossShardPolicy.REJECT;

    /**
     * 单条规则允许的物理分片总数上限。
     *
     * <p>该上限作用于 {@code dbCount × tableCount}，用于避免错误配置将散射查询、
     * 规则注册和运维目标数量放大到不可控范围。</p>
     */
    @Min(value = 1, message = "maxTotalShards 必须 >= 1")
    @Max(value = MAX_TOTAL_SHARDS, message = "maxTotalShards 不能超过 " + MAX_TOTAL_SHARDS)
    private int maxTotalShards = DEFAULT_MAX_TOTAL_SHARDS;

    /**
     * 并行跨分片查询的最大并发数。
     *
     * <p>执行器按批次提交任务，任意时刻不会超过该值，避免按物理分片总数无界创建任务。</p>
     */
    @Min(value = 1, message = "crossShardQueryMaxConcurrency 必须 >= 1")
    @Max(value = CrossShardQueryExecutor.ParallelCrossShardQueryExecutor.MAX_CONCURRENCY,
            message = "crossShardQueryMaxConcurrency 不能超过 "
                    + CrossShardQueryExecutor.ParallelCrossShardQueryExecutor.MAX_CONCURRENCY)
    private int crossShardQueryMaxConcurrency = CrossShardQueryExecutor.ParallelCrossShardQueryExecutor.DEFAULT_MAX_CONCURRENCY;

    /**
     * 分片规则列表（每条规则对应一张逻辑表）
     */
    @Valid
    private List<@NotNull(message = "rules 不能包含空规则") RuleConfig> rules = new ArrayList<>();

    // ─── Getters / Setters ───

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public CrossShardPolicy getCrossShardPolicy() {
        return crossShardPolicy;
    }

    public void setCrossShardPolicy(CrossShardPolicy crossShardPolicy) {
        this.crossShardPolicy = crossShardPolicy;
    }

    public int getMaxTotalShards() {
        return maxTotalShards;
    }

    public void setMaxTotalShards(int maxTotalShards) {
        this.maxTotalShards = maxTotalShards;
    }

    public int getCrossShardQueryMaxConcurrency() {
        return crossShardQueryMaxConcurrency;
    }

    public void setCrossShardQueryMaxConcurrency(int crossShardQueryMaxConcurrency) {
        this.crossShardQueryMaxConcurrency = crossShardQueryMaxConcurrency;
    }

    public List<RuleConfig> getRules() {
        return rules;
    }

    public void setRules(List<RuleConfig> rules) {
        this.rules = rules;
    }

    /**
     * 校验每条规则的物理分片总数。
     *
     * <p>必须使用 {@link Math#multiplyExact(int, int)}，不能让整数溢出绕过上限校验。</p>
     *
     * @return 全部规则均未溢出且不超过 {@link #maxTotalShards} 时返回 {@code true}
     */
    @AssertTrue(message = "每条规则的 dbCount × tableCount 必须不超过 maxTotalShards，且不能发生整数溢出")
    public boolean isRuleTotalShardsWithinLimit() {
        if (rules == null) {
            return true;
        }
        return rules.stream().allMatch(rule -> rule != null && rule.isTotalShardsWithin(maxTotalShards));
    }

    // ─── 跨分片策略枚举 ───

    public enum CrossShardPolicy {
        /**
         * 拒绝跨分片写入（默认，最安全）
         */
        REJECT,
        /**
         * 尽力提交，无分布式事务保证
         */
        BEST_EFFORT,
        /**
         * 由 Seata 管理分布式事务
         */
        SEATA
    }

    // ─── 单条规则配置 ───

    public static class RuleConfig {

        /**
         * 逻辑表名（与实体表名配置一致）
         */
        @NotBlank(message = "logicTableName 不能为空")
        private String logicTableName;

        /**
         * 分库数量（默认 1，不分库）
         */
        @Min(value = 1, message = "dbCount 必须 >= 1")
        @Max(value = MAX_SHARD_DIMENSION, message = "dbCount 不能超过 " + MAX_SHARD_DIMENSION)
        private int dbCount = 1;

        /**
         * 每库表数量（默认 1，不分表）
         */
        @Min(value = 1, message = "tableCount 必须 >= 1")
        @Max(value = MAX_SHARD_DIMENSION, message = "tableCount 不能超过 " + MAX_SHARD_DIMENSION)
        private int tableCount = 1;

        /**
         * 数据源命名模式，{index} 替换为库序号（0-based）
         */
        @NotBlank(message = "dbPattern 不能为空")
        @Pattern(regexp = ".*\\{index\\}.*", message = "dbPattern 必须包含 {index} 占位符")
        private String dbPattern;

        /**
         * 物理表名命名模式，{index} 替换为表序号（0-based）
         */
        @NotBlank(message = "tablePattern 不能为空")
        @Pattern(regexp = ".*\\{index\\}.*", message = "tablePattern 必须包含 {index} 占位符")
        private String tablePattern;

        /**
         * 分片键字段名（可选，优先使用分片键注解字段）
         */
        private String shardingKeyField;

        // ─── Getters / Setters ───

        public String getLogicTableName() {
            return logicTableName;
        }

        public void setLogicTableName(String logicTableName) {
            this.logicTableName = logicTableName;
        }

        public int getDbCount() {
            return dbCount;
        }

        public void setDbCount(int dbCount) {
            this.dbCount = dbCount;
        }

        public int getTableCount() {
            return tableCount;
        }

        public void setTableCount(int tableCount) {
            this.tableCount = tableCount;
        }

        public String getDbPattern() {
            return dbPattern;
        }

        public void setDbPattern(String dbPattern) {
            this.dbPattern = dbPattern;
        }

        public String getTablePattern() {
            return tablePattern;
        }

        public void setTablePattern(String tablePattern) {
            this.tablePattern = tablePattern;
        }

        public String getShardingKeyField() {
            return shardingKeyField;
        }

        public void setShardingKeyField(String shardingKeyField) {
            this.shardingKeyField = shardingKeyField;
        }

        /**
         * 判断物理分片总数是否在给定上限内。
         *
         * @param maxTotalShards 允许的物理分片总数上限
         * @return 未溢出且不超过上限时返回 {@code true}
         */
        boolean isTotalShardsWithin(int maxTotalShards) {
            try {
                return Math.multiplyExact(dbCount, tableCount) <= maxTotalShards;
            } catch (ArithmeticException ignored) {
                return false;
            }
        }
    }
}
