package io.github.guanxiangkai.jpa.plus.field.id.generator;

import java.util.concurrent.atomic.AtomicReference;

/**
 * 雪花算法（Snowflake）ID 生成器
 *
 * <p>生成 64 位分布式唯一 ID，结构如下：</p>
 * <pre>
 * ┌─────────┬──────────────────────────────────┬────────────┬────────────┬──────────────┐
 * │  1 bit  │             41 bits              │   5 bits   │   5 bits   │   12 bits    │
 * │  符号位  │       时间戳差值（毫秒）            │  数据中心ID │  工作节点ID │    序列号     │
 * │   (0)   │     (当前时间 - 自定义纪元)         │   (0~31)   │   (0~31)   │  (0~4095)    │
 * └─────────┴──────────────────────────────────┴────────────┴────────────┴──────────────┘
 * </pre>
 *
 * <h3>特性</h3>
 * <ul>
 *   <li><b>趋势递增</b> —— 时间戳在高位，天然有序，B+ 树索引友好</li>
 *   <li><b>分布式唯一</b> —— 数据中心 + 工作节点 ID 保证多节点不重复</li>
 *   <li><b>高性能</b> —— 纯内存运算，单节点 QPS 可达 400w+/秒</li>
 *   <li><b>无外部依赖</b> —— 不需要数据库或 Redis 参与</li>
 *   <li><b>虚拟线程友好</b> —— 使用 CAS 替代 synchronized，避免 JDK 21+ 虚拟线程钉扎（pinning）</li>
 * </ul>
 *
 * <h3>配置</h3>
 * <pre>{@code
 * jpa-plus:
 *   id-generator:
 *     type: SNOWFLAKE
 *     snowflake:
 *       worker-id: ${SNOWFLAKE_WORKER_ID:1}       # 工作节点 ID（0~31）
 *       datacenter-id: ${SNOWFLAKE_DC_ID:1}        # 数据中心 ID（0~31）
 *       epoch: 1700000000000                        # 自定义纪元（毫秒时间戳，项目上线日期附近）
 * }</pre>
 *
 * <p><b>注意：</b>{@code epoch} 一旦确定不可修改，否则可能生成重复 ID。
 * 推荐设为 2023-11-15 00:53:20 UTC（{@code 1700000000000L}），可用约 69 年至 2092 年。</p>
 *
 * @author guanxiangkai
 * @since 1.0.0
 */
public class SnowflakeIdGenerator {

    // ─── 位分配 ───
    private static final long WORKER_ID_BITS = 5L;
    private static final long DATACENTER_ID_BITS = 5L;
    private static final long SEQUENCE_BITS = 12L;

    // ─── 最大值 ───
    private static final long MAX_WORKER_ID = ~(-1L << WORKER_ID_BITS);         // 31
    private static final long MAX_DATACENTER_ID = ~(-1L << DATACENTER_ID_BITS); // 31
    private static final long SEQUENCE_MASK = ~(-1L << SEQUENCE_BITS);          // 4095

    // ─── 位移量 ───
    private static final long WORKER_ID_SHIFT = SEQUENCE_BITS;                                        // 12
    private static final long DATACENTER_ID_SHIFT = SEQUENCE_BITS + WORKER_ID_BITS;                   // 17
    private static final long TIMESTAMP_SHIFT = SEQUENCE_BITS + WORKER_ID_BITS + DATACENTER_ID_BITS;  // 22

    private final long workerId;
    private final long datacenterId;
    private final long epoch;
    private final AtomicReference<GeneratorState> state =
            new AtomicReference<>(new GeneratorState(0L, -1L));

