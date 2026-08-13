package io.github.guanxiangkai.web.plus.log.annotation;

import java.lang.annotation.*;

/**
 * 定时任务日志注解
 *
 * <pre>{@code
 * @Scheduled(cron = "0 0/5 * * * ?")
 * @ScheduleLog(entity = SysScheduleLog.class, jobName = "数据同步任务", jobGroup = "system")
 * public void syncData() { ... }
 * }</pre>
 *
 * @author guanxiangkai
 * @see io.github.guanxiangkai.web.plus.log.spi.ScheduleLogHandler
 * @since 1.0.0
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface ScheduleLog {

    /**
     * 日志实体类（需继承 {@code BaseLog}）；切面将创建其实例并按字段名约定填充后传给 Handler
     */
    Class<?> entity() default Void.class;

    /**
     * 任务名称，支持 SpEL 模板
     */
    String jobName() default "";

    /** 任务分组（默认 "DEFAULT"） */
    String jobGroup() default "DEFAULT";

    /** CRON 表达式（用于日志记录，不影响调度行为） */
    String cronExpression() default "";

    /** 触发方式（CRON / FIXED_RATE / FIXED_DELAY / MANUAL） */
    String triggerType() default "CRON";

    /** 任务描述，支持 SpEL 模板 */
    String description() default "";

    /** 是否序列化方法入参作为任务参数（默认 true） */
    boolean saveParams() default true;
}
