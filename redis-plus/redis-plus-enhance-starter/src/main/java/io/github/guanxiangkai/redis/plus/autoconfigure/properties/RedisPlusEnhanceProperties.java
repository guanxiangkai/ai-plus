package io.github.guanxiangkai.redis.plus.autoconfigure.properties;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Positive;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;
import org.springframework.validation.annotation.Validated;

@Getter
@Setter
@Validated
@ConfigurationProperties(prefix = "redis-plus.enhance")
public class RedisPlusEnhanceProperties {

    private boolean enabled = true;

    @Valid
    @NestedConfigurationProperty
    private BloomProperties bloom = new BloomProperties();

    @Getter
    @Setter
    public static class BloomProperties {
        private boolean enabled = true;
        @Positive
        private long expectedInsertions = 1_000_000;
        @DecimalMin("0.0")
        @DecimalMax("1.0")
        private double falsePositiveProbability = 0.01;
        @Positive
        private int version = 1;
    }
}
