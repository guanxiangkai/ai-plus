package io.github.guanxiangkai.redis.plus.lock.impl;

import io.github.guanxiangkai.redis.plus.core.key.DefaultKeyNamingStrategy;
import io.github.guanxiangkai.redis.plus.lock.DistributedLock;
import io.github.guanxiangkai.redis.plus.lock.DistributedReadWriteLock;
import io.github.guanxiangkai.redis.plus.lock.LockDefinition;
import io.github.guanxiangkai.redis.plus.lock.spi.LockBackend;
import org.junit.jupiter.api.Test;

import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SuppressWarnings("NullAway")
class RedisLockFactoryTest {

    @Test
    void getLock_rekeysDefinitionBeforeDelegatingToBackend() {
        CapturingLockBackend backend = new CapturingLockBackend();
        RedisLockFactory factory = new RedisLockFactory("test:", new DefaultKeyNamingStrategy(), backend);

        factory.getLock("order:1");

        assertEquals("test:order:1", backend.lockDefinition.name());
    }

    @Test
    void getReadWriteLock_rekeysDefinitionBeforeDelegatingToBackend() {
        CapturingLockBackend backend = new CapturingLockBackend();
        RedisLockFactory factory = new RedisLockFactory("test:", new DefaultKeyNamingStrategy(), backend);

        factory.getReadWriteLock("inventory:1");

        assertEquals("test:inventory:1", backend.readWriteDefinition.name());
    }

    private static final class CapturingLockBackend implements LockBackend {
        private LockDefinition lockDefinition;
        private LockDefinition readWriteDefinition;

        @Override
        public DistributedLock getLock(LockDefinition definition) {
            this.lockDefinition = definition;
            return new NoopLock();
        }

        @Override
        public DistributedReadWriteLock getReadWriteLock(LockDefinition definition) {
            this.readWriteDefinition = definition;
            return new DistributedReadWriteLock() {
                @Override
                public DistributedLock readLock() {
                    return new NoopLock();
                }

                @Override
                public DistributedLock writeLock() {
                    return new NoopLock();
                }
            };
        }
    }

    private static final class NoopLock implements DistributedLock {
        @Override
        public boolean tryLock(long waitTime, long leaseTime, TimeUnit unit) {
            return true;
        }

        @Override
        public boolean tryLock() {
            return true;
        }

        @Override
        public void lock() {
        }

        @Override
        public void lock(long leaseTime, TimeUnit unit) {
        }

        @Override
        public void unlock() {
        }

        @Override
        public boolean isHeldByCurrentThread() {
            return true;
        }

        @Override
        public boolean isLocked() {
            return true;
        }

        @Override
        public long remainingLeaseTime() {
            return -1;
        }
    }
}
