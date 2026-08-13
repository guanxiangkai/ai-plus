package io.github.guanxiangkai.jpa.plus.sharding.rule;

/**
 * 分片规则配置（不可变值对象）
 *
 * <p>描述一张逻辑表的完整分片策略：分几个库、每个库分几张表、如何命名物理表和数据源。</p>
 *
 * <h3>命名占位符规则</h3>
 * <ul>
 *   <li>{@code dbPattern}    —— 数据源名称模板，{@code {index}} 会被替换为库序号（0-based），
 *       例如 {@code "order_db_{index}"} → {@code "order_db_0"}、{@code "order_db_1"}</li>
 *   <li>{@code tablePattern} —— 物理表名模板，{@code {index}} 会被替换为表序号（0-based），
 *       例如 {@code "order_{index}"} → {@code "order_0"}、{@code "order_7"}</li>
 * </ul>
 *
 * <h3>示例</h3>
 * <pre>{@code
 * ShardingRule rule = new ShardingRule(
 *     "order",          // 逻辑表名（与实体表名配置一致）
 *     4,                // 分 4 个库
 *     8,                // 每库 8 张表
 *     "order_db_{index}",  // 数据源名称模式
 *     "order_{index}",     // 物理表名模式
 *     null              // 自动解析分片键，或直接填写字段名
 * );
 * }</pre>
 *
 * <p>字段说明：{@code logicTableName} 为逻辑表名；{@code dbCount} / {@code tableCount}
 * 分别表示分库数与每库分表数；{@code dbPattern} / {@code tablePattern} 为命名模板；
 * {@code shardingKeyField} 为空时由分片键注解或约定字段自动解析。</p>
 *
 * @author guanxiangkai
 * @since 1.0.0
 */
public record ShardingRule(
        String logicTableName,
        int dbCount,
        int tableCount,
        String dbPattern,
        String tablePattern,
        String shardingKeyField
) {

    public ShardingRule {
        if (logicTableName == null || logicTableName.isBlank())
            throw new IllegalArgumentException("logicTableName must not be blank");
        if (dbCount < 1) throw new IllegalArgumentException("dbCount must be >= 1");
        if (tableCount < 1) throw new IllegalArgumentException("tableCount must be >= 1");
        Math.multiplyExact(dbCount, tableCount);
        if (dbPattern == null || dbPattern.isBlank())
            throw new IllegalArgumentException("dbPattern must not be blank");
        if (tablePattern == null || tablePattern.isBlank())
            throw new IllegalArgumentException("tablePattern must not be blank");
    }

    /**
     * 根据库序号生成数据源名称（序号从 0 开始）
     */
    public String resolveDb(int dbIndex) {
        return dbPattern.replace("{index}", String.valueOf(dbIndex));
    }

    /**
     * 根据表序号生成物理表名（序号从 0 开始）
     */
    public String resolveTable(int tableIndex) {
        return tablePattern.replace("{index}", String.valueOf(tableIndex));
    }

    /**
     * 总分片数（dbCount × tableCount）
     */
    public int totalShards() {
        return Math.multiplyExact(dbCount, tableCount);
    }
}
