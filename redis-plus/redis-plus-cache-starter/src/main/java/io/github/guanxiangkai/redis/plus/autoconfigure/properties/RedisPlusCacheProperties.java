package io.github.guanxiangkai.redis.plus.autoconfigure.properties;

import io.github.guanxiangkai.redis.plus.cache.CacheRuntimePolicy;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * Redis 三级缓存自动装配配置。
 *
 * <p>除缓存容量、TTL 和序列化白名单外，运行时边界统一通过 {@link RuntimeProperties} 转换为
 * {@link CacheRuntimePolicy}。</p>
 *
 * @author guanxiangkai
 * @since 1.0.0
 */
@Getter
@Setter
@Validated
@ConfigurationProperties(prefix = "redis-plus.cache")
public class RedisPlusCacheProperties {

    private boolean enabled = true;
    private String keyPrefix = "";
    @NotNull
    private Duration defaultTtl = Duration.ofMinutes(30);
    @DecimalMin("0.0")
    @DecimalMax("1.0")
    private double jitterRatio = 0.1;
    private List<String> allowedPackages = new ArrayList<>();

    @Valid
    @NestedConfigurationProperty
    private LocalCacheProperties local = new LocalCacheProperties();

    @Valid
    @NotNull
    @NestedConfigurationProperty
    private RuntimeProperties runtime = new RuntimeProperties();

    /**
     * L1 本地缓存容量与全局过期配置。
     *
     * @author guanxiangkai
     * @since 1.0.0
     */
    @Getter
    @Setter
    public static class LocalCacheProperties {
        @Positive
        private long maximumSize = 10_000;
        @NotNull
        private Duration ttl = Duration.ofMinutes(5);
    }

    /**
     * 三级缓存运行时边界配置。
     *
     * <p>批量缓存的 L1 回填 TTL 与空值回填 TTL 独立配置，避免将其与单条缓存的剩余 Redis TTL 混淆。</p>
     *
     * @author guanxiangkai
     * @since 1.0.0
     */
    @Getter
    @Setter
    public static class RuntimeProperties {

        private static final CacheRuntimePolicy DEFAULTS = CacheRuntimePolicy.defaults();

        @NotNull
        private Duration loadLockWait = DEFAULTS.loadLockWait();
        @NotNull
        private Duration loadLockLease = DEFAULTS.loadLockLease();
        @Positive
        private int clearScanBatchSize = DEFAULTS.clearScanBatchSize();
        @Positive
        private int clearDeleteBatchSize = DEFAULTS.clearDeleteBatchSize();
        @DecimalMin(value = "0.0", inclusive = false)
        @DecimalMax("1.0")
        private double nullValueTtlRatio = DEFAULTS.nullValueTtlRatio();
        @NotNull
        private Duration nullValueTtlMinimum = DEFAULTS.nullValueTtlMinimum();
        @NotNull
        private Duration batchLocalCacheTtl = DEFAULTS.batchLocalCacheTtl();
        @NotNull
        private Duration batchNullValueCacheTtl = DEFAULTS.batchNullValueCacheTtl();

        /**
         * 转换为缓存模块使用的不可变运行策略。
         *
         * @return 经过构造边界校验的运行策略
         */
        public CacheRuntimePolicy toPolicy() {
            return new CacheRuntimePolicy(loadLockWait, loadLockLease, clearScanBatchSize, clearDeleteBatchSize,
                    nullValueTtlRatio, nullValueTtlMinimum, batchLocalCacheTtl, batchNullValueCacheTtl);
        }
    }
}
