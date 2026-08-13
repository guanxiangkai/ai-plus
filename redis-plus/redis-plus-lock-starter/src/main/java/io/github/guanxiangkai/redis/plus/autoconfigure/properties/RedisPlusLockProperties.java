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
@ConfigurationProperties(prefix = "redis-plus.lock")
public class RedisPlusLockProperties {

    private boolean enabled = true;
    @NotBlank
    private String keyPrefix = "redis-plus:lock:";
    @NotNull
    private Duration defaultLease = Duration.ofSeconds(30);
    @NotNull
    private Duration defaultWait = Duration.ofSeconds(5);
    @NotNull
    private Redisson redisson = new Redisson();

    @Getter
    @Setter
    public static class Redisson {

        @NotBlank
        private String address = "redis://localhost:6379";
        private String password = "";
        private int database = 0;
        @NotNull
        private Duration connectTimeout = Duration.ofSeconds(10);
    }
}
