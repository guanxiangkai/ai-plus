package io.github.guanxiangkai.jpa.plus.sharding;

import io.github.guanxiangkai.jpa.plus.sharding.autoconfigure.ShardingAutoConfiguration;
import io.github.guanxiangkai.jpa.plus.sharding.autoconfigure.ShardingProperties;
import io.github.guanxiangkai.jpa.plus.sharding.rule.ShardingRule;
import io.github.guanxiangkai.jpa.plus.sharding.spi.CrossShardQueryExecutor;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 分片配置与跨分片并发边界测试。
 *
 * @author guanxiangkai
 * @since 1.0.0
 */
@DisplayName("分片配置与跨分片并发边界测试")
class ShardingConfigurationBoundaryTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(ShardingAutoConfiguration.class))
            .withPropertyValues(
                    "jpa-plus.sharding.rules[0].logic-table-name=t_order",
                    "jpa-plus.sharding.rules[0].db-count=2",
                    "jpa-plus.sharding.rules[0].table-count=4",
                    "jpa-plus.sharding.rules[0].db-pattern=order_db_{index}",
                    "jpa-plus.sharding.rules[0].table-pattern=t_order_{index}");

    @Test
    @DisplayName("自动装配应绑定总分片与并发上限，并创建受限并行执行器")
    void bindsBoundedCrossShardExecutionProperties() {
        contextRunner
                .withPropertyValues(
                        "jpa-plus.sharding.max-total-shards=8",
                        "jpa-plus.sharding.cross-shard-query-max-concurrency=3")
                .run(context -> {
                    assertTrue(context.isRunning());
                    ShardingProperties properties = context.getBean(ShardingProperties.class);
                    assertEquals(8, properties.getMaxTotalShards());
                    assertEquals(3, properties.getCrossShardQueryMaxConcurrency());
                    CrossShardQueryExecutor.ParallelCrossShardQueryExecutor executor = assertInstanceOf(
                            CrossShardQueryExecutor.ParallelCrossShardQueryExecutor.class,
                            context.getBean(CrossShardQueryExecutor.class));
                    assertEquals(3, executor.maxConcurrency());
                });
    }

    @Test
    @DisplayName("单维度超过上限时配置绑定必须失败")
    void rejectsShardDimensionAboveMaximum() {
        contextRunner
                .withPropertyValues("jpa-plus.sharding.rules[0].db-count="
                        + (ShardingProperties.MAX_SHARD_DIMENSION + 1))
                .run(context -> assertNotNull(context.getStartupFailure()));
    }

    @Test
    @DisplayName("跨分片查询并发超过上限时配置绑定必须失败")
    void rejectsCrossShardQueryConcurrencyAboveMaximum() {
        contextRunner
                .withPropertyValues("jpa-plus.sharding.cross-shard-query-max-concurrency="
                        + (CrossShardQueryExecutor.ParallelCrossShardQueryExecutor.MAX_CONCURRENCY + 1))
                .run(context -> assertNotNull(context.getStartupFailure()));
    }

    @Test
    @DisplayName("物理分片总数超过配置上限时配置绑定必须失败")
    void rejectsRuleTotalShardsAboveConfiguredMaximum() {
        contextRunner
                .withPropertyValues(
                        "jpa-plus.sharding.max-total-shards=7",
                        "jpa-plus.sharding.rules[0].db-count=2",
                        "jpa-plus.sharding.rules[0].table-count=4")
                .run(context -> assertNotNull(context.getStartupFailure()));
    }

    @Test
    @DisplayName("乘积溢出不能绕过总分片上限")
    void rejectsOverflowedTotalShards() {
        ShardingProperties properties = new ShardingProperties();
        ShardingProperties.RuleConfig rule = new ShardingProperties.RuleConfig();
        rule.setDbCount(Integer.MAX_VALUE);
        rule.setTableCount(2);
        properties.setRules(List.of(rule));

        assertFalse(properties.isRuleTotalShardsWithinLimit());
        assertThrows(ArithmeticException.class, () -> new ShardingRule(
                "t_order", Integer.MAX_VALUE, 2,
                "order_db_{index}", "t_order_{index}", "userId"));
    }

    @Test
    @DisplayName("并行执行器任意时刻不超过配置的并发上限")
    void parallelExecutorCapsConcurrentShardTasks() throws Exception {
        int maxConcurrency = 3;
        int totalShards = 12;
        ShardingRule rule = new ShardingRule(
                "t_order", 1, totalShards,
                "order_db_{index}", "t_order_{index}", "userId");
        CrossShardQueryExecutor executor =
                new CrossShardQueryExecutor.ParallelCrossShardQueryExecutor(maxConcurrency);
        AtomicInteger active = new AtomicInteger();
        AtomicInteger peak = new AtomicInteger();
        AtomicInteger invocations = new AtomicInteger();

        List<String> result = executor.executeAll(rule, target -> {
            int current = active.incrementAndGet();
            peak.accumulateAndGet(current, Math::max);
            try {
                Thread.sleep(20);
                invocations.incrementAndGet();
                return List.of(target.table());
            } finally {
                active.decrementAndGet();
            }
        });

        assertEquals(totalShards, invocations.get());
        assertEquals(totalShards, result.size());
        assertTrue(peak.get() <= maxConcurrency, "并行任务峰值不应超过配置上限");
    }

    @Test
    @DisplayName("并行执行器拒绝非正并发上限")
    void rejectsNonPositiveParallelism() {
        assertThrows(IllegalArgumentException.class,
                () -> new CrossShardQueryExecutor.ParallelCrossShardQueryExecutor(0));
        assertThrows(IllegalArgumentException.class,
                () -> new CrossShardQueryExecutor.ParallelCrossShardQueryExecutor(
                        CrossShardQueryExecutor.ParallelCrossShardQueryExecutor.MAX_CONCURRENCY + 1));
    }
}
