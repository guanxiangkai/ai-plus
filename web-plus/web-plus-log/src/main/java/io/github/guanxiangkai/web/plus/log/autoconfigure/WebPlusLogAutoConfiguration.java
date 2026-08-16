package io.github.guanxiangkai.web.plus.log.autoconfigure;

import io.github.guanxiangkai.web.plus.core.spi.TraceIdGenerator;
import io.github.guanxiangkai.web.plus.core.net.ClientIpResolver;
import io.github.guanxiangkai.web.plus.log.aspect.*;
import io.github.guanxiangkai.web.plus.log.context.OperationLogContextAccessor;
import io.github.guanxiangkai.web.plus.log.context.RequestContextThreadLocalAccessor;
import io.github.guanxiangkai.web.plus.log.filter.AccessLogFilter;
import io.github.guanxiangkai.web.plus.log.filter.TraceIdFilter;
import io.github.guanxiangkai.web.plus.log.properties.LogProperties;
import io.github.guanxiangkai.web.plus.log.spi.*;
import io.github.guanxiangkai.web.plus.log.support.LogEntityBinder;
import io.micrometer.context.ContextRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import reactor.core.publisher.Hooks;

import java.util.UUID;

/**
 * web-plus-log 自动装配
 *
 * <h3>扩展点（SPI）</h3>
 * <ul>
 *   <li>{@link io.github.guanxiangkai.web.plus.log.spi.OperationLogHandler} —— 操作日志持久化（业务方实现，写入 DB）</li>
 *   <li>{@link LoginLogHandler} —— 登录日志持久化（业务方实现，写入 DB）</li>
 *   <li>{@link OssLogHandler} —— OSS 上传日志持久化（FileService 上传自动触发）</li>
 *   <li>{@link AccessLogHandler} —— 访问日志持久化（业务方实现，写入 DB 或 ES）</li>
 *   <li>{@link DataChangeHandler} —— 数据变更日志持久化（配合 jpa-plus {@code DataAuditEvent}，
 *       通过 {@link io.github.guanxiangkai.web.plus.log.context.OperationLogContext} 关联操作日志）</li>
 *   <li>{@link ScheduleLogHandler} —— 定时任务日志持久化（{@code @ScheduleLog} 注解触发）</li>
 *   <li>{@link AiCallLogHandler} —— AI 调用日志持久化（{@code @AiLog} 注解触发）</li>
 *   <li>{@link TaskLogHandler} —— 自定义任务日志持久化（{@code @TaskLog} 注解触发）</li>
 *   <li>{@link TraceIdGenerator} —— TraceId 生成策略（可替换为雪花算法/SkyWalking ID）</li>
 * </ul>
 *
 * @author guanxiangkai
 * @since 1.0.0
 */
