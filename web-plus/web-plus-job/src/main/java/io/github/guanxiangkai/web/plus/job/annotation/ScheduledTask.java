package io.github.guanxiangkai.web.plus.job.annotation;

import java.lang.annotation.*;

/**
 * 标记一个 Spring Bean 为 PowerJob 任务处理器。
 * <p>
 * 配合 {@link io.github.guanxiangkai.web.plus.job.handler.AbstractPowerJobHandler} 使用，
 * 在 PowerJob 控制台创建任务时，「处理器信息」填写 Spring Bean 名称（类名首字母小写）。
 * </p>
 *
 * <pre>
 * {@code
 * @Component
 * @ScheduledTask(name = "清理过期文件", description = "定期清理 OSS 过期文件记录")
 * public class CleanExpiredOssFilesHandler extends AbstractPowerJobHandler {
 *     @Override
 *     public void execute(String params) {
 *         // 业务逻辑
 *     }
 * }
 * }
 * </pre>
 *
 * @author guanxiangkai
 * @since 1.0.0
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface ScheduledTask {

    /**
     * 任务名称（对应 PowerJob 控制台「任务名称」字段），应简洁唯一。
     */
    String name();

    /**
     * 任务描述，说明该任务的用途与触发频率（可选）。
     */
    String description() default "";
}

