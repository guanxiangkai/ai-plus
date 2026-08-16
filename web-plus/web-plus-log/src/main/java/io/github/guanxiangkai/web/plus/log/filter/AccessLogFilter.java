package io.github.guanxiangkai.web.plus.log.filter;

import io.github.guanxiangkai.web.plus.core.context.CurrentUser;
import io.github.guanxiangkai.web.plus.core.context.CurrentUserHolder;
import io.github.guanxiangkai.web.plus.core.context.RequestContext;
import io.github.guanxiangkai.web.plus.core.context.RequestContextHolder;
import io.github.guanxiangkai.web.plus.core.net.ClientIpResolver;
import io.github.guanxiangkai.web.plus.log.entity.BaseLog;
import io.github.guanxiangkai.web.plus.log.spi.AccessLogHandler;
import io.github.guanxiangkai.web.plus.log.support.LogEntityBinder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 访问日志过滤器（WebFlux WebFilter）
 *
 * <p>
 * 自动采集每次请求的路径、耗时、状态码、IP、用户信息等。
 * 若配置了 {@code web-plus.log.access-log-entity-class}，框架将创建对应实体实例
 * 并通过字段名约定填充后传入 {@link AccessLogHandler} SPI。
 * </p>
 *
 * @author guanxiangkai
 * @since 1.0.0
 */
@Slf4j
public class AccessLogFilter implements WebFilter, Ordered {

    private final List<String> ignorePaths;
    private final AccessLogHandler accessLogHandler;
    private final Class<?> entityClass;
    private final ClientIpResolver clientIpResolver;
    private final AntPathMatcher antMatcher = new AntPathMatcher();

    /**
     * 创建仅使用 TCP 对端地址的访问日志过滤器。
     *
     * @param ignorePaths 忽略路径
     * @param accessLogHandler 可选日志处理器
     * @param entityClass 可选日志实体类型
     */
    public AccessLogFilter(List<String> ignorePaths,
                           AccessLogHandler accessLogHandler,
                           Class<?> entityClass) {
        this(ignorePaths, accessLogHandler, entityClass, ClientIpResolver.directPeer());
    }

    /**
     * 创建使用显式客户端 IP 策略的访问日志过滤器。
     *
     * @param ignorePaths 忽略路径
     * @param accessLogHandler 可选日志处理器
     * @param entityClass 可选日志实体类型
     * @param clientIpResolver 客户端 IP 解析策略
     */
    public AccessLogFilter(List<String> ignorePaths,
                           AccessLogHandler accessLogHandler,
                           Class<?> entityClass,
                           ClientIpResolver clientIpResolver) {
        this.ignorePaths = ignorePaths;
        this.accessLogHandler = accessLogHandler;
        this.entityClass = entityClass;
        this.clientIpResolver = clientIpResolver;
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 10;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String path = exchange.getRequest().getURI().getPath();

        if (ignorePaths.stream().anyMatch(p -> matches(path, p))) {
            return chain.filter(exchange);
        }

        final String ip = clientIpResolver.resolve(exchange.getRequest());
        final String userAgent = exchange.getRequest().getHeaders().getFirst("User-Agent");
        final String method = exchange.getRequest().getMethod().name();
        final long startTime = System.currentTimeMillis();
        final LocalDateTime requestTime = LocalDateTime.now();

        // 在订阅时从 Reactor Context 捕获用户和请求上下文，避免 doFinally 回调因线程
        // 切换导致 ThreadLocal 已清空而读不到用户信息。
        return Mono.deferContextual(ctxView -> {
            CurrentUser capturedUser = ctxView.hasKey(CurrentUserHolder.REACTOR_CONTEXT_KEY)
                    ? ctxView.get(CurrentUserHolder.REACTOR_CONTEXT_KEY)
                    : CurrentUserHolder.get();
            RequestContext capturedReqCtx = ctxView.hasKey(RequestContextHolder.REACTOR_CONTEXT_KEY)
                    ? ctxView.get(RequestContextHolder.REACTOR_CONTEXT_KEY)
                    : RequestContextHolder.get();

            return chain.filter(exchange)
                    .doFinally(signal -> {
                        long costMs = System.currentTimeMillis() - startTime;

                        int statusCode = exchange.getResponse().getStatusCode() != null
                                ? exchange.getResponse().getStatusCode().value() : 0;
                        String traceId = capturedReqCtx != null ? capturedReqCtx.traceId() : null;
                        String userId = capturedUser != null ? capturedUser.userId() : null;
                        String username = capturedUser != null ? capturedUser.nickname() : null;
                        String tenantId = capturedUser != null ? capturedUser.tenantId() : null;

                        log.info("[access] {} {} {} {}ms traceId={}",
                                method, path, statusCode, costMs, traceId);

                        if (accessLogHandler != null) {
                            BaseLog entity = LogEntityBinder.newInstance(entityClass);
                            if (entity != null) {
                                String status = (statusCode >= 200 && statusCode < 400) ? "SUCCESS" : "FAIL";
                                LogEntityBinder.bindCommon(entity, traceId, userId, username, tenantId,
                                        ip, status, null);
                                LogEntityBinder.set(entity, "requestMethod", method);
                                LogEntityBinder.set(entity, "requestUrl", path);
                                LogEntityBinder.set(entity, "responseStatus", statusCode);
                                LogEntityBinder.set(entity, "userAgent", userAgent);
                                LogEntityBinder.set(entity, "costMs", costMs);
                                try {
                                    accessLogHandler.handle(entity);
                                } catch (Exception e) {
                                    log.error("[web-plus] AccessLogHandler 执行异常: exception={}",
                                            e.getClass().getSimpleName());
                                }
                            }
                        }
                    });
        });
    }

    private boolean matches(String path, String pattern) {
        return antMatcher.match(pattern, path);
    }
}
