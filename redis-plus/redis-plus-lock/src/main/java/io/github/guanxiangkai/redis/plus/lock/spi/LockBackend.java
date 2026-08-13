package io.github.guanxiangkai.redis.plus.lock.spi;

import io.github.guanxiangkai.redis.plus.lock.DistributedLock;
import io.github.guanxiangkai.redis.plus.lock.DistributedReadWriteLock;
import io.github.guanxiangkai.redis.plus.lock.LockDefinition;

/**
 * Backend adapter used by {@code RedisLockFactory}.
 */
public interface LockBackend {

    DistributedLock getLock(LockDefinition definition);

    DistributedReadWriteLock getReadWriteLock(LockDefinition definition);

    default void close() {
    }
}
