package io.github.guanxiangkai.redis.plus.idempotent.aop;

import io.github.guanxiangkai.redis.plus.core.aop.RedisPlusAspectOrder;
import io.github.guanxiangkai.redis.plus.core.aop.RedisPlusJoinPoints;
import io.github.guanxiangkai.redis.plus.core.invoke.InvocationContext;
import io.github.guanxiangkai.redis.plus.core.invoke.InvocationContexts;
import io.github.guanxiangkai.redis.plus.idempotent.IdempotentExecutor;
import io.github.guanxiangkai.redis.plus.idempotent.annotation.Idempotent;
import io.github.guanxiangkai.redis.plus.idempotent.spi.IdempotentKeyResolver;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.core.annotation.Order;

import java.time.Duration;

/**
 * 幂等 AOP 切面
 *
 * <p>拦截 {@link Idempotent} 注解，通过 {@link IdempotentExecutor} 保证方法幂等执行。
 */
@Aspect
@Order(RedisPlusAspectOrder.IDEMPOTENT)
@SuppressWarnings("NullAway")
public class IdempotentAspect {

    private final IdempotentExecutor executor;
    private final IdempotentKeyResolver keyResolver;

    public IdempotentAspect(IdempotentExecutor executor) {
        this(executor, null);
    }

    public IdempotentAspect(IdempotentExecutor executor,
                            IdempotentKeyResolver keyResolver) {
        this.executor = executor;
        this.keyResolver = keyResolver;
    }

    @Around("@annotation(idempotent)")
    Object around(ProceedingJoinPoint pjp, Idempotent idempotent) throws Throwable {
        InvocationContext context = invocationContext(pjp);
        String key = resolveSpel(context, idempotent.key());
        Duration ttl = Duration.of(idempotent.ttl(), idempotent.unit().toChronoUnit());

        return executor.executeOnce(key, ttl, RedisPlusJoinPoints.supplier(pjp));
    }

    private InvocationContext invocationContext(ProceedingJoinPoint pjp) {
        return RedisPlusJoinPoints.invocationContext(pjp);
    }

    private String resolveSpel(InvocationContext context, String expression) {
        return InvocationContexts.resolveKey(context, expression, keyResolver);
    }
}
