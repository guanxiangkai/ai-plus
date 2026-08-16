package io.github.guanxiangkai.redis.plus.datasource;

import io.lettuce.core.SslVerifyMode;
import io.lettuce.core.api.StatefulConnection;
import org.apache.commons.pool2.impl.GenericObjectPoolConfig;
import org.jspecify.annotations.Nullable;
import org.springframework.data.redis.connection.RedisPassword;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceClientConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.connection.lettuce.LettucePoolingClientConfiguration;

import java.time.Duration;

/**
 * Lettuce 单节点连接工厂构建器。
 *
 * <p>集中处理用户名、密码、客户端名、命令/连接/关闭超时、TLS 与可选连接池，避免应用在创建
 * 额外 Redis DB 连接时遗漏 Spring Data Redis 的关键连接属性。</p>
 *
 * <p>构建器只创建工厂对象，不主动调用生命周期方法。作为 Spring Bean 返回时由容器负责初始化
 * 和销毁；多数据源内部工厂由 Redis Plus 自动配置显式启动。</p>
 *
 * @author guanxiangkai
 * @since 1.1.0
 */
public final class LettuceConnectionFactoryBuilder {

    private final RedisStandaloneConfiguration standaloneConfiguration =
            new RedisStandaloneConfiguration("localhost", 6379);
    private Duration commandTimeout = Duration.ofSeconds(3);
    private Duration connectTimeout = Duration.ofSeconds(3);
    private Duration shutdownTimeout = Duration.ofMillis(100);
    private String clientName = "";
    private boolean ssl;
    private boolean startTls;
    private boolean verifyPeer = true;
    private PoolSettings poolSettings = PoolSettings.disabled();

    private LettuceConnectionFactoryBuilder() {
    }

    /**
     * 创建新的单节点连接构建器。
     *
     * @param host Redis 主机名或 IP
     * @param port Redis TCP 端口
     * @return 使用默认超时且未启用 TLS、连接池的构建器
     * @throws IllegalArgumentException 主机为空或端口超出有效范围时抛出
     */
    public static LettuceConnectionFactoryBuilder standalone(String host, int port) {
        if (host == null || host.isBlank()) {
            throw new IllegalArgumentException("Redis 主机不能为空");
        }
        if (port < 1 || port > 65_535) {
            throw new IllegalArgumentException("Redis 端口必须位于 1 到 65535 之间");
        }
        LettuceConnectionFactoryBuilder builder = new LettuceConnectionFactoryBuilder();
        builder.standaloneConfiguration.setHostName(host.strip());
        builder.standaloneConfiguration.setPort(port);
        return builder;
    }

    /**
     * 设置 Redis 数据库编号。
     *
     * @param database 非负数据库编号
     * @return 当前构建器
     */
    public LettuceConnectionFactoryBuilder database(int database) {
        if (database < 0) {
            throw new IllegalArgumentException("Redis 数据库编号不能小于 0");
        }
        standaloneConfiguration.setDatabase(database);
        return this;
    }

    /**
     * 设置可选用户名。
     *
     * @param username Redis ACL 用户名；空值表示不发送用户名
     * @return 当前构建器
     */
    public LettuceConnectionFactoryBuilder username(@Nullable String username) {
        standaloneConfiguration.setUsername(username == null || username.isBlank()
                ? null
                : username.strip());
        return this;
    }

    /**
     * 设置可选密码。
     *
     * @param password Redis 密码；空值表示不发送密码
     * @return 当前构建器
     */
    public LettuceConnectionFactoryBuilder password(@Nullable String password) {
        standaloneConfiguration.setPassword(password == null || password.isBlank()
                ? RedisPassword.none()
                : RedisPassword.of(password));
        return this;
    }

    /**
     * 设置命令超时。
     *
     * @param timeout 正数持续时间
     * @return 当前构建器
     */
    public LettuceConnectionFactoryBuilder commandTimeout(Duration timeout) {
        commandTimeout = positive(timeout, "Redis 命令超时");
        return this;
    }

    /**
     * 设置连接超时。
     *
     * @param timeout 正数持续时间
     * @return 当前构建器
     */
    public LettuceConnectionFactoryBuilder connectTimeout(Duration timeout) {
        connectTimeout = positive(timeout, "Redis 连接超时");
        return this;
    }

