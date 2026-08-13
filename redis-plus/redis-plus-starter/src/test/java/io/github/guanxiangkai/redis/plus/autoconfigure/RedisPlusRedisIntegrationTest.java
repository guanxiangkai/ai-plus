package io.github.guanxiangkai.redis.plus.autoconfigure;

import io.github.guanxiangkai.redis.plus.core.script.DefaultRedisScriptExecutor;
import io.github.guanxiangkai.redis.plus.idempotent.IdempotentState;
import io.github.guanxiangkai.redis.plus.idempotent.impl.RedisIdempotentStateStore;
import io.github.guanxiangkai.redis.plus.lock.DistributedLock;
import io.github.guanxiangkai.redis.plus.lock.LockDefinition;
import io.github.guanxiangkai.redis.plus.lock.impl.RedissonLockBackend;
import io.github.guanxiangkai.redis.plus.lock.impl.RedisLockFactory;
import io.github.guanxiangkai.redis.plus.cache.serializer.JacksonValueSerializer;
import io.github.guanxiangkai.redis.plus.core.key.DefaultKeyNamingStrategy;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Assumptions;
import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers(disabledWithoutDocker = true)
class RedisPlusRedisIntegrationTest {

    @Container
    static final GenericContainer<?> redis = new GenericContainer<>(DockerImageName.parse("redis:7.4-alpine"))
            .withExposedPorts(6379);

    @BeforeAll
    static void requireDocker() {
        Assumptions.assumeTrue(DockerClientFactory.instance().isDockerAvailable(), "Docker is not available");
    }

    @Test
    void redisScriptExecutor_executesLuaAgainstRedis() {
        StringRedisTemplate template = redisTemplate();
        DefaultRedisScriptExecutor executor = new DefaultRedisScriptExecutor(template);

        Long result = executor.execute("return redis.call('incr', KEYS[1])", Long.class,
                java.util.List.of("it:script:counter"));

        assertThat(result).isEqualTo(1L);
    }

    @Test
    void idempotentStateStore_tryAcquireIsAtomic() {
        StringRedisTemplate template = redisTemplate();
        var serializer = new JacksonValueSerializer(new ObjectMapper());
        var store = new RedisIdempotentStateStore(template, new DefaultRedisScriptExecutor(template), serializer);

        Optional<String> first = store.tryAcquire("it:idempotent:1", Duration.ofSeconds(30));
        Optional<String> second = store.tryAcquire("it:idempotent:1", Duration.ofSeconds(30));

        assertThat(first).isEmpty();
        assertThat(second).isPresent();
        assertThat(serializer.deserialize(second.get(), IdempotentState.class).getStatus())
                .isEqualTo(IdempotentState.Status.PROCESSING);
    }

    @Test
    void redisLockFactory_enforcesMutualExclusion() {
        RedissonClient redissonClient = redissonClient();
        try {
            var backend = new RedissonLockBackend(redissonClient);
            var factory = new RedisLockFactory("it:lock:", new DefaultKeyNamingStrategy(), backend);
            DistributedLock lock = factory.getLock(LockDefinition.of("order:1"));

            assertThat(lock.tryLock(0, 5, TimeUnit.SECONDS)).isTrue();
            assertThat(factory.getLock(LockDefinition.of("order:1")).tryLock(0, 5, TimeUnit.SECONDS)).isTrue();

            lock.unlock();
            lock.unlock();
            factory.destroy();
        } finally {
            redissonClient.shutdown();
        }
    }

    private static StringRedisTemplate redisTemplate() {
        LettuceConnectionFactory factory = new LettuceConnectionFactory(redis.getHost(), redis.getMappedPort(6379));
        factory.afterPropertiesSet();
        return new StringRedisTemplate(factory);
    }

    private static RedissonClient redissonClient() {
        Config config = new Config();
        config.useSingleServer().setAddress("redis://" + redis.getHost() + ":" + redis.getMappedPort(6379));
        return Redisson.create(config);
    }
}
