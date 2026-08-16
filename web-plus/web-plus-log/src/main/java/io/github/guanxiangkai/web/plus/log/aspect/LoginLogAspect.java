package io.github.guanxiangkai.web.plus.log.aspect;

import io.github.guanxiangkai.web.plus.core.context.RequestContextHolder;
import io.github.guanxiangkai.web.plus.core.spi.CurrentUserProvider;
import io.github.guanxiangkai.web.plus.core.net.ClientIpResolver;
import io.github.guanxiangkai.web.plus.log.annotation.LoginLog;
import io.github.guanxiangkai.web.plus.log.entity.BaseLog;
import io.github.guanxiangkai.web.plus.log.spi.LoginLogHandler;
import io.github.guanxiangkai.web.plus.log.support.LogEntityBinder;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * 登录日志 AOP 切面（WebFlux 响应式）
 *
 * @author guanxiangkai
 * @since 1.0.0
 */
@Slf4j
@Aspect
public class LoginLogAspect {

    @Autowired(required = false)
    private LoginLogHandler loginLogHandler;

    @Autowired(required = false)
    private CurrentUserProvider currentUserProvider;

    @Autowired(required = false)
    private ClientIpResolver clientIpResolver;

    @Around("@annotation(loginLog)")
    public Object around(ProceedingJoinPoint joinPoint, LoginLog loginLog) throws Throwable {
        // ── 在请求线程预先捕获不可变上下文（避免响应式回调中 ThreadLocal 不可见）
        ServerWebExchange exchange = extractExchange(joinPoint);
        String ip = "unknown";
        String userAgent = null;

        if (exchange != null) {
            ServerHttpRequest request = exchange.getRequest();
            ip = resolver().resolve(request);
            userAgent = request.getHeaders().getFirst("User-Agent");
        }

        var reqCtx = RequestContextHolder.get();
        final String traceId = reqCtx != null ? reqCtx.traceId() : null;
        final String action = loginLog.action();
        final String username = resolveUsername(joinPoint);
        final String finalIp = ip;
        final String finalUserAgent = userAgent;
        final String userId = currentUserProvider != null
                ? currentUserProvider.getCurrentUser().map(u -> u.userId()).orElse(null) : null;
        final String tenantId = currentUserProvider != null
                ? currentUserProvider.getCurrentUser().map(u -> u.tenantId()).orElse(null) : null;

        Object result;
        try {
            result = joinPoint.proceed();
        } catch (Throwable t) {
            dispatch(loginLog, traceId, userId, username, tenantId, finalIp, finalUserAgent, action, "FAIL",
                    t.getClass().getSimpleName());
            throw t;
        }

        if (result instanceof Mono<?> mono) {
            return mono
                    .doOnSuccess(r -> dispatch(loginLog, traceId, userId, username, tenantId,
                            finalIp, finalUserAgent, action, "SUCCESS", null))
                    .doOnError(e -> dispatch(loginLog, traceId, userId, username, tenantId,
                            finalIp, finalUserAgent, action, "FAIL", e.getClass().getSimpleName()));
        }

        dispatch(loginLog, traceId, userId, username, tenantId, finalIp, finalUserAgent, action, "SUCCESS", null);
        return result;
    }

    private void dispatch(LoginLog loginLog, String traceId, String userId, String username,
                          String tenantId, String clientIp, String userAgent,
                          String action, String status, String errorMsg) {
        log.info("[login] action={} traceId={} status={}", action, traceId, status);

        BaseLog entity = LogEntityBinder.newInstance(loginLog.entity());
        if (entity == null || loginLogHandler == null) return;

        LogEntityBinder.bindCommon(entity, traceId, userId, username, tenantId,
                clientIp, status, errorMsg);
        LogEntityBinder.set(entity, "action", action);
        LogEntityBinder.set(entity, "userAgent", userAgent);

        try {
            loginLogHandler.handle(entity);
        } catch (Exception e) {
            log.error("[web-plus] LoginLogHandler 执行异常: exception={}",
                    e.getClass().getSimpleName());
        }
    }

    /**
     * 解析用户名：优先从方法参数提取（登录 DTO），若无则从 {@link CurrentUserProvider} 读取（登出场景）
     */
    private String resolveUsername(ProceedingJoinPoint jp) {
        String fromArgs = extractUsername(jp);
        if (!"unknown".equals(fromArgs)) return fromArgs;
        // 登出等场景：用户已登录，从上下文读取
        if (currentUserProvider != null) {
            return currentUserProvider.getCurrentUser()
                    .map(u -> u.nickname() != null ? u.nickname() : u.userId())
                    .orElse("unknown");
        }
        return "unknown";
    }

    private ServerWebExchange extractExchange(ProceedingJoinPoint jp) {
        for (Object arg : jp.getArgs()) {
            if (arg instanceof ServerWebExchange ex) return ex;
        }
        return null;
    }

    /**
     * 从方法参数中提取显式用户名属性。
     *
     * <p>不能把任意 {@link String} 参数推断为用户名：登出令牌、刷新令牌等认证凭据也常以
     * 字符串传入，这种猜测会把秘密值写入登录日志。这里只接受 DTO 的
     * {@code getUsername()} 或 Record 的 {@code username()} 访问器。</p>
     */
    private String extractUsername(ProceedingJoinPoint jp) {
        for (Object arg : jp.getArgs()) {
            if (arg == null || arg instanceof ServerWebExchange) continue;
            // 尝试 getter 方法（如 LoginDTO.getUsername()）
            try {
                var val = arg.getClass().getMethod("getUsername").invoke(arg);
                if (val != null) return val.toString();
            } catch (Exception ignored) {
            }
            // 尝试 Record accessor（如 LoginDTO.username()）
            try {
                var val = arg.getClass().getMethod("username").invoke(arg);
                if (val != null) return val.toString();
            } catch (Exception ignored) {
            }
        }
        return "unknown";
    }

    private ClientIpResolver resolver() {
        return clientIpResolver != null ? clientIpResolver : ClientIpResolver.directPeer();
    }
}
