package io.github.guanxiangkai.redis.plus.core.aop;

import io.github.guanxiangkai.redis.plus.core.invoke.InvocationContext;
import io.github.guanxiangkai.redis.plus.core.util.ExceptionUtils;
import io.github.guanxiangkai.redis.plus.core.util.ThrowingSupplier;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.reflect.MethodSignature;

import java.util.function.Supplier;

/**
 * Shared helpers for redis-plus annotation aspects.
 */
public final class RedisPlusJoinPoints {

    private RedisPlusJoinPoints() {
    }

    public static InvocationContext invocationContext(ProceedingJoinPoint pjp) {
        MethodSignature signature = (MethodSignature) pjp.getSignature();
        return InvocationContext.of(pjp.getTarget(), signature.getMethod(), pjp.getArgs());
    }

    public static Class<?> returnType(ProceedingJoinPoint pjp) {
        return ((MethodSignature) pjp.getSignature()).getReturnType();
    }

    public static Supplier<Object> supplier(ProceedingJoinPoint pjp) {
        return unchecked(pjp::proceed);
    }

    public static <T> Supplier<T> unchecked(ThrowingSupplier<T> supplier) {
        return () -> {
            try {
                return supplier.get();
            } catch (Throwable t) {
                return ExceptionUtils.sneakyThrow(t);
            }
        };
    }
}
