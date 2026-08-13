package io.github.guanxiangkai.redis.plus.autoconfigure.lock;

import io.github.guanxiangkai.redis.plus.autoconfigure.properties.RedisPlusLockProperties;
import org.junit.jupiter.api.Test;
import org.redisson.config.Config;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class RedisPlusLockAutoConfigurationTest {

    @Test
    void redissonConfig_usesConfiguredRootPassword() {
        RedisPlusLockProperties properties = new RedisPlusLockProperties();
        properties.getRedisson().setAddress("redis://127.0.0.1:6380");
        properties.getRedisson().setPassword("secret");
        properties.getRedisson().setDatabase(2);
        properties.getRedisson().setConnectTimeout(Duration.ofSeconds(3));

        Config config = RedisPlusLockAutoConfiguration.redissonConfig(properties);

        assertThat(config.getPassword()).isEqualTo("secret");
        assertThat(config.isSingleConfig()).isTrue();
        assertThat(config.useSingleServer().getAddress()).isEqualTo("redis://127.0.0.1:6380");
        assertThat(config.useSingleServer().getDatabase()).isEqualTo(2);
        assertThat(config.useSingleServer().getConnectTimeout()).isEqualTo(3000);
    }

    @Test
    void redissonConfig_ignoresBlankPassword() {
        RedisPlusLockProperties properties = new RedisPlusLockProperties();
        properties.getRedisson().setPassword(" ");

        Config config = RedisPlusLockAutoConfiguration.redissonConfig(properties);

        assertThat(config.getPassword()).isNull();
    }
}
