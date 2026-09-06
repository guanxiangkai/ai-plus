package io.github.guanxiangkai.redis.plus.autoconfigure.properties;

import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
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
    @Valid
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
        /**
         * Redis ACL 认证用户名；原样传递给服务端，空字符串表示不发送用户名。
         */
        private String username = "";
        /**
         * Redis 客户端名；原样传递给 {@code CLIENT SETNAME}，空字符串表示不设置。
         */
        private String clientName = "";
        @Min(0)
        private int database = 0;
        @NotNull
        private Duration timeout = Duration.ofSeconds(3);
        @NotNull
        private Duration connectTimeout = Duration.ofSeconds(3);
        /**
         * Lettuce 客户端关闭超时；单位为 {@link Duration}，零表示不等待。
         */
        @NotNull
        private Duration shutdownTimeout = Duration.ofMillis(100);

        /**
         * TLS 连接选项；启用后固定校验服务端证书和主机名。
         */
        @Valid
        @NotNull
        @NestedConfigurationProperty
        private SslProperties ssl = new SslProperties();

        @Valid
        @NotNull
        @NestedConfigurationProperty
        private PoolProperties pool = new PoolProperties();

        /**
         * 校验 Lettuce 生命周期相关的超时边界。
         *
         * @return 命令和连接超时为正数、关闭超时为非负数时返回 {@code true}
         */
        @AssertTrue(message = "Redis 命令和连接超时必须大于 0，关闭超时不能为负数")
        public boolean isTimeoutConfigurationValid() {
            return isPositive(timeout) && isPositive(connectTimeout) && isNonNegative(shutdownTimeout);
        }
    }

    /**
     * Redis 数据源的 TLS 连接选项。
     *
     * <p>启用 TLS 时始终执行服务端证书和主机名校验。
     */
    @Getter
    @Setter
    public static class SslProperties {
        /**
         * 是否启用 TLS；启用后始终校验服务端证书和主机名。
         */
        private boolean enabled;
        /**
         * 是否在 TLS 已启用时使用 StartTLS。
         */
        private boolean startTls;

        /**
         * StartTLS 只在 TLS 已启用时有效。
         *
         * @return TLS 未启用时未设置 StartTLS，或 TLS 已启用时返回 {@code true}
         */
        @AssertTrue(message = "启用 StartTLS 前必须先启用 TLS")
        public boolean isStartTlsConfigurationValid() {
            return enabled || !startTls;
        }
    }

    @Getter
    @Setter
    public static class PoolProperties {
        private boolean enabled = true;
        private int maxActive = 16;
        private int maxIdle = 8;
        private int minIdle = 2;
        private Duration maxWait = Duration.ofSeconds(3);

        /**
         * 校验 Commons Pool 连接数量与等待时间边界。
         *
         * @return 未启用连接池，或连接池上下界和等待时间有效时返回 {@code true}
         */
        @AssertTrue(message = "Redis 连接池必须满足 0 <= minIdle <= maxIdle <= maxActive，且最大等待时间不能为空")
        public boolean isPoolConfigurationValid() {
            return !enabled || (maxActive > 0 && maxIdle > 0 && minIdle >= 0
                    && minIdle <= maxIdle && maxIdle <= maxActive && maxWait != null);
        }
    }

    private static boolean isPositive(Duration value) {
        return value != null && !value.isZero() && !value.isNegative();
    }

    private static boolean isNonNegative(Duration value) {
        return value != null && !value.isNegative();
    }
}
