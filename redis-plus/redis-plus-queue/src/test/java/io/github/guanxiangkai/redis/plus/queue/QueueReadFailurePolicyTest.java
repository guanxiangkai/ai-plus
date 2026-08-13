package io.github.guanxiangkai.redis.plus.queue;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QueueReadFailurePolicyTest {

    @Test
    void delayShouldGrowExponentiallyAndStopAtMaximumWithoutJitter() {
        QueueReadFailurePolicy policy = new QueueReadFailurePolicy(
                Duration.ofMillis(100), Duration.ofMillis(800), 0D);

        assertEquals(Duration.ofMillis(100), policy.delayFor(1));
        assertEquals(Duration.ofMillis(200), policy.delayFor(2));
        assertEquals(Duration.ofMillis(400), policy.delayFor(3));
        assertEquals(Duration.ofMillis(800), policy.delayFor(4));
        assertEquals(Duration.ofMillis(800), policy.delayFor(20));
    }

    @Test
    void jitteredDelayShouldRemainPositiveAndBounded() {
        QueueReadFailurePolicy policy = new QueueReadFailurePolicy(
                Duration.ofMillis(100), Duration.ofMillis(800), 0.2D);

        for (int index = 0; index < 100; index++) {
            Duration delay = policy.delayFor(4);
            assertTrue(!delay.isZero() && !delay.isNegative());
            assertTrue(delay.compareTo(Duration.ofMillis(800)) <= 0);
        }
    }

    @Test
    void invalidPolicyShouldFailFast() {
        assertThrows(IllegalArgumentException.class,
                () -> new QueueReadFailurePolicy(Duration.ZERO, Duration.ofSeconds(1), 0.2D));
        assertThrows(IllegalArgumentException.class,
                () -> new QueueReadFailurePolicy(Duration.ofSeconds(2), Duration.ofSeconds(1), 0.2D));
        assertThrows(IllegalArgumentException.class,
                () -> new QueueReadFailurePolicy(Duration.ofSeconds(1), Duration.ofSeconds(2), 1.1D));
        assertThrows(IllegalArgumentException.class, () -> QueueReadFailurePolicy.defaults().delayFor(0));
    }
}
