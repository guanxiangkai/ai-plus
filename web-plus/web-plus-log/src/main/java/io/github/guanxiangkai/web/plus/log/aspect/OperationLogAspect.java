package io.github.guanxiangkai.web.plus.log.aspect;

import io.github.guanxiangkai.web.plus.core.context.CurrentUser;
import io.github.guanxiangkai.web.plus.core.context.CurrentUserHolder;
import io.github.guanxiangkai.web.plus.core.context.RequestContext;
import io.github.guanxiangkai.web.plus.core.context.RequestContextHolder;
import io.github.guanxiangkai.web.plus.core.spi.CurrentUserProvider;
import io.github.guanxiangkai.web.plus.core.util.IpUtils;
import io.github.guanxiangkai.web.plus.core.util.SafeSpelTemplateEvaluator;
import io.github.guanxiangkai.web.plus.log.annotation.OperationLog;
import io.github.guanxiangkai.web.plus.log.context.OperationLogContext;
import io.github.guanxiangkai.web.plus.log.context.OperationLogContext.OperationContext;
import io.github.guanxiangkai.web.plus.log.entity.BaseLog;
import io.github.guanxiangkai.web.plus.log.spi.OperationLogHandler;
import io.github.guanxiangkai.web.plus.log.support.LogEntityBinder;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 操作日志 AOP 切面（WebFlux 响应式）
 *
 * @author guanxiangkai
 * @since 1.0.0
 */
@Slf4j
@Aspect
public class OperationLogAspect {

    @Autowired(required = false)
    private OperationLogHandler operationLogHandler;

    @Autowired(required = false)
    private CurrentUserProvider currentUserProvider;

    @Autowired
    private ObjectMapper objectMapper;

    @Around("@annotation(opLog)")
    public Object around(ProceedingJoinPoint joinPoint, OperationLog opLog) throws Throwable {
        long startTime = System.currentTimeMillis();
        String operationId = UUID.randomUUID().toString().replace("-", "");
        ServerWebExchange exchange = extractExchange(joinPoint);

        Class<?> returnType = ((MethodSignature) joinPoint.getSignature()).getMethod().getReturnType();

        if (Mono.class.isAssignableFrom(returnType)) {
            return handleMonoPath(joinPoint, opLog, operationId, exchange, startTime);
        }
        if (Flux.class.isAssignableFrom(returnType)) {
            return handleFluxPath(joinPoint, opLog, operationId, exchange, startTime);
        }

        // ── 同步路径 ──
        var reqCtx = RequestContextHolder.get();
        var user = currentUserProvider != null
                ? currentUserProvider.getCurrentUser().orElse(null)
                : CurrentUserHolder.get();
        return handleSyncPath(joinPoint, opLog, operationId, exchange, user, reqCtx, startTime);
    }

    @SuppressWarnings("unchecked")
    private Mono<?> handleMonoPath(ProceedingJoinPoint joinPoint, OperationLog opLog,
                                   String operationId, ServerWebExchange exchange,
                                   long startTime) throws Throwable {
        Mono<Object> downstream = (Mono<Object>) joinPoint.proceed();
        Mono<java.util.Optional<CurrentUser>> userMono = currentUserProvider != null
                ? currentUserProvider.getCurrentUserMono()
                : Mono.fromCallable(() -> java.util.Optional.ofNullable(CurrentUserHolder.get()));

        return userMono.flatMap(userOpt -> {
            CurrentUser user = userOpt.orElse(null);
            var reqCtx = RequestContextHolder.get();
            String module = resolveSpel(opLog.module(), joinPoint);
            String typeCode = resolveSpel(opLog.typeCode(), joinPoint);
            String description = resolveSpel(opLog.description(), joinPoint);
            String requestParams = opLog.saveRequestParams() ? serializeArgs(joinPoint) : null;

            OperationContext opCtx = new OperationContext(
                    operationId,
                    reqCtx != null ? reqCtx.traceId() : null,
                    user != null ? user.userId() : null,
                    user != null ? user.tenantId() : null,
                    module, description, typeCode, null
            );

            return downstream
                    .contextWrite(ctx -> ctx.put(OperationLogContext.REACTOR_KEY, opCtx))
                    .doOnSuccess(r -> record(opLog, operationId, module, typeCode, description,
                            requestParams, exchange, user, reqCtx, startTime, "SUCCESS", null,
                            opLog.saveResponseData() ? toJson(r) : null))
                    .doOnError(e -> record(opLog, operationId, module, typeCode, description,
                            requestParams, exchange, user, reqCtx, startTime, "FAIL",
                            e.getMessage(), null));
        });
    }

    @SuppressWarnings("unchecked")
    private Flux<?> handleFluxPath(ProceedingJoinPoint joinPoint, OperationLog opLog,
                                   String operationId, ServerWebExchange exchange,
                                   long startTime) throws Throwable {
        Flux<Object> downstream = (Flux<Object>) joinPoint.proceed();
        Mono<java.util.Optional<CurrentUser>> userMono = currentUserProvider != null
                ? currentUserProvider.getCurrentUserMono()
                : Mono.fromCallable(() -> java.util.Optional.ofNullable(CurrentUserHolder.get()));

        return userMono.flatMapMany(userOpt -> {
            CurrentUser user = userOpt.orElse(null);
            var reqCtx = RequestContextHolder.get();
            String module = resolveSpel(opLog.module(), joinPoint);
            String typeCode = resolveSpel(opLog.typeCode(), joinPoint);
            String description = resolveSpel(opLog.description(), joinPoint);
            String requestParams = opLog.saveRequestParams() ? serializeArgs(joinPoint) : null;

            OperationContext opCtx = new OperationContext(
                    operationId,
                    reqCtx != null ? reqCtx.traceId() : null,
                    user != null ? user.userId() : null,
                    user != null ? user.tenantId() : null,
                    module, description, typeCode, null
            );

            return downstream
                    .contextWrite(ctx -> ctx.put(OperationLogContext.REACTOR_KEY, opCtx))
                    .doOnComplete(() -> record(opLog, operationId, module, typeCode, description,
                            requestParams, exchange, user, reqCtx, startTime, "SUCCESS", null, null))
                    .doOnError(e -> record(opLog, operationId, module, typeCode, description,
                            requestParams, exchange, user, reqCtx, startTime, "FAIL",
                            e.getMessage(), null));
        });
    }

