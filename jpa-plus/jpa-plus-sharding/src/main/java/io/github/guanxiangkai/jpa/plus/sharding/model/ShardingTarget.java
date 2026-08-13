package io.github.guanxiangkai.jpa.plus.sharding.model;

/**
 * 分片路由结果（不可变值对象）
 *
 * <p>封装一次路由计算的目标库名和目标表名。
 * 使用 Java {@code record} 保证不可变性，线程安全。</p>
 *
 * <h3>示例</h3>
 * <pre>{@code
 * // 逻辑表 order，userId=12345，按 4 库 8 表分片
 * ShardingTarget target = router.route("order", entity);
 * // 可能得到：ShardingTarget[db="order_db_2", table="order_3"]
 * }</pre>
 *
 * <p>其中 {@code db} 为目标数据源名称，{@code table} 为目标物理表名。</p>
 *
 * @author guanxiangkai
 * @since 1.0.0
 */
public record ShardingTarget(String db, String table) {

    public ShardingTarget {
        if (db == null || db.isBlank()) throw new IllegalArgumentException("ShardingTarget.db must not be blank");
        if (table == null || table.isBlank())
            throw new IllegalArgumentException("ShardingTarget.table must not be blank");
    }

    @Override
    public String toString() {
        return "ShardingTarget{db='" + db + "', table='" + table + "'}";
    }
}

