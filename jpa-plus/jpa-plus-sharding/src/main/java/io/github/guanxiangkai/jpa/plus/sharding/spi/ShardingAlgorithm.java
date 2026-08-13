package io.github.guanxiangkai.jpa.plus.sharding.spi;

import io.github.guanxiangkai.jpa.plus.sharding.model.ShardingTarget;
import io.github.guanxiangkai.jpa.plus.sharding.rule.ShardingRule;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;

/**
 * 分片算法（SPI）
 *
 * <p>根据逻辑表名、分片键值和分片规则，计算出目标数据源和物理表名。</p>
 *
 * <h3>框架内置实现</h3>
 * <ul>
 *   <li>{@link HashModShardingAlgorithm} —— 基于 Hash-Mod 的均匀分片（默认）</li>
 *   <li>{@link RangeShardingAlgorithm} —— 基于数值范围的分片，适合按 ID 段分片</li>
 *   <li>{@link DateShardingAlgorithm} —— 基于日期的分片，按年月路由到指定库表</li>
 * </ul>
 *
 * <h3>自定义示例（按日期范围分片）</h3>
 * <pre>{@code
 * @Component
 * public class MyDateRangeShardingAlgorithm implements ShardingAlgorithm {
 *
 *     @Override
 *     public ShardingTarget route(String logicTable, Object shardingKey, ShardingRule rule) {
 *         LocalDate date = (LocalDate) shardingKey;
 *         int year = date.getYear();
 *         int month = date.getMonthValue();
 *         String db = "order_db_" + (year % rule.dbCount());
 *         String table = logicTable + "_" + year + String.format("%02d", month);
 *         return new ShardingTarget(db, table);
 *     }
 * }
 * }</pre>
 *
 * <p><b>设计模式：</b>策略模式（Strategy）</p>
 *
 * @author guanxiangkai
 * @since 1.0.0
 */
@FunctionalInterface
public interface ShardingAlgorithm {

    /**
     * 计算路由目标
     *
     * @param logicTable  逻辑表名
     * @param shardingKey 分片键值（不为 {@code null}）
     * @param rule        分片规则
     * @return 路由结果，包含目标数据源名称和物理表名
     */
    ShardingTarget route(String logicTable, Object shardingKey, ShardingRule rule);

    // ─────────────────────────────────────────────
    // 内置默认实现
    // ─────────────────────────────────────────────

    /**
     * 基于 Hash-Mod 的均匀分片算法（默认实现）
     *
     * <h3>算法说明</h3>
     * <pre>
     * globalShard = abs(hash(shardingKey)) % (dbCount × tableCount)
     * dbIndex     = globalShard / tableCount
     * tableIndex  = globalShard % tableCount
     * </pre>
     *
     * <p>对字符串类型的分片键使用 {@link String#hashCode()}；
     * 对数值类型直接取 {@code longValue()}；其余类型使用 {@code toString().hashCode()}。</p>
     */
    class HashModShardingAlgorithm implements ShardingAlgorithm {

        private static long toHash(Object key) {
            if (key instanceof Number n) return n.longValue();
            if (key instanceof String s) return s.hashCode();
            return key.toString().hashCode();
        }

        @Override
        public ShardingTarget route(String logicTable, Object shardingKey, ShardingRule rule) {
            long hash = toHash(shardingKey);
            int totalShards = rule.totalShards();
            // Use bitmasking instead of Math.abs to avoid Long.MIN_VALUE overflow
            // (Math.abs(Long.MIN_VALUE) == Long.MIN_VALUE which is still negative)
            int globalShard = (int) ((hash & Long.MAX_VALUE) % totalShards);
            int dbIndex = globalShard / rule.tableCount();
            int tableIndex = globalShard % rule.tableCount();
            return new ShardingTarget(rule.resolveDb(dbIndex), rule.resolveTable(tableIndex));
        }
    }