    private Object handleSyncPath(ProceedingJoinPoint joinPoint, OperationLog opLog,
                                  String operationId, ServerWebExchange exchange,
                                  CurrentUser user, RequestContext reqCtx,
                                  long startTime) throws Throwable {
        String module = resolveSpel(opLog.module(), joinPoint);
        String typeCode = resolveSpel(opLog.typeCode(), joinPoint);
        String description = resolveSpel(opLog.description(), joinPoint);
        String requestParams = opLog.saveRequestParams() ? serializeArgs(joinPoint) : null;

        OperationContext opCtx = new OperationContext(
                operationId,
                reqCtx != null ? reqCtx.traceId() : null,
                user != null ? user.userId() : null,
                user != null ? user.tenantId() : null,
                module, description, typeCode, null
        );
        OperationLogContext.set(opCtx);

        Object result;
        try {
            result = joinPoint.proceed();
        } catch (Throwable t) {
            OperationLogContext.clear();
            record(opLog, operationId, module, typeCode, description, requestParams,
                    exchange, user, reqCtx, startTime, "FAIL", t.getMessage(), null);
            throw t;
        }

        OperationLogContext.clear();
        record(opLog, operationId, module, typeCode, description, requestParams,
                exchange, user, reqCtx, startTime, "SUCCESS", null,
                opLog.saveResponseData() ? toJson(result) : null);
        return result;
    }

    private void record(OperationLog opLog,
                        String operationId, String module, String typeCode,
                        String description, String requestParams,
                        ServerWebExchange exchange,
                        CurrentUser user, RequestContext reqCtx,
                        long startTime, String status, String errorMsg, String responseData) {
        try {
            long costMs = System.currentTimeMillis() - startTime;
            String traceId = reqCtx != null ? reqCtx.traceId() : null;
            String clientIp = null;
            String requestMethod = null;
            String requestUrl = null;
            String userAgent = null;

            if (exchange != null) {
                ServerHttpRequest req = exchange.getRequest();
                clientIp = IpUtils.getClientIp(req);
                requestMethod = req.getMethod().name();
                requestUrl = req.getURI().getPath();
                userAgent = req.getHeaders().getFirst("User-Agent");
            }

            log.info("[operation] opId={} module={} typeCode={} desc={} user={} ip={} url={} cost={}ms status={}",
                    operationId, module, typeCode, description,
                    user != null ? user.nickname() : null, clientIp, requestUrl, costMs, status);

            BaseLog entity = LogEntityBinder.newInstance(opLog.entity());
            if (entity == null || operationLogHandler == null) return;

            LogEntityBinder.bindCommon(entity, traceId,
                    user != null ? user.userId() : null,
                    user != null ? user.nickname() : null,
                    user != null ? user.tenantId() : null,
                    clientIp, status, errorMsg);
            LogEntityBinder.set(entity, "operationId", operationId);
            LogEntityBinder.set(entity, "module", module);
            LogEntityBinder.set(entity, "operationTypeCode", typeCode);
            LogEntityBinder.set(entity, "description", description);
            LogEntityBinder.set(entity, "requestMethod", requestMethod);
            LogEntityBinder.set(entity, "requestUrl", requestUrl);
            LogEntityBinder.set(entity, "requestParams", requestParams);
            LogEntityBinder.set(entity, "responseData", responseData);
            LogEntityBinder.set(entity, "userAgent", userAgent);
            LogEntityBinder.set(entity, "costMs", costMs);
            LogEntityBinder.set(entity, "errorMessage", errorMsg);

            try {
                operationLogHandler.handle(entity);
            } catch (Exception e) {
                log.error("[web-plus] OperationLogHandler 执行异常", e);
            }
        } catch (Exception e) {
            log.error("[web-plus] 记录操作日志异常", e);
        }
    }

    private String resolveSpel(String expr, ProceedingJoinPoint jp) {
        if (expr == null || !expr.contains("#{")) return expr;
        try {
            return SafeSpelTemplateEvaluator.evaluate(expr, jp.getTarget());
        } catch (Exception e) {
            log.warn("[web-plus] SpEL 解析失败: {}", expr);
            return expr;
        }
    }

    private ServerWebExchange extractExchange(ProceedingJoinPoint jp) {
        for (Object arg : jp.getArgs()) {
            if (arg instanceof ServerWebExchange ex) return ex;
        }
        return null;
    }

    private String serializeArgs(ProceedingJoinPoint jp) {
        try {
            List<Object> list = new ArrayList<>();
            for (Object arg : jp.getArgs()) {
                if (arg instanceof ServerWebExchange) list.add("[Exchange]");
                else if (arg instanceof ServerHttpRequest) list.add("[Request]");
                else list.add(arg);
            }
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
