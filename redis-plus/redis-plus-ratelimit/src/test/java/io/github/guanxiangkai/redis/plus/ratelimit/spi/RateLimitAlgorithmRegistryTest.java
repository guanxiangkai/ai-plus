package io.github.guanxiangkai.redis.plus.ratelimit.spi;

import io.github.guanxiangkai.redis.plus.ratelimit.RateLimitConfig;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class RateLimitAlgorithmRegistryTest {

    @Test
    void getRequired_normalizesAlgorithmName() {
        RateLimitAlgorithm algorithm = algorithm("CUSTOM", true);
        RateLimitAlgorithmRegistry registry = new RateLimitAlgorithmRegistry(List.of(algorithm));

        assertSame(algorithm, registry.getRequired("custom"));
    }

    @Test
    void constructor_rejectsDuplicateNamesIgnoringCase() {
        assertThrows(IllegalStateException.class,
                () -> new RateLimitAlgorithmRegistry(List.of(
                        algorithm("CUSTOM", true),
                        algorithm("custom", false))));
    }

    @Test
    void named_delegatesToTypedLimiter() {
        RateLimitAlgorithm algorithm = RateLimitAlgorithms.named(
                "FIXED_WINDOW",
                RateLimitConfig.FixedWindow.class,
                (key, config) -> key.equals("user:1") && config.limit() == 10);

        boolean allowed = algorithm.tryAcquire("user:1",
                new RateLimitConfig.FixedWindow(10, Duration.ofSeconds(1)));

        assertTrue(allowed);
    }

    private static RateLimitAlgorithm algorithm(String name, boolean allowed) {
        return new RateLimitAlgorithm() {
            @Override
            public String algorithmName() {
                return name;
            }

            @Override
            public boolean tryAcquire(String key, RateLimitConfig config) {
                return allowed;
            }
        };
    }
}
