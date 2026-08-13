package io.github.guanxiangkai.redis.plus.lock.impl;

import io.github.guanxiangkai.redis.plus.lock.DistributedLock;
import io.github.guanxiangkai.redis.plus.lock.DistributedReadWriteLock;
import io.github.guanxiangkai.redis.plus.lock.LockDefinition;
import io.github.guanxiangkai.redis.plus.lock.spi.LockBackend;
import org.redisson.api.RLock;
import org.redisson.api.RReadWriteLock;
import org.redisson.api.RedissonClient;

import java.util.concurrent.TimeUnit;

/**
 * Redisson-backed lock backend.
 */
public class RedissonLockBackend implements LockBackend {

    private final RedissonClient redissonClient;

    public RedissonLockBackend(RedissonClient redissonClient) {
        this.redissonClient = redissonClient;
    }

    @Override
    public DistributedLock getLock(LockDefinition definition) {
        return new RedissonDistributedLock(redissonClient.getLock(definition.name()));
    }

    @Override
    public DistributedReadWriteLock getReadWriteLock(LockDefinition definition) {
        return new RedissonDistributedReadWriteLock(redissonClient.getReadWriteLock(definition.name()));
    }

    private record RedissonDistributedReadWriteLock(RReadWriteLock delegate) implements DistributedReadWriteLock {

        @Override
        public DistributedLock readLock() {
            return new RedissonDistributedLock(delegate.readLock());
        }

        @Override
        public DistributedLock writeLock() {
            return new RedissonDistributedLock(delegate.writeLock());
        }
    }

    private record RedissonDistributedLock(RLock delegate) implements DistributedLock {

        @Override
        public boolean tryLock(long waitTime, long leaseTime, TimeUnit unit) {
            try {
                if (leaseTime < 0) {
                    return delegate.tryLock(waitTime, unit);
                }
                return delegate.tryLock(waitTime, leaseTime, unit);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        }

        @Override
        public boolean tryLock() {
            return delegate.tryLock();
        }

        @Override
        public void lock() {
            delegate.lock();
        }

        @Override
        public void lock(long leaseTime, TimeUnit unit) {
            if (leaseTime < 0) {
                delegate.lock();
                return;
            }
            delegate.lock(leaseTime, unit);
        }

        @Override
        public void unlock() {
            delegate.unlock();
        }

        @Override
        public boolean isHeldByCurrentThread() {
            return delegate.isHeldByCurrentThread();
        }

        @Override
        public boolean isLocked() {
            return delegate.isLocked();
        }

        @Override
        public long remainingLeaseTime() {
            return delegate.remainTimeToLive();
        }
    }
}
