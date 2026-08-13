package io.github.guanxiangkai.redis.plus.queue;

import java.time.Duration;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 队列读取故障的指数退避策略。
 *
 * @param initialBackoff 首次失败等待时间
 * @param maxBackoff 最大等待时间
 * @param jitterFactor 抖动比例，取值范围为 {@code 0.0-1.0}
 * @author guanxiangkai
 * @since 1.0.2
 */
public record QueueReadFailurePolicy(
        Duration initialBackoff,
        Duration maxBackoff,
        double jitterFactor
) {

    private static final Duration DEFAULT_INITIAL_BACKOFF = Duration.ofMillis(250);
    private static final Duration DEFAULT_MAX_BACKOFF = Duration.ofSeconds(30);
    private static final double DEFAULT_JITTER_FACTOR = 0.2D;
    private static final int MAX_EXPONENT = 30;

    public QueueReadFailurePolicy {
        requirePositive(initialBackoff, "initialBackoff");
        requirePositive(maxBackoff, "maxBackoff");
        requireNanosecondRange(initialBackoff, "initialBackoff");
        requireNanosecondRange(maxBackoff, "maxBackoff");
        if (maxBackoff.compareTo(initialBackoff) < 0) {
            throw new IllegalArgumentException("maxBackoff 不能小于 initialBackoff");
        }
        if (!Double.isFinite(jitterFactor) || jitterFactor < 0D || jitterFactor > 1D) {
            throw new IllegalArgumentException("jitterFactor 必须位于 0.0 到 1.0 之间");
        }
    }

    /** 返回默认退避策略。 */
    public static QueueReadFailurePolicy defaults() {
        return new QueueReadFailurePolicy(
                DEFAULT_INITIAL_BACKOFF,
                DEFAULT_MAX_BACKOFF,
                DEFAULT_JITTER_FACTOR);
    }

    /**
     * 按连续失败次数计算下一次等待时间。
     *
     * @param consecutiveFailures 连续失败次数，从 {@code 1} 开始
     * @return 不超过 {@link #maxBackoff()} 的抖动退避时间
     */
    public Duration delayFor(int consecutiveFailures) {
        if (consecutiveFailures <= 0) {
            throw new IllegalArgumentException("consecutiveFailures 必须大于 0");
        }
        int exponent = Math.min(consecutiveFailures - 1, MAX_EXPONENT);
        double expandedNanos = Math.scalb((double) initialBackoff.toNanos(), exponent);
        long baseNanos = Math.min(maxBackoff.toNanos(), Math.round(expandedNanos));
        double jitter = jitterFactor == 0D
                ? 0D
                : ThreadLocalRandom.current().nextDouble(-jitterFactor, jitterFactor);
        long jitteredNanos = Math.round(baseNanos * (1D + jitter));
        return Duration.ofNanos(Math.max(1L, Math.min(maxBackoff.toNanos(), jitteredNanos)));
    }

    private static void requirePositive(Duration value, String fieldName) {
        if (value == null || value.isZero() || value.isNegative()) {
            throw new IllegalArgumentException(fieldName + " 必须大于 0");
        }
    }

    private static void requireNanosecondRange(Duration value, String fieldName) {
        try {
            if (value.toNanos() <= 0L) {
                throw new IllegalArgumentException(fieldName + " 必须至少为 1 纳秒");
            }
        } catch (ArithmeticException e) {
            throw new IllegalArgumentException(fieldName + " 超出可计算范围", e);
        }
    }
}
