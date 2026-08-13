package io.github.guanxiangkai.web.plus.job.aot;

import io.github.guanxiangkai.web.plus.job.annotation.ScheduledTask;
import io.github.guanxiangkai.web.plus.job.enums.JobStatus;
import io.github.guanxiangkai.web.plus.job.enums.MisfirePolicy;
import io.github.guanxiangkai.web.plus.job.handler.AbstractPowerJobHandler;
import io.github.guanxiangkai.web.plus.job.handler.TaskHandler;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.springframework.aot.hint.MemberCategory;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.RuntimeHintsRegistrar;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.ImportRuntimeHints;

/**
 * GraalVM Native Image AOT 配置
 * <p>
 * 为 Job 模块（PowerJob 模式）提供 Native Image 支持
 * </p>
 *
 * @author guanxiangkai
 * @since 1.0.0
 */
@Slf4j
@Configuration
@ImportRuntimeHints(JobNativeConfig.Registrar.class)
public class JobNativeConfig {

    static class Registrar implements RuntimeHintsRegistrar {

        @Override
        public void registerHints(@NonNull RuntimeHints hints, ClassLoader classLoader) {
            // 枚举类
            hints.reflection().registerType(JobStatus.class,
                    hint -> hint.withMembers(MemberCategory.INVOKE_PUBLIC_CONSTRUCTORS, MemberCategory.INVOKE_PUBLIC_METHODS));
            hints.reflection().registerType(MisfirePolicy.class,
                    hint -> hint.withMembers(MemberCategory.INVOKE_PUBLIC_CONSTRUCTORS, MemberCategory.INVOKE_PUBLIC_METHODS));
            // 处理器接口及基类
            hints.reflection().registerType(TaskHandler.class,
                    hint -> hint.withMembers(MemberCategory.INVOKE_PUBLIC_METHODS));
            hints.reflection().registerType(AbstractPowerJobHandler.class,
                    hint -> hint.withMembers(MemberCategory.INVOKE_DECLARED_CONSTRUCTORS, MemberCategory.INVOKE_PUBLIC_METHODS));
            // 任务元数据注解
            hints.reflection().registerType(ScheduledTask.class,
                    hint -> hint.withMembers(MemberCategory.INVOKE_PUBLIC_METHODS));
        }
    }
}

