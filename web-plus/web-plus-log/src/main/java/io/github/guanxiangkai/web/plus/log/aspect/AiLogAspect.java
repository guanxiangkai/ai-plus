package io.github.guanxiangkai.web.plus.log.aspect;

import io.github.guanxiangkai.web.plus.core.context.CurrentUserHolder;
import io.github.guanxiangkai.web.plus.core.spi.CurrentUserProvider;
import io.github.guanxiangkai.web.plus.core.util.SafeSpelTemplateEvaluator;
import io.github.guanxiangkai.web.plus.log.annotation.AiLog;
import io.github.guanxiangkai.web.plus.log.context.OperationLogContext;
import io.github.guanxiangkai.web.plus.log.context.OperationLogContext.OperationContext;
import io.github.guanxiangkai.web.plus.log.entity.BaseLog;
import io.github.guanxiangkai.web.plus.log.spi.AiCallLogHandler;
import io.github.guanxiangkai.web.plus.log.support.LogEntityBinder;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.beans.factory.annotation.Autowired;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * AI 模型调用日志 AOP 切面
 *
 * @author guanxiangkai
 * @since 1.0.0
 */
@Slf4j
@Aspect
public class AiLogAspect {

    @Autowired(required = false)
    private AiCallLogHandler aiCallLogHandler;

    @Autowired(required = false)
    private CurrentUserProvider currentUserProvider;

    @Autowired
    private ObjectMapper objectMapper;

    @Around("@annotation(aiLog)")
    public Object around(ProceedingJoinPoint joinPoint, AiLog aiLog) throws Throwable {
        long startMs = System.currentTimeMillis();
        LocalDateTime callTime = LocalDateTime.now();

        OperationContext opCtx = OperationLogContext.current();
        var user = currentUserProvider != null
                ? currentUserProvider.getCurrentUser().orElse(null)
                : CurrentUserHolder.get();

        String provider = resolveSpel(aiLog.provider(), joinPoint);
        String model = resolveSpel(aiLog.model(), joinPoint);
        String description = resolveSpel(aiLog.description(), joinPoint);
        String inputContent = aiLog.saveInputContent() ? serializeArgs(joinPoint) : null;

        Object result;
        try {
            result = joinPoint.proceed();
        } catch (Throwable t) {
            dispatch(aiLog, opCtx, user, provider, model, description, inputContent, null, startMs, "FAIL", t.getMessage());
            throw t;
        }

        if (result instanceof Mono<?> mono) {
            return mono
                    .doOnSuccess(r -> dispatch(aiLog, opCtx, user, provider, model, description, inputContent,
                            aiLog.saveOutputContent() ? toJson(r) : null, startMs, "SUCCESS", null))
                    .doOnError(e -> dispatch(aiLog, opCtx, user, provider, model, description, inputContent,
                            null, startMs, "FAIL", e.getMessage()));
        } else if (result instanceof Flux<?> flux) {
            return flux
                    .doOnComplete(() -> dispatch(aiLog, opCtx, user, provider, model, description, inputContent,
                            null, startMs, "SUCCESS", null))
                    .doOnError(e -> dispatch(aiLog, opCtx, user, provider, model, description, inputContent,
                            null, startMs, "FAIL", e.getMessage()));
        } else {
            dispatch(aiLog, opCtx, user, provider, model, description, inputContent,
                    aiLog.saveOutputContent() ? toJson(result) : null, startMs, "SUCCESS", null);
            return result;
        }
    }

    private void dispatch(AiLog aiLog, OperationContext opCtx,
                          io.github.guanxiangkai.web.plus.core.context.CurrentUser user,
                          String provider, String model, String description,
                          String inputContent, String outputContent,
                          long startMs, String status, String errorMessage) {
        long costMs = System.currentTimeMillis() - startMs;

        log.info("[ai-call] provider={} model={} desc={} user={} cost={}ms status={}",
                provider, model, description, user != null ? user.userId() : null, costMs, status);

        BaseLog entity = LogEntityBinder.newInstance(aiLog.entity());
        if (entity == null || aiCallLogHandler == null) return;

        LogEntityBinder.bindCommon(entity,
                opCtx != null ? opCtx.traceId() : null,
                user != null ? user.userId() : null,
                user != null ? user.nickname() : null,
                user != null ? user.tenantId() : null,
                null, status, errorMessage);
        LogEntityBinder.set(entity, "operationId", opCtx != null ? opCtx.operationId() : null);
        LogEntityBinder.set(entity, "provider", provider);
        LogEntityBinder.set(entity, "model", model);
        LogEntityBinder.set(entity, "description", description);
        LogEntityBinder.set(entity, "inputContent", inputContent);
        LogEntityBinder.set(entity, "outputContent", outputContent);
        LogEntityBinder.set(entity, "costMs", costMs);
        LogEntityBinder.set(entity, "errorMessage", errorMessage);

        try {
            aiCallLogHandler.handle(entity);
        } catch (Exception e) {
            log.error("[web-plus] AiCallLogHandler 执行异常", e);
        }
    }

    private String resolveSpel(String expr, ProceedingJoinPoint jp) {
        if (expr == null || !expr.contains("#{")) return expr;
        try {
            return SafeSpelTemplateEvaluator.evaluate(expr, jp.getTarget());
        } catch (Exception e) {
            log.warn("[web-plus] AiLog SpEL 解析失败: {}", expr);
            return expr;
        }
    }

    private String serializeArgs(ProceedingJoinPoint jp) {
        try {
            List<Object> list = new ArrayList<>();
            Collections.addAll(list, jp.getArgs());
            return objectMapper.writeValueAsString(list);
        } catch (Exception e) {
            return null;
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
