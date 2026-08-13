package io.github.guanxiangkai.web.plus.log.aspect;

import io.github.guanxiangkai.web.plus.core.context.CurrentUserHolder;
import io.github.guanxiangkai.web.plus.core.spi.CurrentUserProvider;
import io.github.guanxiangkai.web.plus.core.util.SafeSpelTemplateEvaluator;
import io.github.guanxiangkai.web.plus.log.annotation.SseLog;
import io.github.guanxiangkai.web.plus.log.context.OperationLogContext;
import io.github.guanxiangkai.web.plus.log.context.OperationLogContext.OperationContext;
import io.github.guanxiangkai.web.plus.log.entity.BaseLog;
import io.github.guanxiangkai.web.plus.log.spi.SseLogHandler;
import io.github.guanxiangkai.web.plus.log.support.LogEntityBinder;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.beans.factory.annotation.Autowired;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import tools.jackson.databind.ObjectMapper;

/**
 * SSE 消息推送日志 AOP 切面
 *
 * @author guanxiangkai
 * @since 1.0.0
 */
@Slf4j
@Aspect
public class SseLogAspect {

    @Autowired(required = false)
    private SseLogHandler sseLogHandler;

    @Autowired(required = false)
    private CurrentUserProvider currentUserProvider;

    @Autowired
    private ObjectMapper objectMapper;

    @Around("@annotation(sseLog)")
    public Object around(ProceedingJoinPoint joinPoint, SseLog sseLog) throws Throwable {
        long startMs = System.currentTimeMillis();

        OperationContext opCtx = OperationLogContext.current();
        var user = currentUserProvider != null
                ? currentUserProvider.getCurrentUser().orElse(null)
                : CurrentUserHolder.get();

        String messageType = resolveSpel(sseLog.messageType(), joinPoint);
        String targetType = resolveSpel(sseLog.targetType(), joinPoint);
        String description = resolveSpel(sseLog.description(), joinPoint);

        Object result;
        try {
            result = joinPoint.proceed();
        } catch (Throwable t) {
            dispatch(sseLog, opCtx, user, messageType, targetType, description, null, startMs, "FAIL", t.getMessage());
            throw t;
        }

        if (result instanceof Mono<?> mono) {
            return mono
                    .doOnSuccess(r -> dispatch(sseLog, opCtx, user, messageType, targetType, description,
                            sseLog.saveContent() ? toJson(r) : null, startMs, "SUCCESS", null))
                    .doOnError(e -> dispatch(sseLog, opCtx, user, messageType, targetType, description,
                            null, startMs, "FAIL", e.getMessage()));
        } else if (result instanceof Flux<?> flux) {
            return flux
                    .doOnComplete(() -> dispatch(sseLog, opCtx, user, messageType, targetType, description,
                            null, startMs, "SUCCESS", null))
                    .doOnError(e -> dispatch(sseLog, opCtx, user, messageType, targetType, description,
                            null, startMs, "FAIL", e.getMessage()));
        } else {
            dispatch(sseLog, opCtx, user, messageType, targetType, description,
                    sseLog.saveContent() ? toJson(result) : null, startMs, "SUCCESS", null);
            return result;
        }
    }

    private void dispatch(SseLog sseLog, OperationContext opCtx,
                          io.github.guanxiangkai.web.plus.core.context.CurrentUser user,
                          String messageType, String targetType, String description,
                          String content, long startMs, String status, String failReason) {
        long costMs = System.currentTimeMillis() - startMs;

        log.info("[sse] type={} target={} user={} cost={}ms status={}",
                messageType, targetType, user != null ? user.userId() : null, costMs, status);

        BaseLog entity = LogEntityBinder.newInstance(sseLog.entity());
        if (entity == null || sseLogHandler == null) return;

        LogEntityBinder.bindCommon(entity,
                opCtx != null ? opCtx.traceId() : null,
                user != null ? user.userId() : null,
                user != null ? user.nickname() : null,
                user != null ? user.tenantId() : null,
                null, status, failReason);
        LogEntityBinder.set(entity, "messageType", messageType);
        LogEntityBinder.set(entity, "targetType", targetType);
        LogEntityBinder.set(entity, "description", description);
        LogEntityBinder.set(entity, "content", content);
        LogEntityBinder.set(entity, "operationId", opCtx != null ? opCtx.operationId() : null);
        LogEntityBinder.set(entity, "costMs", costMs);
        LogEntityBinder.set(entity, "failReason", failReason);

        try {
            sseLogHandler.handle(entity);
        } catch (Exception e) {
            log.error("[web-plus] SseLogHandler 执行异常", e);
        }
    }

    private String resolveSpel(String expr, ProceedingJoinPoint jp) {
        if (expr == null || !expr.contains("#{")) return expr;
        try {
            return SafeSpelTemplateEvaluator.evaluate(expr, jp.getTarget());
        } catch (Exception e) {
            log.warn("[web-plus] SseLog SpEL 解析失败: {}", expr);
            return expr;
        }
    }

    private String toJson(Object obj) {
        if (obj == null) return null;
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (Exception e) {
            return null;
        }
    }
}
