package io.github.guanxiangkai.jpa.plus.sharding;

import io.github.guanxiangkai.jpa.plus.sharding.model.ShardingTarget;
import io.github.guanxiangkai.jpa.plus.sharding.router.DefaultShardingRouter;
import io.github.guanxiangkai.jpa.plus.sharding.router.ShardingRouter;
import io.github.guanxiangkai.jpa.plus.sharding.rule.ShardingRule;
import io.github.guanxiangkai.jpa.plus.sharding.rule.ShardingRuleRegistry;
import io.github.guanxiangkai.jpa.plus.sharding.spi.CrossShardQueryExecutor;
import io.github.guanxiangkai.jpa.plus.sharding.spi.ShardingAlgorithm;
import io.github.guanxiangkai.jpa.plus.sharding.spi.ShardingKeyExtractor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 分片路由核心逻辑集成测试
 *
 * <p>使用纯 Java 不依赖 Spring 上下文，验证 {@link ShardingRule}、{@link ShardingRouter}、
 * {@link ShardingAlgorithm}、{@link CrossShardQueryExecutor} 的路由逻辑正确性。</p>
 *
 * <p>通过 H2 内存库场景模拟：规则配置 2 库 × 4 表，断言不同分片键路由到预期目标。</p>
 *
 * @author guanxiangkai
 * @since 1.0.0
 */
@DisplayName("分片路由集成测试（H2 内存库场景）")
class ShardingRoutingTest {

    // ─── 测试规则：2 库 × 4 表，模拟 H2 场景下的 t_order 表 ───

    private static final ShardingRule ORDER_RULE = new ShardingRule(
            "t_order",
            2,          // 2 个库：order_db_0 / order_db_1
            4,          // 每库 4 张表：t_order_0 … t_order_3
            "order_db_{index}",
            "t_order_{index}",
            "userId"
    );

    private ShardingRuleRegistry registry;
    private ShardingRouter router;
    private CrossShardQueryExecutor sequentialExecutor;
    private CrossShardQueryExecutor parallelExecutor;

    @BeforeEach
    void setUp() {
        registry = new ShardingRuleRegistry();
        registry.register(ORDER_RULE);

        ShardingKeyExtractor extractor = new ShardingKeyExtractor.AnnotationShardingKeyExtractor();
        ShardingAlgorithm algorithm = new ShardingAlgorithm.HashModShardingAlgorithm();
        router = new DefaultShardingRouter(registry, extractor, algorithm);

        sequentialExecutor = new CrossShardQueryExecutor.SequentialCrossShardQueryExecutor();
        parallelExecutor = new CrossShardQueryExecutor.ParallelCrossShardQueryExecutor();
    }

    // ─── ShardingRule 基本属性 ───────────────────────────────────────────────

    @Test
    @DisplayName("ShardingRule：总分片数 = dbCount × tableCount")
    void testTotalShards() {
        assertEquals(8, ORDER_RULE.totalShards());
    }

    @Test
    @DisplayName("ShardingRule：resolveDb / resolveTable 命名模式替换")
    void testResolveNaming() {
        assertEquals("order_db_0", ORDER_RULE.resolveDb(0));
        assertEquals("order_db_1", ORDER_RULE.resolveDb(1));
        assertEquals("t_order_0", ORDER_RULE.resolveTable(0));
        assertEquals("t_order_3", ORDER_RULE.resolveTable(3));
    }

    // ─── Hash-Mod 路由一致性 ─────────────────────────────────────────────────

    @Test
    @DisplayName("routeByKey：相同分片键多次路由结果一致（Hash 路由幂等性）")
    void testRouteConsistency() {
        ShardingTarget t1 = router.routeByKey("t_order", 12345L);
        ShardingTarget t2 = router.routeByKey("t_order", 12345L);
        assertEquals(t1, t2, "相同分片键应始终路由到相同目标");
    }

    @Test
    @DisplayName("routeByKey：路由目标 db 和 table 均来自规则定义的命名模式")
    void testRouteTargetFormat() {
        for (long userId = 0L; userId < 20L; userId++) {
            ShardingTarget target = router.routeByKey("t_order", userId);
            assertTrue(target.db().startsWith("order_db_"),
                    "db 应以 order_db_ 开头，实际：" + target.db());
            assertTrue(target.table().startsWith("t_order_"),
                    "table 应以 t_order_ 开头，实际：" + target.table());
        }
    }

    @Test
    @DisplayName("routeByKey：路由结果 db/table 序号均在合法范围内")
    void testRouteIndexInRange() {
        for (long userId = 0L; userId < 100L; userId++) {
            ShardingTarget target = router.routeByKey("t_order", userId);
            int dbIndex = Integer.parseInt(target.db().replace("order_db_", ""));
            int tableIndex = Integer.parseInt(target.table().replace("t_order_", ""));
            assertTrue(dbIndex >= 0 && dbIndex < ORDER_RULE.dbCount(),
                    "dbIndex 越界：" + dbIndex);
            assertTrue(tableIndex >= 0 && tableIndex < ORDER_RULE.tableCount(),
                    "tableIndex 越界：" + tableIndex);
        }
    }

    @Test
    @DisplayName("routeByKey：shardingKey 为 null 时应抛出 IllegalArgumentException")
    void testNullKeyThrows() {
        assertThrows(IllegalArgumentException.class,
                () -> router.routeByKey("t_order", null));
    }

    @Test
    @DisplayName("routeByKey：未注册规则的逻辑表应抛出 IllegalArgumentException")
    void testUnregisteredTableThrows() {
        assertThrows(IllegalArgumentException.class,
                () -> router.routeByKey("t_unknown", 1L));
    }

