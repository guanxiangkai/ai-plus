package io.github.guanxiangkai.web.plus.security.aot;

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
 * 为安全模块提供 Native Image 支持（反射注册）
 * </p>
 *
 * @author guanxiangkai
 * @since 1.0.0
 */
@Slf4j
@Configuration
@ImportRuntimeHints(SecurityNativeConfig.Registrar.class)
public class SecurityNativeConfig {

    static class Registrar implements RuntimeHintsRegistrar {

        @Override
        public void registerHints(@NonNull RuntimeHints hints, ClassLoader classLoader) {
            log.info("注册 Security 模块 Native Image 运行时提示");

            // 1. 注册 Spring Security 响应式相关类
            registerReactiveSecurityHints(hints);

            // 2. 注册 Hutool JWT 相关类
            registerHutoolJwtHints(hints);

            log.info("Security 模块 Native Image 运行时提示注册完成");
        }

        private void registerReactiveSecurityHints(RuntimeHints hints) {
            try {
                Class<?> filterChainClass = Class.forName(
                        "org.springframework.security.web.server.SecurityWebFilterChain");
                hints.reflection().registerType(
                        filterChainClass,
                        hint -> hint.withMembers(MemberCategory.INVOKE_PUBLIC_METHODS)
                );
                log.debug("已注册 Spring Security 响应式类");
            } catch (ClassNotFoundException e) {
                log.debug("Spring Security 响应式类不存在，跳过注册");
            }
        }

        private void registerHutoolJwtHints(RuntimeHints hints) {
            try {
                Class<?> jwtUtilClass = Class.forName("cn.hutool.jwt.JWTUtil");
                hints.reflection().registerType(
                        jwtUtilClass,
                        hint -> hint.withMembers(MemberCategory.INVOKE_PUBLIC_METHODS)
                );
                log.debug("已注册 Hutool JWT 类");
            } catch (ClassNotFoundException e) {
                log.debug("Hutool JWT 类不存在，跳过注册");
            }
        }
    }
}