    /**
     * 基于数值范围的分片算法
     *
     * <p>将数值分片键均匀分配到各分片，适合按 ID 段分片场景。
     * 例如 ID 1~1000000 路由到 db_0，1000001~2000000 路由到 db_1。</p>
     *
     * <p>使用方式：指定 {@code rangeSize}（每个分片的 ID 段大小）</p>
     *
     * <h3>示例</h3>
     * <pre>{@code
     * // 每 100 万条路由一个分片，共 4 库 × 4 表
     * new RangeShardingAlgorithm(1_000_000L)
     * }</pre>
     */
    class RangeShardingAlgorithm implements ShardingAlgorithm {

        private final long rangeSize;

        /**
         * 默认每分片 100 万条
         */
        public RangeShardingAlgorithm() {
            this(1_000_000L);
        }

        public RangeShardingAlgorithm(long rangeSize) {
            if (rangeSize <= 0) throw new IllegalArgumentException("rangeSize must be > 0");
            this.rangeSize = rangeSize;
        }

        private static long toNumber(Object key) {
            if (key instanceof Number n) return n.longValue() & Long.MAX_VALUE;
            try {
                return Long.parseLong(key.toString()) & Long.MAX_VALUE;
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("RangeShardingAlgorithm requires a numeric sharding key, got: " + key);
            }
        }

        @Override
        public ShardingTarget route(String logicTable, Object shardingKey, ShardingRule rule) {
            long id = toNumber(shardingKey);
            long shardIndex = (id / rangeSize) % rule.totalShards();
            int dbIndex = (int) (shardIndex / rule.tableCount());
            int tableIndex = (int) (shardIndex % rule.tableCount());
            return new ShardingTarget(rule.resolveDb(dbIndex), rule.resolveTable(tableIndex));
        }
    }

    /**
     * 基于日期的分片算法
     *
     * <p>支持 {@link LocalDate}、{@link LocalDateTime}、{@link Date}、{@link String}（yyyy-MM-dd）
     * 类型的分片键，按年份取模路由到数据库，按月份取模路由到物理表。</p>
     *
     * <h3>路由规则</h3>
     * <pre>
     * dbIndex    = year % dbCount
     * tableIndex = (month - 1) % tableCount
     * </pre>
     *
     * <h3>示例</h3>
     * <pre>{@code
     * // 4 库 × 12 表，按年分库、按月分表
     * new DateShardingAlgorithm()
     * }</pre>
     */
    class DateShardingAlgorithm implements ShardingAlgorithm {

        /**
         * 时区（默认 UTC，保证跨时区 JVM 分片结果一致）
         *
         * <p>如果业务数据以本地时区存储，可通过构造器传入 {@link ZoneId#systemDefault()}，
         * 但需确保所有节点使用相同时区配置。</p>
         */
        private final ZoneId zoneId;

        public DateShardingAlgorithm() {
            this(ZoneId.of("UTC"));
        }

        public DateShardingAlgorithm(ZoneId zoneId) {
            this.zoneId = java.util.Objects.requireNonNull(zoneId, "zoneId must not be null");
        }

        private LocalDate toLocalDate(Object key) {
            return switch (key) {
                case LocalDate ld -> ld;
                case LocalDateTime ldt -> ldt.toLocalDate();
                case Date d -> d.toInstant().atZone(zoneId).toLocalDate();
                case String s -> LocalDate.parse(s);
                default -> throw new IllegalArgumentException(
                        "DateShardingAlgorithm: unsupported key type " + key.getClass().getName());
            };
        }

        @Override
        public ShardingTarget route(String logicTable, Object shardingKey, ShardingRule rule) {
            LocalDate date = toLocalDate(shardingKey);
            int dbIndex = date.getYear() % rule.dbCount();
            int tableIndex = (date.getMonthValue() - 1) % rule.tableCount();
            return new ShardingTarget(rule.resolveDb(dbIndex), rule.resolveTable(tableIndex));
        }
    }
}

