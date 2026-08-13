package io.github.guanxiangkai.web.plus.log.aspect;

import io.github.guanxiangkai.web.plus.core.util.SafeSpelTemplateEvaluator;
import io.github.guanxiangkai.web.plus.log.annotation.ScheduleLog;
import io.github.guanxiangkai.web.plus.log.entity.BaseLog;
import io.github.guanxiangkai.web.plus.log.spi.ScheduleLogHandler;
import io.github.guanxiangkai.web.plus.log.support.LogEntityBinder;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.beans.factory.annotation.Autowired;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import tools.jackson.databind.ObjectMapper;

import java.net.InetAddress;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 定时任务日志 AOP 切面
 *
 * @author guanxiangkai
 * @since 1.0.0
 */
@Slf4j
@Aspect
public class ScheduleLogAspect {

    @Autowired(required = false)
    private ScheduleLogHandler scheduleLogHandler;

    @Autowired
    private ObjectMapper objectMapper;

    @Around("@annotation(scheduleLog)")
    public Object around(ProceedingJoinPoint joinPoint, ScheduleLog scheduleLog) throws Throwable {
        LocalDateTime startTime = LocalDateTime.now();
        long startMs = System.currentTimeMillis();

        String jobName = resolveSpel(scheduleLog.jobName(), joinPoint);
        String description = resolveSpel(scheduleLog.description(), joinPoint);
        String params = scheduleLog.saveParams() ? serializeArgs(joinPoint) : null;
        String node = localHostname();

        Object result;
        try {
            result = joinPoint.proceed();
        } catch (Throwable t) {
            dispatch(scheduleLog, jobName, description, params, startTime, startMs, node, "FAIL", t.getMessage());
            throw t;
        }

        if (result instanceof Mono<?> mono) {
            return mono
                    .doOnSuccess(r -> dispatch(scheduleLog, jobName, description, params, startTime, startMs, node, "SUCCESS", null))
                    .doOnError(e -> dispatch(scheduleLog, jobName, description, params, startTime, startMs, node, "FAIL", e.getMessage()));
        } else if (result instanceof Flux<?> flux) {
            return flux
                    .doOnComplete(() -> dispatch(scheduleLog, jobName, description, params, startTime, startMs, node, "SUCCESS", null))
                    .doOnError(e -> dispatch(scheduleLog, jobName, description, params, startTime, startMs, node, "FAIL", e.getMessage()));
        } else {
            dispatch(scheduleLog, jobName, description, params, startTime, startMs, node, "SUCCESS", null);
            return result;
        }
    }

    private void dispatch(ScheduleLog ann, String jobName, String description,
                          String params, LocalDateTime startTime, long startMs,
                          String node, String status, String errorMessage) {
        long costMs = System.currentTimeMillis() - startMs;
        LocalDateTime endTime = LocalDateTime.now();

        log.info("[schedule] job={}/{} cost={}ms status={} node={}",
                ann.jobGroup(), jobName, costMs, status, node);

        BaseLog entity = LogEntityBinder.newInstance(ann.entity());
        if (entity == null || scheduleLogHandler == null) return;

        LogEntityBinder.bindCommon(entity, null, null, null, null, null, status, errorMessage);
        LogEntityBinder.set(entity, "jobName", jobName);
        LogEntityBinder.set(entity, "jobGroup", ann.jobGroup());
        LogEntityBinder.set(entity, "cronExpression", ann.cronExpression());
        LogEntityBinder.set(entity, "triggerType", ann.triggerType());
        LogEntityBinder.set(entity, "description", description);
        LogEntityBinder.set(entity, "params", params);
        LogEntityBinder.set(entity, "startTime", startTime);
        LogEntityBinder.set(entity, "endTime", endTime);
        LogEntityBinder.set(entity, "costMs", costMs);
        LogEntityBinder.set(entity, "executorNode", node);
        LogEntityBinder.set(entity, "errorMessage", errorMessage);

        try {
            scheduleLogHandler.handle(entity);
        } catch (Exception e) {
            log.error("[web-plus] ScheduleLogHandler 执行异常", e);
        }
    }

    private String resolveSpel(String expr, ProceedingJoinPoint jp) {
        if (expr == null || !expr.contains("#{")) return expr;
        try {
            return SafeSpelTemplateEvaluator.evaluate(expr, jp.getTarget());
        } catch (Exception e) {
            log.warn("[web-plus] ScheduleLog SpEL 解析失败: {}", expr);
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

    private String localHostname() {
        try {
            return InetAddress.getLocalHost().getHostName();
        } catch (Exception e) {
            return "unknown";
        }
    }
}
