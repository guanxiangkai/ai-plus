package io.github.guanxiangkai.redis.plus.autoconfigure.properties;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Getter
@Setter
@Validated
@ConfigurationProperties(prefix = "redis-plus.ratelimit")
public class RedisPlusRateLimitProperties {

    private boolean enabled = true;
    @NotBlank
    private String keyPrefix = "redis-plus:ratelimit:";
    @Positive
    private long tokenBucketRefillRate = 100;
}
