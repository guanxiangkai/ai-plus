package io.github.guanxiangkai.web.plus.web.crypto;

import org.springframework.util.unit.DataSize;

import java.util.Objects;

/**
 * API 加解密聚合与执行资源策略。
 *
 * @param maxRequestBodySize 可聚合的最大加密请求体
 * @param maxResponseBodySize 可聚合的最大 JSON 响应体
 * @param maxQueryEnvelopeLength 最大加密查询信封字符数
 * @param workerCount 加解密工作线程上限
 * @param taskQueueCapacity 等待执行的加解密任务上限
 * @author guanxiangkai
 * @since 4.0.0
 */
public record ApiCryptoRuntimePolicy(
        DataSize maxRequestBodySize,
        DataSize maxResponseBodySize,
        int maxQueryEnvelopeLength,
        int workerCount,
        int taskQueueCapacity
) {

    private static final DataSize DEFAULT_MAX_REQUEST_BODY_SIZE = DataSize.ofMegabytes(4);
    private static final DataSize DEFAULT_MAX_RESPONSE_BODY_SIZE = DataSize.ofMegabytes(8);
    private static final DataSize MAX_BODY_SIZE = DataSize.ofMegabytes(64);
    private static final int DEFAULT_MAX_QUERY_ENVELOPE_LENGTH = 16_384;
    private static final int MAX_QUERY_ENVELOPE_LENGTH = 65_536;
    private static final int DEFAULT_TASK_QUEUE_CAPACITY = 1_024;
    private static final int MAX_TASK_QUEUE_CAPACITY = 100_000;
    private static final int MAX_WORKER_COUNT = 64;

    public ApiCryptoRuntimePolicy {
        Objects.requireNonNull(maxRequestBodySize, "maxRequestBodySize 不能为空");
        Objects.requireNonNull(maxResponseBodySize, "maxResponseBodySize 不能为空");
        validateBodySize(maxRequestBodySize, "maxRequestBodySize");
        validateBodySize(maxResponseBodySize, "maxResponseBodySize");
        requireRange(maxQueryEnvelopeLength, 1, MAX_QUERY_ENVELOPE_LENGTH, "maxQueryEnvelopeLength");
        requireRange(workerCount, 1, MAX_WORKER_COUNT, "workerCount");
        requireRange(taskQueueCapacity, 1, MAX_TASK_QUEUE_CAPACITY, "taskQueueCapacity");
    }

    /** 返回适用于普通 JSON 接口的安全默认策略。 */
    public static ApiCryptoRuntimePolicy defaults() {
        int processors = Runtime.getRuntime().availableProcessors();
        return new ApiCryptoRuntimePolicy(
                DEFAULT_MAX_REQUEST_BODY_SIZE,
                DEFAULT_MAX_RESPONSE_BODY_SIZE,
                DEFAULT_MAX_QUERY_ENVELOPE_LENGTH,
                Math.max(2, Math.min(processors, MAX_WORKER_COUNT)),
                DEFAULT_TASK_QUEUE_CAPACITY);
    }

    /** 返回适用于 {@code DataBufferUtils.join} 的请求字节上限。 */
    public int maxRequestBodyBytes() {
        return Math.toIntExact(maxRequestBodySize.toBytes());
    }

    /** 返回适用于 {@code DataBufferUtils.join} 的响应字节上限。 */
    public int maxResponseBodyBytes() {
        return Math.toIntExact(maxResponseBodySize.toBytes());
    }

    private static void validateBodySize(DataSize value, String fieldName) {
        long bytes = value.toBytes();
        if (bytes <= 0L || bytes > MAX_BODY_SIZE.toBytes()) {
            throw new IllegalArgumentException(fieldName + " 必须大于 0 且不超过 " + MAX_BODY_SIZE);
        }
    }

    private static void requireRange(int value, int minimum, int maximum, String fieldName) {
        if (value < minimum || value > maximum) {
            throw new IllegalArgumentException(
                    fieldName + " 必须位于 " + minimum + " 到 " + maximum + " 之间");
        }
    }
}