    /**
     * @param workerId     工作节点 ID（0~31）
     * @param datacenterId 数据中心 ID（0~31）
     * @param epoch        自定义纪元（毫秒时间戳，不可修改）
     */
    public SnowflakeIdGenerator(long workerId, long datacenterId, long epoch) {
        if (workerId < 0 || workerId > MAX_WORKER_ID) {
            throw new IllegalArgumentException(
                    "Worker ID must be between 0 and " + MAX_WORKER_ID + ", got: " + workerId);
        }
        if (datacenterId < 0 || datacenterId > MAX_DATACENTER_ID) {
            throw new IllegalArgumentException(
                    "Datacenter ID must be between 0 and " + MAX_DATACENTER_ID + ", got: " + datacenterId);
        }
        this.workerId = workerId;
        this.datacenterId = datacenterId;
        this.epoch = epoch;
    }

    /**
     * 生成下一个唯一 ID
     *
     * <p>线程安全，使用无锁 CAS 循环实现。同一毫秒内通过序列号区分（最多 4096 个/毫秒/节点）。
     * 序列号耗尽时忙等至下一毫秒；时钟回拨 ≤5ms 时短暂睡眠，&gt;5ms 时直接抛出异常。</p>
     *
     * <p><b>虚拟线程安全：</b>睡眠操作发生在 CAS 循环之外，不持有任何监视器锁，
     * 不会触发 JDK 21+ 的虚拟线程钉扎（carrier thread pinning）。</p>
     *
     * @return 64 位唯一 ID
     * @throws IllegalStateException 系统时钟回拨超过容忍阈值时抛出
     */
    public long nextId() {
        while (true) {
            long timestamp = currentTimeMillis();
            GeneratorState curr = state.get();
            long lastTs = curr.lastTimestamp();

            // ─── 时钟回拨检测（睡眠在 CAS 循环外，不持有任何锁） ───
            if (timestamp < lastTs) {
                long offset = lastTs - timestamp;
                if (offset <= 5) {
                    try {
                        Thread.sleep(offset << 1);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        throw clockBackwardsException(timestamp);
                    }
                    timestamp = currentTimeMillis();
                    if (timestamp < lastTs) {
                        throw clockBackwardsException(timestamp);
                    }
                } else {
                    throw clockBackwardsException(timestamp);
                }
            }

            long seq;
            long ts;
            if (timestamp == lastTs) {
                seq = (curr.sequence() + 1) & SEQUENCE_MASK;
                if (seq == 0) {
                    // 序列号溢出，忙等到下一毫秒（无锁）
                    ts = waitNextMillis(lastTs);
                } else {
                    ts = timestamp;
                }
            } else {
                // 新的毫秒，序列号归零
                seq = 0L;
                ts = timestamp;
            }

            GeneratorState next = new GeneratorState(seq, ts);
            if (state.compareAndSet(curr, next)) {
                return ((ts - epoch) << TIMESTAMP_SHIFT)
                        | (datacenterId << DATACENTER_ID_SHIFT)
                        | (workerId << WORKER_ID_SHIFT)
                        | seq;
            }
            // CAS 失败：另一线程先于本线程更新状态，自旋重试
        }
    }

    private long waitNextMillis(long lastTimestamp) {
        long timestamp = currentTimeMillis();
        while (timestamp <= lastTimestamp) {
            Thread.onSpinWait();
            timestamp = currentTimeMillis();
        }
        return timestamp;
    }

    // ─── 内部方法 ───

    private long currentTimeMillis() {
        return System.currentTimeMillis();
    }

    private IllegalStateException clockBackwardsException(long timestamp) {
        long lastTs = state.get().lastTimestamp();
        return new IllegalStateException(
                "Clock moved backwards. Refusing to generate ID for " +
                        (lastTs - timestamp) + " milliseconds. " +
                        "Last timestamp: " + lastTs + ", current: " + timestamp);
    }

    /**
     * 不可变状态快照（序列号 + 上次时间戳），通过 CAS 原子更新。
     * 替代 volatile 裸字段，避免 synchronized + Thread.sleep() 组合对虚拟线程的钉扎。
     */
    private record GeneratorState(long sequence, long lastTimestamp) {
    }
}

