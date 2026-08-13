package io.github.guanxiangkai.redis.plus.autoconfigure.properties;

import io.github.guanxiangkai.redis.plus.cache.CacheRuntimePolicy;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * {@link RedisPlusCacheProperties} 运行时策略转换测试。
 *
 * @author guanxiangkai
 * @since 1.0.0
 */
class RedisPlusCachePropertiesTest {

    @Test
    void runtime_convertsConfiguredValuesToPolicy() {
        RedisPlusCacheProperties.RuntimeProperties runtime = new RedisPlusCacheProperties.RuntimeProperties();
        runtime.setLoadLockWait(Duration.ofSeconds(4));
        runtime.setClearDeleteBatchSize(128);
        runtime.setNullValueTtlRatio(0.4);

        CacheRuntimePolicy policy = runtime.toPolicy();

        assertEquals(Duration.ofSeconds(4), policy.loadLockWait());
        assertEquals(128, policy.clearDeleteBatchSize());
        assertEquals(Duration.ofMinutes(2), policy.resolveNullValueTtl(Duration.ofMinutes(5)));
    }

    @Test
    void runtime_rejectsValuesOutsidePolicyBoundary() {
        RedisPlusCacheProperties.RuntimeProperties runtime = new RedisPlusCacheProperties.RuntimeProperties();
        runtime.setBatchNullValueCacheTtl(Duration.ZERO);

        assertThrows(IllegalArgumentException.class, runtime::toPolicy);
    }
}
