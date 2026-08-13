package io.github.guanxiangkai.redis.plus.cache;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * {@link CacheRuntimePolicy} 行为测试。
 *
 * @author guanxiangkai
 * @since 1.0.0
 */
class CacheRuntimePolicyTest {

    @Test
    void defaults_providesSingleProgrammaticRuntimePolicy() {
        CacheRuntimePolicy policy = CacheRuntimePolicy.defaults();

        assertEquals(Duration.ofSeconds(10), policy.loadLockWait());
        assertEquals(Duration.ofSeconds(30), policy.loadLockLease());
        assertEquals(1_000, policy.clearScanBatchSize());
        assertEquals(1_000, policy.clearDeleteBatchSize());
        assertEquals(Duration.ofMinutes(5), policy.batchLocalCacheTtl());
        assertEquals(Duration.ofMinutes(5), policy.batchNullValueCacheTtl());
    }

    @Test
    void resolveNullValueTtl_appliesRatioAndMinimum() {
        CacheRuntimePolicy policy = CacheRuntimePolicy.defaults();

        assertEquals(Duration.ofMinutes(1), policy.resolveNullValueTtl(Duration.ofMinutes(5)));
        assertEquals(Duration.ofSeconds(30), policy.resolveNullValueTtl(Duration.ofSeconds(10)));
    }

    @Test
    void constructor_rejectsInvalidRuntimeBoundaries() {
        CacheRuntimePolicy defaults = CacheRuntimePolicy.defaults();

        assertThrows(IllegalArgumentException.class, () -> new CacheRuntimePolicy(
                Duration.ZERO, defaults.loadLockLease(), defaults.clearScanBatchSize(), defaults.clearDeleteBatchSize(),
                defaults.nullValueTtlRatio(), defaults.nullValueTtlMinimum(), defaults.batchLocalCacheTtl(),
                defaults.batchNullValueCacheTtl()));
        assertThrows(IllegalArgumentException.class, () -> new CacheRuntimePolicy(
                defaults.loadLockWait(), defaults.loadLockLease(), 0, defaults.clearDeleteBatchSize(),
                defaults.nullValueTtlRatio(), defaults.nullValueTtlMinimum(), defaults.batchLocalCacheTtl(),
                defaults.batchNullValueCacheTtl()));
        assertThrows(IllegalArgumentException.class, () -> new CacheRuntimePolicy(
                defaults.loadLockWait(), defaults.loadLockLease(), defaults.clearScanBatchSize(), defaults.clearDeleteBatchSize(),
                1.1, defaults.nullValueTtlMinimum(), defaults.batchLocalCacheTtl(), defaults.batchNullValueCacheTtl()));
    }
}
