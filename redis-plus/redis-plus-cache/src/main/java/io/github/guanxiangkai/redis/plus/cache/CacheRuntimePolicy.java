package io.github.guanxiangkai.redis.plus.cache;

import java.time.Duration;
import java.util.Objects;

/**
 * 三级缓存运行时共享策略。
 *
 * <p>统一定义回源锁、批量清理和批量缓存回填的边界，避免不同缓存入口拥有不一致的硬编码参数。</p>
 *
 * @author guanxiangkai
 * @since 1.0.0
 */
public record CacheRuntimePolicy(
        Duration loadLockWait,
        Duration loadLockLease,
        int clearScanBatchSize,
        int clearDeleteBatchSize,
        double nullValueTtlRatio,
        Duration nullValueTtlMinimum,
        Duration batchLocalCacheTtl,
        Duration batchNullValueCacheTtl
) {

    /**
     * 创建经过边界校验的缓存运行策略。
     *
     * @throws IllegalArgumentException 参数不满足运行时边界时抛出
     */
    public CacheRuntimePolicy {
        loadLockWait = requirePositive(loadLockWait, "loadLockWait");
        loadLockLease = requirePositive(loadLockLease, "loadLockLease");
        if (clearScanBatchSize <= 0) {
            throw new IllegalArgumentException("clearScanBatchSize 必须大于 0");
        }
        if (clearDeleteBatchSize <= 0) {
            throw new IllegalArgumentException("clearDeleteBatchSize 必须大于 0");
        }
        if (nullValueTtlRatio <= 0 || nullValueTtlRatio > 1) {
            throw new IllegalArgumentException("nullValueTtlRatio 必须在 (0, 1] 范围内");
        }
        nullValueTtlMinimum = requirePositive(nullValueTtlMinimum, "nullValueTtlMinimum");
        batchLocalCacheTtl = requirePositive(batchLocalCacheTtl, "batchLocalCacheTtl");
        batchNullValueCacheTtl = requirePositive(batchNullValueCacheTtl, "batchNullValueCacheTtl");
    }

    /**
     * 返回程序化装配使用的唯一默认运行策略。
     *
     * @return 当前默认缓存运行策略
     */
    public static CacheRuntimePolicy defaults() {
        return new CacheRuntimePolicy(
                Duration.ofSeconds(10),
                Duration.ofSeconds(30),
                1_000,
                1_000,
                0.2,
                Duration.ofSeconds(30),
                Duration.ofMinutes(5),
                Duration.ofMinutes(5));
    }

    /**
     * 根据基准 TTL 计算空值缓存 TTL。
     *
     * @param baseTtl 缓存条目的基准 TTL
     * @return 不低于空值 TTL 下限的空值缓存 TTL
     */
    public Duration resolveNullValueTtl(Duration baseTtl) {
        Duration validatedBaseTtl = requirePositive(baseTtl, "baseTtl");
        long ratioMillis = (long) Math.ceil(validatedBaseTtl.toMillis() * nullValueTtlRatio);
        return Duration.ofMillis(Math.max(ratioMillis, nullValueTtlMinimum.toMillis()));
    }

    private static Duration requirePositive(Duration value, String name) {
        Objects.requireNonNull(value, name + " 不能为空");
        if (value.isZero() || value.isNegative()) {
            throw new IllegalArgumentException(name + " 必须大于 0");
        }
        return value;
    }
}
