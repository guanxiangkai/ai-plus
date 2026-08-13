package io.github.guanxiangkai.jpa.plus.sharding;

import io.github.guanxiangkai.jpa.plus.datasource.context.JpaPlusContext;
import io.github.guanxiangkai.jpa.plus.sharding.annotation.ShardingQuery;
import io.github.guanxiangkai.jpa.plus.sharding.aop.ShardingQueryAspect;
import io.github.guanxiangkai.jpa.plus.sharding.model.ShardingContext;
import io.github.guanxiangkai.jpa.plus.sharding.model.ShardingTarget;
import io.github.guanxiangkai.jpa.plus.sharding.router.DefaultShardingRouter;
import io.github.guanxiangkai.jpa.plus.sharding.router.ShardingRouter;
import io.github.guanxiangkai.jpa.plus.sharding.rule.ShardingRule;
import io.github.guanxiangkai.jpa.plus.sharding.rule.ShardingRuleRegistry;
import io.github.guanxiangkai.jpa.plus.sharding.spi.CrossShardQueryExecutor;
import io.github.guanxiangkai.jpa.plus.sharding.spi.ShardingAlgorithm;
import io.github.guanxiangkai.jpa.plus.sharding.spi.ShardingKeyExtractor;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.stereotype.Component;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link ShardingQueryAspect} + Spring AOP 全链路集成测试
 *
 * <p>使用 {@code @ContextConfiguration} 启动最小化 Spring 上下文，验证：
 * <ul>
 *   <li>AOP 切面能正确拦截 {@code @ShardingQuery} 方法</li>
 *   <li>SpEL 表达式从参数中提取分片键</li>
 *   <li>{@link ShardingContext} 在方法执行期间正确设置</li>
 *   <li>{@link JpaPlusContext#currentDS()} 返回正确的目标数据库名</li>
 *   <li>返回类型适配（{@code Optional}、{@code List}）</li>
 *   <li>{@link CrossShardQueryExecutor} 流式 API 行为正确</li>
 * </ul>
 * </p>
 *
 * @author guanxiangkai
 * @since 1.0.0
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = ShardingQueryAspectIntegrationTest.TestConfig.class)
@DisplayName("@ShardingQuery AOP 全链路集成测试")
class ShardingQueryAspectIntegrationTest {

    // ─── 测试规则：2 库 × 4 表 ──────────────────────────────────────────────

    static final ShardingRule ORDER_RULE = new ShardingRule(
            "t_order", 2, 4,
            "order_db_{index}", "t_order_{index}", "userId");

    // ─── Spring 测试配置 ─────────────────────────────────────────────────────
    @Autowired
    OrderQueryService orderQueryService;

    // ─── 测试目标 Bean ───────────────────────────────────────────────────────
    @Autowired
    ShardingRouter router;

    // ─── 注入被测 Bean ───────────────────────────────────────────────────────

    @Test
    @DisplayName("AOP 拦截：queryOrders 被 ShardingQueryAspect 代理，ShardingContext 正确设置")
    void testAopInterceptsAndSetsContext() {
        long userId = 42L;
        List<String> result = orderQueryService.queryOrders(userId);

        assertNotNull(result);
        assertFalse(result.isEmpty(), "结果列表不应为空");

        String routing = result.get(0);
        assertTrue(routing.startsWith("order_db_"), "数据库名应以 order_db_ 开头，实际：" + routing);
        assertTrue(routing.contains("t_order_"), "表名应包含 t_order_，实际：" + routing);
        assertTrue(routing.contains("@ds=order_db_"), "JpaPlusContext 的 DS 应与路由目标一致");
    }

    @Test
    @DisplayName("AOP 路由一致性：相同 userId 多次调用路由到相同目标")
    void testRoutingConsistency() {
        long userId = 12345L;
        List<String> r1 = orderQueryService.queryOrders(userId);
        List<String> r2 = orderQueryService.queryOrders(userId);

        assertEquals(r1, r2, "相同 userId 应始终路由到相同目标");
    }

    // ─── 测试用例 ────────────────────────────────────────────────────────────

    @Test
    @DisplayName("AOP 路由隔离：不同 userId 路由分布不完全相同（统计验证）")
    void testRoutingDistribution() {
        // 用 20 个不同 userId 采样，至少应路由到 2 个不同目标
        List<String> targets = new ArrayList<>();
        for (long uid = 0; uid < 20; uid++) {
            targets.add(orderQueryService.queryOrders(uid).get(0));
        }
        long distinct = targets.stream().distinct().count();
        assertTrue(distinct >= 2, "20 个不同 userId 应路由到至少 2 个不同目标，实际 distinct=" + distinct);
    }

    @Test
    @DisplayName("Optional 返回类型：结果不为 null，为 Optional 实例")
    void testOptionalReturnType() {
        Optional<String> result = orderQueryService.findOrder(100L);
        assertNotNull(result, "Optional 返回值不应为 null");
        assertTrue(result.isPresent(), "Optional 应为 present");
        assertTrue(result.get().startsWith("order_db_"), "Optional 内容应为路由后的库名");
    }

    @Test
    @DisplayName("SpEL 属性访问：#req.userId 从 OrderRequest 对象中提取分片键")
    void testSpelPropertyAccess() {
        var req = new OrderQueryService.OrderRequest(999L, "PAID");
        List<String> result = orderQueryService.queryByRequest(req);

        assertNotNull(result);
        assertFalse(result.isEmpty());

        // 直接 routeByKey 结果应与 AOP 路由结果一致
        ShardingTarget expected = router.routeByKey("t_order", 999L);
        assertEquals(expected.db(), result.get(0),
                "SpEL #req.userId 路由应与直接 routeByKey(999) 一致");
    }

