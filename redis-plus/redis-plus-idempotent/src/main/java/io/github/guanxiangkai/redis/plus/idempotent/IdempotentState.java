package io.github.guanxiangkai.redis.plus.idempotent;

import java.time.Instant;

/**
 * Structured idempotent operation state envelope.
 */
@SuppressWarnings("NullAway")
public record IdempotentState(Status status, String resultJson, String resultType, Instant createdAt) {

    public enum Status {
        PROCESSING, DONE, FAILED
    }

    public IdempotentState {
        if (status == null) {
            throw new IllegalArgumentException("status must not be null");
        }
        if (createdAt == null) {
            throw new IllegalArgumentException("createdAt must not be null");
        }
        if (resultJson != null && resultType == null) {
            throw new IllegalArgumentException("resultType must not be null when resultJson is set");
        }
        if (resultJson == null && resultType != null) {
            throw new IllegalArgumentException("resultJson must not be null when resultType is set");
        }
    }

    public static IdempotentState processing() {
        return new IdempotentState(Status.PROCESSING, null, null, Instant.now());
    }

    public static IdempotentState done(String resultJson, String resultType) {
        return new IdempotentState(Status.DONE, resultJson, resultType, Instant.now());
    }

    public static IdempotentState failed() {
        return new IdempotentState(Status.FAILED, null, null, Instant.now());
    }

    public Status getStatus() {
        return status;
    }

    public String getResultJson() {
        return resultJson;
    }

    public String getResultType() {
        return resultType;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
