package io.github.guanxiangkai.redis.plus.autoconfigure.properties;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

@Getter
@Setter
@Validated
@ConfigurationProperties(prefix = "redis-plus.idempotent")
public class RedisPlusIdempotentProperties {

    private boolean enabled = true;
    @NotBlank
    private String keyPrefix = "redis-plus:idempotent:";
    @NotNull
    private Duration processingTimeout = Duration.ofMinutes(10);
}
