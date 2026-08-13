package io.github.guanxiangkai.redis.plus.core.util;

/**
 * Supplier variant whose operation may throw any {@link Throwable}.
 *
 * @param <T> supplied value type
 */
@FunctionalInterface
public interface ThrowingSupplier<T> {

    T get() throws Throwable;
}
