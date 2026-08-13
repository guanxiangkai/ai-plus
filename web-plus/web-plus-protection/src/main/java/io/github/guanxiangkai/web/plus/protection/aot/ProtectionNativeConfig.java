package io.github.guanxiangkai.web.plus.protection.aot;

import io.github.guanxiangkai.web.plus.protection.filter.ApiRateLimitFilter;
import io.github.guanxiangkai.web.plus.protection.filter.DebounceFilter;
import io.github.guanxiangkai.web.plus.protection.properties.ApiRateLimitProperties;
import io.github.guanxiangkai.web.plus.protection.properties.DebounceProperties;
import org.springframework.aot.hint.MemberCategory;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.RuntimeHintsRegistrar;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.ImportRuntimeHints;

/**
 * web-plus-protection Native Image 运行时提示。
 */
@Configuration
@ImportRuntimeHints(ProtectionNativeConfig.Registrar.class)
public class ProtectionNativeConfig {

    static class Registrar implements RuntimeHintsRegistrar {

        @Override
        public void registerHints(RuntimeHints hints, ClassLoader classLoader) {
            Class<?>[] classes = {
                    ApiRateLimitProperties.class,
                    DebounceProperties.class,
                    ApiRateLimitFilter.class,
                    DebounceFilter.class
            };
            for (Class<?> type : classes) {
                hints.reflection().registerType(type,
                        MemberCategory.INVOKE_DECLARED_CONSTRUCTORS,
                        MemberCategory.INVOKE_DECLARED_METHODS,
                        MemberCategory.INVOKE_PUBLIC_METHODS);
            }
            hints.resources().registerPattern("META-INF/spring/*");
        }
    }
}
