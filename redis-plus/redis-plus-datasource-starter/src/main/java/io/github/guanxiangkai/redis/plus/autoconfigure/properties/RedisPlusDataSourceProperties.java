package io.github.guanxiangkai.redis.plus.autoconfigure.properties;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

@Getter
@Setter
@Validated
@ConfigurationProperties(prefix = "redis-plus.datasource")
public class RedisPlusDataSourceProperties {

    private boolean enabled = true;
    private Map<String, RedisSourceProperties> sources = new LinkedHashMap<>();
    @NotBlank
    private String primary = "primary";
    private boolean strict = false;

    @Getter
    @Setter
    public static class RedisSourceProperties {
        @NotBlank
        private String host = "localhost";
        @Positive
        private int port = 6379;
        private String password = "";
        @Min(0)
        private int database = 0;
        @NotNull
        private Duration timeout = Duration.ofSeconds(3);
        @NotNull
        private Duration connectTimeout = Duration.ofSeconds(3);

        @Valid
        @NestedConfigurationProperty
        private PoolProperties pool = new PoolProperties();
    }

    @Getter
    @Setter
    public static class PoolProperties {
        private boolean enabled = true;
        @Positive
        private int maxActive = 16;
        @Positive
        private int maxIdle = 8;
        @Min(0)
        private int minIdle = 2;
        @NotNull
        private Duration maxWait = Duration.ofSeconds(3);
    }
}
