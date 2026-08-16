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

/**
 * Redis Plus 多数据源连接配置。
 *
 * <p>每个命名数据源都使用独立的 Lettuce 连接工厂；{@link #primary} 指定默认路由目标，
 * {@link #strict} 决定未找到路由键时是否禁止回退到默认数据源。</p>
 */
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

    /** 单个命名 Redis 数据源的连接、超时、TLS 与连接池配置。 */
    @Getter
    @Setter
    public static class RedisSourceProperties {
        @NotBlank
        private String host = "localhost";
        @Positive
        private int port = 6379;
        private String username = "";
        private String password = "";
        private String clientName = "";
        @Min(0)
        private int database = 0;
        @NotNull
        private Duration timeout = Duration.ofSeconds(3);
        @NotNull
        private Duration connectTimeout = Duration.ofSeconds(3);
        @NotNull
        private Duration shutdownTimeout = Duration.ofMillis(100);

        @Valid
        @NestedConfigurationProperty
        private SslProperties ssl = new SslProperties();

        @Valid
        @NestedConfigurationProperty
        private PoolProperties pool = new PoolProperties();
    }

    /** Redis TLS 连接配置。 */
    @Getter
    @Setter
    public static class SslProperties {
        private boolean enabled = false;
        private boolean startTls = false;
        private boolean verifyPeer = true;
    }

    /** Lettuce Commons Pool 连接池配置。 */
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
