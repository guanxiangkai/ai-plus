package io.github.guanxiangkai.redis.plus.autoconfigure.datasource;

import io.github.guanxiangkai.redis.plus.datasource.MultiRedisConnectionFactory;
import io.lettuce.core.SslVerifyMode;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.connection.lettuce.LettucePoolingClientConfiguration;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class RedisPlusDataSourceAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(RedisPlusDataSourceAutoConfiguration.class))
            .withPropertyValues(
                    "redis-plus.datasource.primary=primary",
                    "redis-plus.datasource.sources.primary.host=redis.example.com",
                    "redis-plus.datasource.sources.primary.port=6380");

    @Test
    void pooledSource_appliesConnectionAndAclConfiguration() {
        contextRunner
                .withPropertyValues(
                        "redis-plus.datasource.sources.primary.username= application ",
                        "redis-plus.datasource.sources.primary.password=secret",
                        "redis-plus.datasource.sources.primary.client-name= orders-api ",
                        "redis-plus.datasource.sources.primary.timeout=4s",
                        "redis-plus.datasource.sources.primary.connect-timeout=2s",
                        "redis-plus.datasource.sources.primary.shutdown-timeout=500ms",
                        "redis-plus.datasource.sources.primary.pool.max-active=12",
                        "redis-plus.datasource.sources.primary.pool.max-idle=8",
                        "redis-plus.datasource.sources.primary.pool.min-idle=2",
                        "redis-plus.datasource.sources.primary.pool.max-wait=1s")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    LettuceConnectionFactory factory = configuredFactory(context);

                    assertThat(factory.getStandaloneConfiguration().getUsername()).isEqualTo(" application ");
                    assertThat(factory.getStandaloneConfiguration().getPassword()
                            .map(characters -> new String(characters))).contains("secret");
                    assertThat(factory.getClientConfiguration().getClientName()).contains(" orders-api ");
                    assertThat(factory.getClientConfiguration().getCommandTimeout()).isEqualTo(Duration.ofSeconds(4));
                    assertThat(factory.getClientConfiguration().getShutdownTimeout()).isEqualTo(Duration.ofMillis(500));
                    assertThat(factory.getClientConfiguration().getClientOptions()).isPresent();
                    assertThat(factory.getClientConfiguration().getClientOptions().orElseThrow()
                            .getSocketOptions().getConnectTimeout()).isEqualTo(Duration.ofSeconds(2));
                    assertThat(factory.getClientConfiguration().getClientOptions().orElseThrow()
                            .getTimeoutOptions().isTimeoutCommands()).isTrue();
                    assertThat(factory.getClientConfiguration())
                            .isInstanceOf(LettucePoolingClientConfiguration.class);
                    LettucePoolingClientConfiguration clientConfiguration =
                            (LettucePoolingClientConfiguration) factory.getClientConfiguration();
                    assertThat(clientConfiguration.getPoolConfig().getMaxTotal()).isEqualTo(12);
                    assertThat(clientConfiguration.getPoolConfig().getMaxIdle()).isEqualTo(8);
                    assertThat(clientConfiguration.getPoolConfig().getMinIdle()).isEqualTo(2);
                });
    }

    @Test
    void nonPooledTlsSource_verifiesPeerByDefault() {
        contextRunner
                .withPropertyValues(
                        "redis-plus.datasource.sources.primary.pool.enabled=false",
                        "redis-plus.datasource.sources.primary.ssl.enabled=true")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    LettuceConnectionFactory factory = configuredFactory(context);

                    assertThat(factory.getClientConfiguration())
                            .isNotInstanceOf(LettucePoolingClientConfiguration.class);
                    assertThat(factory.getClientConfiguration().isUseSsl()).isTrue();
                    assertThat(factory.getClientConfiguration().isStartTls()).isFalse();
                    assertThat(factory.getClientConfiguration().getVerifyMode()).isEqualTo(SslVerifyMode.FULL);
                });
    }

    @Test
    void invalidConnectionProperties_preventAutoConfiguration() {
        contextRunner
                .withPropertyValues("redis-plus.datasource.sources.primary.connect-timeout=-1s")
                .run(context -> assertThat(context).hasFailed());

        contextRunner
                .withPropertyValues(
                        "redis-plus.datasource.sources.primary.pool.max-active=-3",
                        "redis-plus.datasource.sources.primary.pool.max-idle=-2",
                        "redis-plus.datasource.sources.primary.pool.min-idle=-1")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void disabledPool_doesNotValidateUnusedPoolLimits() {
        contextRunner
                .withPropertyValues(
                        "redis-plus.datasource.sources.primary.pool.enabled=false",
                        "redis-plus.datasource.sources.primary.pool.max-active=-3",
                        "redis-plus.datasource.sources.primary.pool.max-idle=-2",
                        "redis-plus.datasource.sources.primary.pool.min-idle=-1",
                        "redis-plus.datasource.sources.primary.pool.max-wait=-1s")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(configuredFactory(context).getClientConfiguration())
                            .isNotInstanceOf(LettucePoolingClientConfiguration.class);
                });
    }

    @Test
    void pooledSource_preservesCommonsPoolZeroWaitSemantics() {
        contextRunner.withPropertyValues("redis-plus.datasource.sources.primary.pool.max-wait=0s")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    LettucePoolingClientConfiguration client = (LettucePoolingClientConfiguration)
                            configuredFactory(context).getClientConfiguration();
                    assertThat(client.getPoolConfig().getMaxWaitDuration()).isEqualTo(Duration.ZERO);
                });
    }

    @Test
    void zeroShutdownTimeout_alsoDisablesQuietPeriod() {
        contextRunner.withPropertyValues("redis-plus.datasource.sources.primary.shutdown-timeout=0s")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(configuredFactory(context).getClientConfiguration().getShutdownTimeout()).isZero();
                    assertThat(configuredFactory(context).getClientConfiguration().getShutdownQuietPeriod()).isZero();
                });
    }

    private static LettuceConnectionFactory configuredFactory(
            org.springframework.boot.test.context.assertj.AssertableApplicationContext context) {
        MultiRedisConnectionFactory routingFactory = context.getBean(MultiRedisConnectionFactory.class);
        assertThat(routingFactory.determine()).isInstanceOf(LettuceConnectionFactory.class);
        return (LettuceConnectionFactory) routingFactory.determine();
    }
}