    // ─── isSharded ───────────────────────────────────────────────────────────

    @Test
    @DisplayName("isSharded：已注册表返回 true，未注册表返回 false")
    void testIsSharded() {
        assertTrue(router.isSharded("t_order"));
        assertFalse(router.isSharded("t_user"));
    }

    // ─── RangeShardingAlgorithm ──────────────────────────────────────────────

    @Test
    @DisplayName("RangeShardingAlgorithm：ID 在同一区段路由到同一分片")
    void testRangeAlgorithm() {
        ShardingAlgorithm range = new ShardingAlgorithm.RangeShardingAlgorithm(1_000_000L);
        ShardingTarget t1 = range.route("t_order", 1L, ORDER_RULE);
        ShardingTarget t2 = range.route("t_order", 500_000L, ORDER_RULE);
        assertEquals(t1, t2, "同一 Range 段内的 ID 应路由到相同分片");

        ShardingTarget t3 = range.route("t_order", 1_000_001L, ORDER_RULE);
        assertNotEquals(t1, t3, "不同 Range 段的 ID 应路由到不同分片");
    }

    // ─── CrossShardQueryExecutor ─────────────────────────────────────────────

    @Test
    @DisplayName("SequentialExecutor：executeAll 遍历全部 8 个分片，返回正确总条数")
    void testSequentialExecuteAll() throws Exception {
        // 每个分片固定返回 3 条虚拟数据
        CrossShardQueryExecutor.ShardTaskFactory<String> factory =
                target -> List.of(target.db() + "#" + target.table() + "#1",
                        target.db() + "#" + target.table() + "#2",
                        target.db() + "#" + target.table() + "#3");

        List<String> result = sequentialExecutor.executeAll(ORDER_RULE, factory);
        assertEquals(24, result.size(), "2库×4表×3条 = 24条");
    }

    @Test
    @DisplayName("ParallelExecutor：executeAll 并行结果与顺序结果等量")
    void testParallelExecuteAll() throws Exception {
        CrossShardQueryExecutor.ShardTaskFactory<String> factory =
                target -> List.of(target.table());

        List<String> seqResult = sequentialExecutor.executeAll(ORDER_RULE, factory);
        List<String> parResult = parallelExecutor.executeAll(ORDER_RULE, factory);

        assertEquals(seqResult.size(), parResult.size(),
                "顺序与并行执行结果条数应一致");
    }

    @Test
    @DisplayName("executeAllSorted：结果按给定 Comparator 全局升序排列")
    void testExecuteAllSorted() throws Exception {
        CrossShardQueryExecutor.ShardTaskFactory<Integer> factory = target -> {
            // 每个分片返回若干无序整数
            int base = Integer.parseInt(target.table().replace("t_order_", "")) * 10;
            return List.of(base + 3, base + 1, base + 2);
        };

        List<Integer> sorted = sequentialExecutor.executeAllSorted(ORDER_RULE, factory,
                Comparator.naturalOrder());

        for (int i = 1; i < sorted.size(); i++) {
            assertTrue(sorted.get(i) >= sorted.get(i - 1),
                    "索引 " + i + " 处元素应 >= 前一个，实际：" + sorted.get(i - 1) + " > " + sorted.get(i));
        }
    }

    @Test
    @DisplayName("executePaged：全局排序后分页，第1页大小正确")
    void testExecutePaged() throws Exception {
        // 8 个分片，每片产生 10 条数据（序号 0-9），共 80 条
        CrossShardQueryExecutor.ShardTaskFactory<Integer> factory = target -> {
            List<Integer> list = new ArrayList<>();
            for (int i = 0; i < 10; i++) list.add(i);
            return list;
        };

        CrossShardQueryExecutor.PagedResult<Integer> page1 =
                sequentialExecutor.executePaged(ORDER_RULE, factory,
                        Comparator.naturalOrder(), 1, 10);

        assertEquals(80, page1.total());
        assertEquals(10, page1.content().size());
        assertEquals(8, page1.totalPages());
        assertTrue(page1.hasNext());
        assertFalse(page1.hasPrevious());
    }

    @Test
    @DisplayName("executePaged：最后一页数据量不超 pageSize")
    void testExecutePagedLastPage() throws Exception {
        // 8 个分片 × 3 条 = 24 条，每页 5 条 → 5 页，最后页 4 条
        CrossShardQueryExecutor.ShardTaskFactory<Integer> factory =
                target -> List.of(1, 2, 3);

        CrossShardQueryExecutor.PagedResult<Integer> lastPage =
                sequentialExecutor.executePaged(ORDER_RULE, factory,
                        Comparator.naturalOrder(), 5, 5);

        assertEquals(24, lastPage.total());
        assertEquals(4, lastPage.content().size(), "最后一页应有 4 条");
        assertFalse(lastPage.hasNext());
        assertTrue(lastPage.hasPrevious());
    }

    @Test
    @DisplayName("executePaged：超出范围的页码返回空 content")
    void testExecutePagedOutOfRange() throws Exception {
        CrossShardQueryExecutor.ShardTaskFactory<Integer> factory =
                target -> List.of(1, 2, 3);

        CrossShardQueryExecutor.PagedResult<Integer> outOfRange =
                sequentialExecutor.executePaged(ORDER_RULE, factory,
                        Comparator.naturalOrder(), 100, 10);

        assertTrue(outOfRange.content().isEmpty());
        assertEquals(24, outOfRange.total());
    }
}