    /**
     * 设置关闭超时。
     *
     * @param timeout 非负持续时间，零表示不等待
     * @return 当前构建器
     */
    public LettuceConnectionFactoryBuilder shutdownTimeout(Duration timeout) {
        if (timeout == null || timeout.isNegative()) {
            throw new IllegalArgumentException("Redis 关闭超时不能为 null 或负数");
        }
        shutdownTimeout = timeout;
        return this;
    }

    /**
     * 设置可选客户端名。
     *
     * @param name Redis 客户端名；空值表示不设置
     * @return 当前构建器
     */
    public LettuceConnectionFactoryBuilder clientName(@Nullable String name) {
        clientName = name == null || name.isBlank() ? "" : name.strip();
        return this;
    }

    /**
     * 配置 TLS、StartTLS 与服务端证书校验。
     *
     * @param enabled 是否启用 TLS
     * @param startTls 是否先建立明文连接再升级为 TLS；仅在 TLS 启用时有效
     * @param verifyPeer 是否校验服务端证书与主机名
     * @return 当前构建器
     */
    public LettuceConnectionFactoryBuilder ssl(boolean enabled, boolean startTls, boolean verifyPeer) {
        this.ssl = enabled;
        this.startTls = enabled && startTls;
        this.verifyPeer = verifyPeer;
        return this;
    }

    /**
     * 启用有界 Commons Pool 连接池。
     *
     * @param maxActive 最大连接数
     * @param maxIdle 最大空闲连接数
     * @param minIdle 最小空闲连接数
     * @param maxWait 获取连接的最大等待时间
     * @return 当前构建器
     */
    public LettuceConnectionFactoryBuilder pool(
            int maxActive,
            int maxIdle,
            int minIdle,
            Duration maxWait) {
        if (maxActive < 1 || maxIdle < 0 || minIdle < 0 || minIdle > maxIdle || maxIdle > maxActive) {
            throw new IllegalArgumentException("Redis 连接池大小必须满足 0 <= minIdle <= maxIdle <= maxActive");
        }
        poolSettings = new PoolSettings(true, maxActive, maxIdle, minIdle,
                positive(maxWait, "Redis 连接池最大等待时间"));
        return this;
    }

    /**
     * 创建尚未启动的 Lettuce 连接工厂。
     *
     * @return 由 Spring 容器或调用方负责初始化和销毁的连接工厂
     */
    public LettuceConnectionFactory build() {
        LettuceClientConfiguration.LettuceClientConfigurationBuilder clientBuilder = poolSettings.enabled()
                ? poolingClientBuilder(poolSettings)
                : LettuceClientConfiguration.builder();
        configureClient(clientBuilder);
        return new LettuceConnectionFactory(standaloneConfiguration, clientBuilder.build());
    }

    private void configureClient(LettuceClientConfiguration.LettuceClientConfigurationBuilder builder) {
        builder.commandTimeout(commandTimeout)
                .shutdownTimeout(shutdownTimeout)
                .clientOptions(io.lettuce.core.ClientOptions.builder()
                        .socketOptions(io.lettuce.core.SocketOptions.builder()
                                .connectTimeout(connectTimeout)
                                .build())
                        .build());
        if (!clientName.isEmpty()) {
            builder.clientName(clientName);
        }
        if (ssl) {
            LettuceClientConfiguration.LettuceSslClientConfigurationBuilder sslBuilder = builder.useSsl();
            sslBuilder.verifyPeer(verifyPeer ? SslVerifyMode.FULL : SslVerifyMode.NONE);
            if (startTls) {
                sslBuilder.startTls();
            }
        }
    }

    private static LettucePoolingClientConfiguration.LettucePoolingClientConfigurationBuilder poolingClientBuilder(
            PoolSettings settings) {
        GenericObjectPoolConfig<StatefulConnection<?, ?>> pool = new GenericObjectPoolConfig<>();
        pool.setMaxTotal(settings.maxActive());
        pool.setMaxIdle(settings.maxIdle());
        pool.setMinIdle(settings.minIdle());
        pool.setMaxWait(settings.maxWait());
        return LettucePoolingClientConfiguration.builder().poolConfig(pool);
    }

    private static Duration positive(Duration value, String label) {
        if (value == null || value.isZero() || value.isNegative()) {
            throw new IllegalArgumentException(label + "必须大于 0");
        }
        return value;
    }

    private record PoolSettings(boolean enabled, int maxActive, int maxIdle, int minIdle, Duration maxWait) {

        private static PoolSettings disabled() {
            return new PoolSettings(false, 0, 0, 0, Duration.ofMillis(1));
        }
    }
}
