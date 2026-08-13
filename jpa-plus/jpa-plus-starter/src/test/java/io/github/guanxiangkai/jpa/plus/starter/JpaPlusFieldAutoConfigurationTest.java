package io.github.guanxiangkai.jpa.plus.starter;

import io.github.guanxiangkai.jpa.plus.field.dict.annotation.Dict;
import io.github.guanxiangkai.jpa.plus.field.dict.handler.DictFieldHandler;
import io.github.guanxiangkai.jpa.plus.field.dict.model.DictTranslateItem;
import io.github.guanxiangkai.jpa.plus.field.dict.spi.DictProvider;
import io.github.guanxiangkai.jpa.plus.starter.dict.CachedDictProvider;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import java.lang.reflect.Field;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class JpaPlusFieldAutoConfigurationTest {

    private final AtomicInteger providerCalls = new AtomicInteger();

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(JpaPlusFieldAutoConfiguration.class))
            .withBean(DictProvider.class, () -> dictCode -> {
                providerCalls.incrementAndGet();
                return List.of(new DictTranslateItem("status", "enabled", "启用", null, 0));
            });

    @Test
    void dictFieldHandlerUsesCachedProviderWhenCacheEnabled() {
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(CachedDictProvider.class);
            DictFieldHandler handler = context.getBean(DictFieldHandler.class);

            assertThat(translateTwice(handler)).isEqualTo("启用");
            assertThat(providerCalls).hasValue(1);
        });
    }

    @Test
    void dictFieldHandlerUsesUniqueRawProviderWhenCacheDisabled() {
        contextRunner
                .withPropertyValues("jpa-plus.dict.cache.enabled=false")
                .run(context -> {
                    assertThat(context).doesNotHaveBean(CachedDictProvider.class);
                    DictFieldHandler handler = context.getBean(DictFieldHandler.class);

                    assertThat(translateTwice(handler)).isEqualTo("启用");
                    assertThat(providerCalls).hasValue(2);
                });
    }

    @Test
    void customDictFieldHandlerOverridesDefaultHandler() {
        DictFieldHandler customHandler = new DictFieldHandler(dictCode -> List.of());

        contextRunner
                .withBean(DictFieldHandler.class, () -> customHandler)
                .run(context -> assertThat(context.getBean(DictFieldHandler.class)).isSameAs(customHandler));
    }

    private String translateTwice(DictFieldHandler handler) throws NoSuchFieldException {
        Field statusField = DictProbe.class.getDeclaredField("status");
        DictProbe first = new DictProbe();
        DictProbe second = new DictProbe();
        handler.afterQuery(first, statusField);
        handler.afterQuery(second, statusField);
        return second.statusLabel;
    }

    private static final class DictProbe {
        @Dict(type = "status")
        private final String status = "enabled";
        private String statusLabel;
    }
}
