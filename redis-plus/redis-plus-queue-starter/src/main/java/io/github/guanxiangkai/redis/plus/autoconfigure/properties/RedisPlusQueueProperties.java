package io.github.guanxiangkai.redis.plus.autoconfigure.properties;

import io.github.guanxiangkai.redis.plus.queue.QueueReadFailurePolicy;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

/**
 * Redis 队列自动装配配置。
 *
 * <p>定义队列键前缀、消费组、重试、轮询、Stream 回收及读取故障退避的默认边界。</p>
 *
 * @author guanxiangkai
 * @since 1.0.0
 */
@Getter
@Setter
@Validated
@ConfigurationProperties(prefix = "redis-plus.queue")
public class RedisPlusQueueProperties {

    private boolean enabled = true;
    @NotBlank
    private String keyPrefix = "redis-plus:queue:";
    @NotBlank
    private String defaultConsumerGroup = "redis-plus-consumers";
    @Min(0)
    private int maxRetryAttempts = 3;
    @Positive
    private int batchSize = 10;
    @NotNull
    private Duration pollTimeout = Duration.ofSeconds(2);
    private boolean reclaimOnStart = true;
    @NotNull
    private Duration pendingReclaimIdleTime = Duration.ofMinutes(5);
    @Min(0)
    private long maxStreamLength = 0;
    @Valid
    @NotNull
    private ReadFailure readFailure = new ReadFailure();

    /**
     * Redis 读取异常后的指数退避配置。
     *
     * @author guanxiangkai
     * @since 1.0.0
     */
    @Getter
    @Setter
    public static class ReadFailure {

        @NotNull
        private Duration initialBackoff = QueueReadFailurePolicy.defaults().initialBackoff();
        @NotNull
        private Duration maxBackoff = QueueReadFailurePolicy.defaults().maxBackoff();
        @DecimalMin("0.0")
        @DecimalMax("1.0")
        private double jitterFactor = QueueReadFailurePolicy.defaults().jitterFactor();

        /** 转换为队列核心模块的不可变策略。 */
        public QueueReadFailurePolicy toPolicy() {
            return new QueueReadFailurePolicy(initialBackoff, maxBackoff, jitterFactor);
        }
    }
}
