package io.github.guanxiangkai.web.plus.log.aspect;

import io.github.guanxiangkai.web.plus.core.context.CurrentUser;
import io.github.guanxiangkai.web.plus.core.context.CurrentUserHolder;
import io.github.guanxiangkai.web.plus.core.context.RequestContextHolder;
import io.github.guanxiangkai.web.plus.core.spi.CurrentUserProvider;
import io.github.guanxiangkai.web.plus.log.annotation.OssLog;
import io.github.guanxiangkai.web.plus.log.context.OperationLogContext;
import io.github.guanxiangkai.web.plus.log.context.OperationLogContext.OperationContext;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.beans.factory.annotation.Autowired;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.lang.reflect.Method;
import java.util.UUID;

/**
 * OSS 上传日志上下文切面
 *
 * <p>
 * 该切面本身不直接持久化日志，而是在上传入口处创建统一的 {@code operationId}，
 * 并通过 ThreadLocal + Reactor Context 传递给文件服务，最终由
 * {@link io.github.guanxiangkai.web.plus.log.spi.OssLogHandler} 完成落库。
 * </p>
 *
 * @author guanxiangkai
 * @since 1.0.0
 */
@Slf4j
@Aspect
public class OssLogAspect {

    @Autowired(required = false)
    private CurrentUserProvider currentUserProvider;

    @Around("@annotation(io.github.guanxiangkai.web.plus.log.annotation.OssLog) || execution(* io.github.guanxiangkai.web.plus.file.controller.FileController.upload(..)) || execution(* io.github.guanxiangkai.web.plus.file.controller.FileController.uploadBatch(..))")
    public Object around(ProceedingJoinPoint joinPoint) throws Throwable {
        OssLog ossLog = resolveAnnotation(joinPoint);
        OperationContext existing = OperationLogContext.current();
        if (existing != null) {
            return joinPoint.proceed();
        }

        var reqCtx = RequestContextHolder.get();
        CurrentUser user = currentUserProvider != null
                ? currentUserProvider.getCurrentUser().orElse(null)
                : CurrentUserHolder.get();

        OperationContext opCtx = new OperationContext(
                UUID.randomUUID().toString().replace("-", ""),
                reqCtx != null ? reqCtx.traceId() : null,
                user != null ? user.userId() : null,
                user != null ? user.tenantId() : null,
                ossLog != null ? ossLog.module() : "OSS",
                ossLog != null ? ossLog.description() : "文件上传",
                ossLog != null ? ossLog.typeCode() : "OSS_UPLOAD",
                "文件上传"
        );

        OperationLogContext.set(opCtx);
        log.debug("[oss] create operation context opId={} module={} typeCode={} desc={}",
                opCtx.operationId(), opCtx.module(), opCtx.operationTypeCode(), opCtx.description());

        Object result;
        try {
            result = joinPoint.proceed();
        } catch (Throwable t) {
            OperationLogContext.clear();
            throw t;
        }

        if (result instanceof Mono<?> mono) {
            return mono
                    .contextWrite(ctx -> ctx.put(OperationLogContext.REACTOR_KEY, opCtx))
                    .doFinally(signal -> OperationLogContext.clear());
        }
        if (result instanceof Flux<?> flux) {
            return flux
                    .contextWrite(ctx -> ctx.put(OperationLogContext.REACTOR_KEY, opCtx))
                    .doFinally(signal -> OperationLogContext.clear());
        }

        OperationLogContext.clear();
        return result;
    }

    private OssLog resolveAnnotation(ProceedingJoinPoint joinPoint) {
        try {
            if (!(joinPoint.getSignature() instanceof MethodSignature signature)) {
                return null;
            }
            Method method = signature.getMethod();
            OssLog ann = method.getAnnotation(OssLog.class);
            if (ann != null) return ann;

            Method targetMethod = joinPoint.getTarget().getClass()
                    .getMethod(method.getName(), method.getParameterTypes());
            return targetMethod.getAnnotation(OssLog.class);
        } catch (Exception ignored) {
            return null;
        }
    }
}
