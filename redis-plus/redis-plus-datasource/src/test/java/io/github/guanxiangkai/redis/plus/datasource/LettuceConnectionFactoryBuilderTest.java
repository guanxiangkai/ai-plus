package io.github.guanxiangkai.redis.plus.datasource;

import io.lettuce.core.SslVerifyMode;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.connection.lettuce.LettucePoolingClientConfiguration;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class LettuceConnectionFactoryBuilderTest {

    @Test
    void shouldBuildCompleteStandaloneConfiguration() {
        LettuceConnectionFactory factory = LettuceConnectionFactoryBuilder
                .standalone(" redis.example.com ", 16_379)
                .database(3)
                .username(" app ")
                .password("secret")
                .clientName(" platform-system ")
                .commandTimeout(Duration.ofSeconds(4))
                .connectTimeout(Duration.ofSeconds(2))
                .shutdownTimeout(Duration.ofMillis(500))
                .ssl(true, true, false)
                .build();

        assertThat(factory.getStandaloneConfiguration().getHostName()).isEqualTo("redis.example.com");
        assertThat(factory.getStandaloneConfiguration().getPort()).isEqualTo(16_379);
        assertThat(factory.getStandaloneConfiguration().getDatabase()).isEqualTo(3);
        assertThat(factory.getStandaloneConfiguration().getUsername()).isEqualTo("app");
        assertThat(factory.getStandaloneConfiguration().getPassword()
                .map(characters -> new String(characters)))
                .contains("secret");
        assertThat(factory.getClientConfiguration().getClientName()).contains("platform-system");
        assertThat(factory.getClientConfiguration().getCommandTimeout()).isEqualTo(Duration.ofSeconds(4));
        assertThat(factory.getClientConfiguration().getShutdownTimeout()).isEqualTo(Duration.ofMillis(500));
        assertThat(factory.getClientConfiguration().getClientOptions()).isPresent();
        assertThat(factory.getClientConfiguration().getClientOptions().orElseThrow()
                .getSocketOptions().getConnectTimeout()).isEqualTo(Duration.ofSeconds(2));
        assertThat(factory.getClientConfiguration().isUseSsl()).isTrue();
        assertThat(factory.getClientConfiguration().isStartTls()).isTrue();
        assertThat(factory.getClientConfiguration().getVerifyMode()).isEqualTo(SslVerifyMode.NONE);
    }

    @Test
    void shouldBuildValidatedPoolConfiguration() {
        LettuceConnectionFactory factory = LettuceConnectionFactoryBuilder
                .standalone("localhost", 6379)
                .pool(16, 8, 2, Duration.ofSeconds(1))
                .build();

        assertThat(factory.getClientConfiguration())
                .isInstanceOf(LettucePoolingClientConfiguration.class);
        LettucePoolingClientConfiguration clientConfiguration =
                (LettucePoolingClientConfiguration) factory.getClientConfiguration();
        assertThat(clientConfiguration.getPoolConfig().getMaxTotal()).isEqualTo(16);
        assertThat(clientConfiguration.getPoolConfig().getMaxIdle()).isEqualTo(8);
        assertThat(clientConfiguration.getPoolConfig().getMinIdle()).isEqualTo(2);
    }

    @Test
    void shouldClearOptionalCredentialsAndClientName() {
        LettuceConnectionFactory factory = LettuceConnectionFactoryBuilder
                .standalone("localhost", 6379)
                .username("app")
                .password("secret")
                .clientName("client")
                .username(" ")
                .password(null)
                .clientName(null)
                .build();

        assertThat(factory.getStandaloneConfiguration().getUsername()).isNull();
        assertThat(factory.getStandaloneConfiguration().getPassword().isPresent()).isFalse();
        assertThat(factory.getClientConfiguration().getClientName()).isEmpty();
    }

    @Test
    void shouldFollowSpringConnectionFactoryLifecycle() throws Exception {
        LettuceConnectionFactory factory = LettuceConnectionFactoryBuilder
                .standalone("localhost", 6379)
                .build();

        assertThat(factory.isRunning()).isFalse();
        factory.afterPropertiesSet();
        try {
            assertThat(factory.isRunning()).isTrue();
        } finally {
            factory.destroy();
        }
        assertThat(factory.isRunning()).isFalse();
    }

    @Test
    void shouldRejectInvalidEndpointAndPoolSettings() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> LettuceConnectionFactoryBuilder.standalone(" ", 6379));
        assertThatIllegalArgumentException()
                .isThrownBy(() -> LettuceConnectionFactoryBuilder.standalone("localhost", 0));
        assertThatIllegalArgumentException()
                .isThrownBy(() -> LettuceConnectionFactoryBuilder.standalone("localhost", 6379)
                        .pool(4, 5, 1, Duration.ofSeconds(1)));
    }
}
