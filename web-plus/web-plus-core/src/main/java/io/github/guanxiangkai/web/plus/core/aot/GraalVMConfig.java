package io.github.guanxiangkai.web.plus.core.aot;

import io.github.guanxiangkai.web.plus.core.enums.YesNoEnum;
import io.github.guanxiangkai.web.plus.core.model.ApiResponse;
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
 * Spring Boot 4 AOT 特性：
 * 1. 自动注册反射信息
 * 2. 编译时优化
 * 3. 减少启动时间
 * 4. 降低内存占用
 * </p>
 * <p>
 * 为 GraalVM Native Image 编译提供运行时提示：
 * - 反射提示：需要反射访问的类
 * - 资源提示：需要包含的资源文件
 * - 代理提示：JDK 动态代理类
 * - 序列化提示：需要序列化的类
 * </p>
 *
 * @author guanxiangkai
 * @since 1.0.0
 */
@Slf4j
@Configuration
@ImportRuntimeHints(GraalVMConfig.Registrar.class)
public class GraalVMConfig {

    static class Registrar implements RuntimeHintsRegistrar {

        @Override
        public void registerHints(@NonNull RuntimeHints hints, ClassLoader classLoader) {
            log.info("注册 GraalVM Native Image 运行时提示");

            // 1. 注册 Record 类
            registerRecordClasses(hints);

            // 2. 注册枚举类
            registerEnumClasses(hints);

            // 3. 注册资源文件
            registerResources(hints);

            log.info("GraalVM 运行时提示注册完成");
        }

        /**
         * 注册 Record 类（用于 JSON 序列化）
         * <p>
         * Record 类通过公共方法访问字段，不需要直接字段访问
         * </p>
         */
        private void registerRecordClasses(RuntimeHints hints) {
            hints.reflection().registerType(
                    ApiResponse.class,
                    hint -> hint.withMembers(
                            MemberCategory.INVOKE_DECLARED_CONSTRUCTORS,
                            MemberCategory.INVOKE_PUBLIC_CONSTRUCTORS,
                            MemberCategory.INVOKE_PUBLIC_METHODS
                    )
            );
            log.debug("已注册 Record 类: ApiResponse");
        }

        /**
         * 注册枚举类
         */
        private void registerEnumClasses(RuntimeHints hints) {
            Class<?>[] enumClasses = {
                    YesNoEnum.class
            };

            for (Class<?> enumClass : enumClasses) {
                hints.reflection().registerType(
                        enumClass,
                        hint -> hint.withMembers(
                                MemberCategory.INVOKE_PUBLIC_CONSTRUCTORS,
                                MemberCategory.INVOKE_PUBLIC_METHODS
                        )
                );
                log.debug("已注册枚举类: {}", enumClass.getSimpleName());
            }
        }

        /**
         * 注册资源文件
         */
        private void registerResources(RuntimeHints hints) {
            // Spring Boot 自动配置文件
            hints.resources().registerPattern(
                    "META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports"
            );

            // 应用配置文件
            hints.resources().registerPattern("application*.yml");
            hints.resources().registerPattern("application*.yaml");
            hints.resources().registerPattern("application*.properties");

            log.debug("已注册资源文件");
        }
    }
}

