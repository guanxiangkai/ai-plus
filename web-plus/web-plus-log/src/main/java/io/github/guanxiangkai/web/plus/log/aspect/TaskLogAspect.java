package io.github.guanxiangkai.web.plus.log.aspect;

import io.github.guanxiangkai.web.plus.core.context.CurrentUserHolder;
import io.github.guanxiangkai.web.plus.core.spi.CurrentUserProvider;
import io.github.guanxiangkai.web.plus.core.util.SafeSpelTemplateEvaluator;
import io.github.guanxiangkai.web.plus.log.annotation.TaskLog;
import io.github.guanxiangkai.web.plus.log.context.OperationLogContext;
import io.github.guanxiangkai.web.plus.log.context.OperationLogContext.OperationContext;
import io.github.guanxiangkai.web.plus.log.entity.BaseLog;
import io.github.guanxiangkai.web.plus.log.spi.TaskLogHandler;
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
 * 自定义任务日志 AOP 切面
 *
 * @author guanxiangkai
 * @since 1.0.0
 */
@Slf4j
@Aspect
public class TaskLogAspect {

    @Autowired(required = false)
    private TaskLogHandler taskLogHandler;

    @Autowired(required = false)
    private CurrentUserProvider currentUserProvider;

    @Autowired
    private ObjectMapper objectMapper;

    @Around("@annotation(taskLog)")
    public Object around(ProceedingJoinPoint joinPoint, TaskLog taskLog) throws Throwable {
        LocalDateTime startTime = LocalDateTime.now();
        long startMs = System.currentTimeMillis();

        OperationContext opCtx = OperationLogContext.current();
        var user = currentUserProvider != null
                ? currentUserProvider.getCurrentUser().orElse(null)
                : CurrentUserHolder.get();

        String taskName = resolveSpel(taskLog.taskName(), joinPoint);
        String taskType = resolveSpel(taskLog.taskType(), joinPoint);
        String description = resolveSpel(taskLog.description(), joinPoint);
        String params = taskLog.saveParams() ? serializeArgs(joinPoint) : null;

        Object result;
        try {
            result = joinPoint.proceed();
        } catch (Throwable t) {
            dispatch(taskLog, opCtx, user, taskName, taskType, description, params, startTime, startMs, "FAIL", t.getMessage());
            throw t;
        }

        if (result instanceof Mono<?> mono) {
            return mono
                    .doOnSuccess(r -> dispatch(taskLog, opCtx, user, taskName, taskType, description, params,
                            startTime, startMs, "SUCCESS", null))
                    .doOnError(e -> dispatch(taskLog, opCtx, user, taskName, taskType, description, params,
                            startTime, startMs, "FAIL", e.getMessage()));
        } else if (result instanceof Flux<?> flux) {
            return flux
                    .doOnComplete(() -> dispatch(taskLog, opCtx, user, taskName, taskType, description, params,
                            startTime, startMs, "SUCCESS", null))
                    .doOnError(e -> dispatch(taskLog, opCtx, user, taskName, taskType, description, params,
                            startTime, startMs, "FAIL", e.getMessage()));
        } else {
            dispatch(taskLog, opCtx, user, taskName, taskType, description, params,
                    startTime, startMs, "SUCCESS", null);
            return result;
        }
    }

    private void dispatch(TaskLog taskLog, OperationContext opCtx,
                          io.github.guanxiangkai.web.plus.core.context.CurrentUser user,
                          String taskName, String taskType, String description, String params,
                          LocalDateTime startTime, long startMs, String status, String errorMessage) {
        long costMs = System.currentTimeMillis() - startMs;
        LocalDateTime endTime = LocalDateTime.now();

        log.info("[task] name={} type={} user={} cost={}ms status={}",
                taskName, taskType, user != null ? user.userId() : null, costMs, status);

        BaseLog entity = LogEntityBinder.newInstance(taskLog.entity());
        if (entity == null || taskLogHandler == null) return;

        LogEntityBinder.bindCommon(entity,
                opCtx != null ? opCtx.traceId() : null,
                user != null ? user.userId() : null,
                user != null ? user.nickname() : null,
                user != null ? user.tenantId() : null,
                null, status, errorMessage);
        LogEntityBinder.set(entity, "taskName", taskName);
        LogEntityBinder.set(entity, "taskType", taskType);
        LogEntityBinder.set(entity, "description", description);
        LogEntityBinder.set(entity, "params", params);
        LogEntityBinder.set(entity, "operationId", opCtx != null ? opCtx.operationId() : null);
        LogEntityBinder.set(entity, "startTime", startTime);
        LogEntityBinder.set(entity, "endTime", endTime);
        LogEntityBinder.set(entity, "costMs", costMs);
        LogEntityBinder.set(entity, "errorMessage", errorMessage);

        try {
            taskLogHandler.handle(entity);
        } catch (Exception e) {
            log.error("[web-plus] TaskLogHandler 执行异常", e);
        }
    }

    private String resolveSpel(String expr, ProceedingJoinPoint jp) {
        if (expr == null || !expr.contains("#{")) return expr;
        try {
            return SafeSpelTemplateEvaluator.evaluate(expr, jp.getTarget());
        } catch (Exception e) {
            log.warn("[web-plus] TaskLog SpEL 解析失败: {}", expr);
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
}