@Slf4j
@AutoConfiguration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.REACTIVE)
@ConditionalOnProperty(prefix = "web-plus.log", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(LogProperties.class)
public class WebPlusLogAutoConfiguration {

    public WebPlusLogAutoConfiguration() {
        log.info("[web-plus] Log 模块已启用");
    }

    /**
     * 默认 TraceId 生成器（UUID，可被业务方 Bean 覆盖）
     */
    @Bean
    @ConditionalOnMissingBean(TraceIdGenerator.class)
    public TraceIdGenerator traceIdGenerator() {
        return () -> UUID.randomUUID().toString().replace("-", "");
    }

    /**
     * TraceId 过滤器（最高优先级）
     */
    @Bean
    public TraceIdFilter traceIdFilter(TraceIdGenerator generator, LogProperties props) {
        return new TraceIdFilter(generator, props.traceHeaderName());
    }

    private static Class<?> resolveClass(String className, String configKey) {
        if (className == null || className.isBlank()) return null;
        try {
            return Class.forName(className.strip());
        } catch (ClassNotFoundException e) {
            org.slf4j.LoggerFactory.getLogger(WebPlusLogAutoConfiguration.class)
                    .warn("[web-plus] 无法加载日志实体类 [{}={}]，请检查类路径", configKey, className);
            return null;
        }
    }

    /**
     * 操作日志 AOP 切面
     */
    @Bean
    @ConditionalOnMissingBean(OperationLogAspect.class)
    @ConditionalOnProperty(prefix = "web-plus.log", name = "operation-log-enabled",
            havingValue = "true", matchIfMissing = true)
    public OperationLogAspect operationLogAspect() {
        return new OperationLogAspect();
    }

    /**
     * 登录日志 AOP 切面
     */
    @Bean
    @ConditionalOnMissingBean(LoginLogAspect.class)
    @ConditionalOnProperty(prefix = "web-plus.log", name = "login-log-enabled",
            havingValue = "true", matchIfMissing = true)
    public LoginLogAspect loginLogAspect() {
        return new LoginLogAspect();
    }

    /**
     * 访问日志过滤器
     */
    @Bean
    @ConditionalOnProperty(prefix = "web-plus.log", name = "access-log-enabled",
            havingValue = "true", matchIfMissing = true)
    public AccessLogFilter accessLogFilter(LogProperties props,
                                           @Autowired(required = false) AccessLogHandler accessLogHandler,
                                           ObjectProvider<ClientIpResolver> clientIpResolver) {
        Class<?> entityClass = resolveClass(props.accessLogEntityClass(), "accessLogEntityClass");
        return new AccessLogFilter(props.ignorePaths(), accessLogHandler, entityClass,
                clientIpResolver.getIfAvailable(ClientIpResolver::directPeer));
    }

    /**
     * Micrometer ThreadLocalAccessor（WebFlux + 阻塞 JPA 场景的操作上下文传播）
     *
     * <p>
     * 当 {@code web-plus.log.context-propagation-enabled=true}（默认）时，
     * 自动调用 {@code Hooks.enableAutomaticContextPropagation()} 并将此 Accessor
     * 注册到 {@code ContextRegistry}，使 jpa-plus {@code DataAuditEvent} 监听器
     * 在弹性调度线程（如阻塞 JPA 操作）上也能通过
     * {@link io.github.guanxiangkai.web.plus.log.context.OperationLogContext#current()} 读取当前操作 ID。
     * </p>
     * <p>若项目已通过 {@code web-plus-security} 启用了上下文传播，重复调用幂等，无副作用。</p>
     */
    @Bean
    @ConditionalOnMissingBean(OperationLogContextAccessor.class)
    public OperationLogContextAccessor operationLogContextAccessor(LogProperties props) {
        OperationLogContextAccessor accessor = new OperationLogContextAccessor();
        if (Boolean.TRUE.equals(props.contextPropagationEnabled())) {
            Hooks.enableAutomaticContextPropagation();
            ContextRegistry.getInstance().registerThreadLocalAccessor(accessor);
            ContextRegistry.getInstance().registerThreadLocalAccessor(new RequestContextThreadLocalAccessor());
            log.info("[web-plus] OperationLog/RequestContext 上下文传播已启用（web-plus.log.context-propagation-enabled=false 可关闭）");
        }
        return accessor;
    }

    /**
     * OSS 上传日志上下文切面
     */
    @Bean
    @ConditionalOnMissingBean(OssLogAspect.class)
    @ConditionalOnProperty(prefix = "web-plus.log", name = "oss-log-enabled",
            havingValue = "true", matchIfMissing = true)
    public OssLogAspect ossLogAspect() {
        return new OssLogAspect();
    }

    /**
     * 默认数据变更日志处理器（仅打印到日志，业务方可覆盖）
     *
     * <p>
     * 若业务项目需要将数据变更持久化到数据库（结合 jpa-plus），
     * 只需注册自己的 {@link DataChangeHandler} Bean 即可自动覆盖此默认实现。
     * </p>
     */
    @Bean
    @ConditionalOnMissingBean(DataChangeHandler.class)
    public DataChangeHandler defaultDataChangeHandler() {
        return entity -> {
            if (entity == null) return;
            log.debug("[data-change] opId={} entity={} type={} fields={}",
                    LogEntityBinder.get(entity, "operationId"),
                    LogEntityBinder.get(entity, "entityType"),
                    LogEntityBinder.get(entity, "changeType"),
                    LogEntityBinder.get(entity, "fieldChanges") != null ? "..." : "null");
        };
    }

    // ─── 新增日志类型：OSS上传 / 定时任务 / AI调用 / 自定义任务 ────────────

    /**
     * 默认登录日志处理器（仅打印到日志，业务方可覆盖）
     */
    @Bean
    @ConditionalOnMissingBean(LoginLogHandler.class)
    public LoginLogHandler defaultLoginLogHandler() {
        return entity -> {
            if (entity == null) return;
            log.debug("[login] action={} status={}",
                    LogEntityBinder.get(entity, "action"), entity.getStatus());
        };
    }

    /**
     * 定时任务日志 AOP 切面
     */
    @Bean
    @ConditionalOnMissingBean(ScheduleLogAspect.class)
    @ConditionalOnProperty(prefix = "web-plus.log", name = "schedule-log-enabled",
            havingValue = "true", matchIfMissing = true)
    public ScheduleLogAspect scheduleLogAspect() {
        return new ScheduleLogAspect();
    }

    /**
     * AI 调用日志 AOP 切面
     */
    @Bean
    @ConditionalOnMissingBean(AiLogAspect.class)
    @ConditionalOnProperty(prefix = "web-plus.log", name = "ai-log-enabled",
            havingValue = "true", matchIfMissing = true)
    public AiLogAspect aiLogAspect() {
        return new AiLogAspect();
    }

    /**
     * 自定义任务日志 AOP 切面
     */
    @Bean
    @ConditionalOnMissingBean(TaskLogAspect.class)
    @ConditionalOnProperty(prefix = "web-plus.log", name = "task-log-enabled",
            havingValue = "true", matchIfMissing = true)
    public TaskLogAspect taskLogAspect() {
        return new TaskLogAspect();
    }

    /**
     * 默认 OSS 上传日志处理器（降级打印，业务方可覆盖）
     */
    @Bean
    @ConditionalOnMissingBean(OssLogHandler.class)
    public OssLogHandler defaultOssLogHandler() {
        return entity -> {
            if (entity == null) return;
            log.debug("[oss-upload] size={} cost={}ms status={}",
                    LogEntityBinder.get(entity, "fileSize"),
                    LogEntityBinder.get(entity, "costMs"),
                    entity.getStatus());
        };
    }

    /**
     * SSE 消息推送日志 AOP 切面
     */
    @Bean
    @ConditionalOnMissingBean(SseLogAspect.class)
    @ConditionalOnProperty(prefix = "web-plus.log", name = "sse-log-enabled",
            havingValue = "true", matchIfMissing = true)
    public SseLogAspect sseLogAspect() {
        return new SseLogAspect();
    }

    /**
     * 默认定时任务日志处理器（降级打印，业务方可覆盖）
     */
    @Bean
    @ConditionalOnMissingBean(ScheduleLogHandler.class)
    public ScheduleLogHandler defaultScheduleLogHandler() {
        return entity -> {
            if (entity == null) return;
            log.debug("[schedule] job={}/{} cost={}ms status={}",
                    LogEntityBinder.get(entity, "jobGroup"),
                    LogEntityBinder.get(entity, "jobName"),
                    LogEntityBinder.get(entity, "costMs"),
                    entity.getStatus());
        };
    }

    /**
     * 默认 AI 调用日志处理器（降级打印，业务方可覆盖）
     */
    @Bean
    @ConditionalOnMissingBean(AiCallLogHandler.class)
    public AiCallLogHandler defaultAiCallLogHandler() {
        return entity -> {
            if (entity == null) return;
            log.debug("[ai-call] provider={} model={} cost={}ms status={}",
                    LogEntityBinder.get(entity, "provider"),
                    LogEntityBinder.get(entity, "model"),
                    LogEntityBinder.get(entity, "costMs"),
                    entity.getStatus());
        };
    }

    /**
     * 默认自定义任务日志处理器（降级打印，业务方可覆盖）
     */
    @Bean
    @ConditionalOnMissingBean(TaskLogHandler.class)
    public TaskLogHandler defaultTaskLogHandler() {
        return entity -> {
            if (entity == null) return;
            log.debug("[task] name={} type={} cost={}ms status={}",
                    LogEntityBinder.get(entity, "taskName"),
                    LogEntityBinder.get(entity, "taskType"),
                    LogEntityBinder.get(entity, "costMs"),
                    entity.getStatus());
        };
    }

    /**
     * 默认 SSE 推送日志处理器（降级打印，业务方可覆盖）
     */
    @Bean
    @ConditionalOnMissingBean(SseLogHandler.class)
    public SseLogHandler defaultSseLogHandler() {
        return entity -> {
            if (entity == null) return;
            log.debug("[sse] type={} target={} cost={}ms status={}",
                    LogEntityBinder.get(entity, "messageType"),
                    LogEntityBinder.get(entity, "targetType"),
                    LogEntityBinder.get(entity, "costMs"),
                    entity.getStatus());
        };
    }

}