    @Test
    @DisplayName("ShardingContext 生命周期：方法执行完毕后上下文应清除（无泄漏）")
    void testContextCleared() {
        orderQueryService.queryOrders(77L);
        // 方法退出后 ShardingContext 应已清除（ScopedValue 作用域自动退出）
        // 在 ScopedValue 模型下，作用域外访问返回 null/默认值
        ShardingTarget afterCall = ShardingContext.current();
        assertNull(afterCall, "方法退出后 ShardingContext 应为 null（无泄漏）");
    }

    @Test
    @DisplayName("executeAsStream：流式遍历全部 8 个分片，总条数正确")
    void testExecuteAsStream() {
        CrossShardQueryExecutor executor = new CrossShardQueryExecutor.SequentialCrossShardQueryExecutor();
        CrossShardQueryExecutor.ShardTaskFactory<String> factory =
                target -> List.of(target.db() + "#" + target.table());

        try (Stream<String> stream = executor.executeAsStream(ORDER_RULE, factory)) {
            List<String> collected = stream.collect(Collectors.toList());
            assertEquals(8, collected.size(), "2库×4表 = 8个分片，每片1条 = 8条");
        }
    }

    @Test
    @DisplayName("executeAsStream(sorted)：流式全局排序，结果有序")
    void testExecuteAsStreamSorted() {
        CrossShardQueryExecutor executor = new CrossShardQueryExecutor.SequentialCrossShardQueryExecutor();
        CrossShardQueryExecutor.ShardTaskFactory<Integer> factory = target -> {
            int base = Integer.parseInt(target.table().replace("t_order_", "")) * 10;
            return List.of(base + 3, base + 1, base + 2);
        };

        try (Stream<Integer> stream = executor.executeAsStream(ORDER_RULE, factory, Comparator.naturalOrder())) {
            List<Integer> sorted = stream.collect(Collectors.toList());
            for (int i = 1; i < sorted.size(); i++) {
                assertTrue(sorted.get(i) >= sorted.get(i - 1),
                        "排序后序列应非降序，位置 " + i + " 处违反");
            }
        }
    }

    // ─── CrossShardQueryExecutor 流式 API ───────────────────────────────────

    @Test
    @DisplayName("executeAsStream 惰性：分片查询在终止操作前不执行")
    void testExecuteAsStreamIsLazy() {
        AtomicReference<Integer> callCount = new AtomicReference<>(0);
        CrossShardQueryExecutor executor = new CrossShardQueryExecutor.SequentialCrossShardQueryExecutor();
        CrossShardQueryExecutor.ShardTaskFactory<String> factory = target -> {
            callCount.updateAndGet(n -> n + 1);
            return List.of(target.table());
        };

        // 仅创建 Stream，不调用终止操作
        Stream<String> stream = executor.executeAsStream(ORDER_RULE, factory);
        assertEquals(0, callCount.get(), "创建 Stream 时不应触发任何分片查询");

        // 执行 findFirst() 终止操作，只需查询第一个分片
        stream.findFirst();
        assertEquals(1, callCount.get(), "findFirst() 应只触发第一个分片查询（惰性）");
        stream.close();
    }

    @Configuration
    @EnableAspectJAutoProxy
    static class TestConfig {

        @Bean
        ShardingRuleRegistry shardingRuleRegistry() {
            ShardingRuleRegistry registry = new ShardingRuleRegistry();
            registry.register(ORDER_RULE);
            return registry;
        }

        @Bean
        ShardingRouter shardingRouter(ShardingRuleRegistry registry) {
            return new DefaultShardingRouter(registry,
                    new ShardingKeyExtractor.AnnotationShardingKeyExtractor(),
                    new ShardingAlgorithm.HashModShardingAlgorithm());
        }

        @Bean
        ShardingQueryAspect shardingQueryAspect(ShardingRouter router) {
            return new ShardingQueryAspect(router);
        }

        @Bean
        OrderQueryService orderQueryService() {
            return new OrderQueryService();
        }
    }

    @Component
    static class OrderQueryService {

        /**
         * 验证路由上下文，返回被路由到的库名 + 表名
         */
        @ShardingQuery(logicTable = "t_order", keyExpression = "#userId")
        public List<String> queryOrders(Long userId) {
            // 在切面设置的路由上下文中被调用
            ShardingTarget target = ShardingContext.current();
            String currentDs = JpaPlusContext.currentDS();
            return List.of(target.db() + ":" + target.table() + "@ds=" + currentDs);
        }

        /**
         * 验证 Optional 返回类型不为 null
         */
        @ShardingQuery(logicTable = "t_order", keyExpression = "#userId")
        public Optional<String> findOrder(Long userId) {
            ShardingTarget target = ShardingContext.current();
            return Optional.of(target.db()); // 返回路由到的库名
        }

        /**
         * 验证 SpEL 属性访问（从对象中取分片键）
         */
        @ShardingQuery(logicTable = "t_order", keyExpression = "#req.userId")
        public List<String> queryByRequest(OrderRequest req) {
            ShardingTarget target = ShardingContext.current();
            return List.of(target.db());
        }

        record OrderRequest(Long userId, String status) {
        }
    }
}

