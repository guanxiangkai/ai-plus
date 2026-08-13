package io.github.guanxiangkai.web.plus.log.annotation;

import java.lang.annotation.*;

/**
 * SSE 消息推送日志注解
 *
 * <pre>{@code
 * @SseLog(entity = SysSseLog.class, messageType = "ORDER_STATUS_CHANGED", targetType = "USER")
 * public Mono<Void> pushOrderStatus(String userId, OrderStatusEvent event) { ... }
 * }</pre>
 *
 * @author guanxiangkai
 * @see io.github.guanxiangkai.web.plus.log.spi.SseLogHandler
 * @since 1.0.0
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface SseLog {

    /**
     * 日志实体类（需继承 {@code BaseLog}）；切面将创建其实例并按字段名约定填充后传给 Handler
     */
    Class<?> entity() default Void.class;

    /**
     * 消息类型编码，支持 SpEL 模板
     */
    String messageType() default "";

    /**
     * 推送目标类型（USER / USERS / TENANT / BROADCAST），支持 SpEL 模板
     */
    String targetType() default "USER";

    /**
     * 日志描述，支持 SpEL 模板
     */
    String description() default "";

    /**
     * 是否记录推送内容快照（默认 false）
     */
    boolean saveContent() default false;
}
