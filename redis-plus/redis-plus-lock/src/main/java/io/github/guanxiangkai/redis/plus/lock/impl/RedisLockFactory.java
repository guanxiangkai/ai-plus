package io.github.guanxiangkai.redis.plus.lock.impl;

import io.github.guanxiangkai.redis.plus.core.key.KeyNamespaceUtils;
import io.github.guanxiangkai.redis.plus.core.key.KeyNamingStrategy;
import io.github.guanxiangkai.redis.plus.lock.DistributedLock;
import io.github.guanxiangkai.redis.plus.lock.DistributedReadWriteLock;
import io.github.guanxiangkai.redis.plus.lock.LockDefinition;
import io.github.guanxiangkai.redis.plus.lock.spi.LockBackend;
import org.springframework.beans.factory.DisposableBean;

/**
 * 分布式锁工厂
 *
 * <p>统一入口，根据锁名获取 {@link DistributedLock} 或 {@link DistributedReadWriteLock} 实例。
 *
 * <p>工厂只负责 Key 命名与入口编排，具体锁语义由 {@link LockBackend} 提供。
 *
 * <p>注意：每次调用返回新实例；是否支持重入、续期、读写锁由后端实现决定。
 */
public class RedisLockFactory implements DisposableBean {

    private final String keyNamespace;
    private final KeyNamingStrategy keyNamingStrategy;
    private final LockBackend backend;

    public RedisLockFactory(String keyPrefix,
                            KeyNamingStrategy keyNamingStrategy,
                            LockBackend backend) {
        this.keyNamespace = KeyNamespaceUtils.namespace(keyPrefix, "redis-plus:lock");
        this.keyNamingStrategy = keyNamingStrategy;
        this.backend = backend;
    }

    /**
     * 获取互斥分布式锁。
     *
     * @param name 锁业务名称，最终 Key = {@code keyPrefix + name}
     */
    public DistributedLock getLock(String name) {
        return getLock(LockDefinition.of(name));
    }

    public DistributedLock getLock(LockDefinition definition) {
        return backend.getLock(rekey(definition));
    }

    /**
     * 获取分布式读写锁。
     *
     * @param name 锁业务名称
     */
    public DistributedReadWriteLock getReadWriteLock(String name) {
        return getReadWriteLock(LockDefinition.of(name));
    }

    public DistributedReadWriteLock getReadWriteLock(LockDefinition definition) {
        return backend.getReadWriteLock(rekey(definition));
    }

    @Override
    public void destroy() {
        backend.close();
    }

    private LockDefinition rekey(LockDefinition definition) {
        String key = keyNamingStrategy.resolve(keyNamespace, definition.name());
        return new LockDefinition(key, definition.leaseTime(), definition.waitTime(), definition.reentrant());
    }
}
