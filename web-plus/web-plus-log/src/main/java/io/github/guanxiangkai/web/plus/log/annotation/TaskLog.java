package io.github.guanxiangkai.web.plus.log.annotation;

import java.lang.annotation.*;

/**
 * 自定义任务日志注解
 *
 * <pre>{@code
 * @TaskLog(entity = SysTaskLog.class, taskName = "文件批量处理", taskType = "IMPORT")
 * public void importUsers(List<UserDTO> users) { ... }
 * }</pre>
 *
 * @author guanxiangkai
 * @see io.github.guanxiangkai.web.plus.log.spi.TaskLogHandler
 * @since 1.0.0
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface TaskLog {

    /**
     * 日志实体类（需继承 {@code BaseLog}）；切面将创建其实例并按字段名约定填充后传给 Handler
     */
    Class<?> entity() default Void.class;

    /**
     * 任务名称，支持 SpEL 模板
     */
    String taskName() default "";

    /** 任务类型（如 "IMPORT"、"EXPORT"、"SYNC"），支持 SpEL 模板 */
    String taskType() default "CUSTOM";

    /** 任务描述，支持 SpEL 模板 */
    String description() default "";

    /** 是否序列化方法入参作为任务参数（默认 true） */
    boolean saveParams() default true;
}
