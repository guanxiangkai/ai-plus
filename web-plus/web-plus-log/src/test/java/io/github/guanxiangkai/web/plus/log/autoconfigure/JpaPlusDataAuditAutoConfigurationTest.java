package io.github.guanxiangkai.web.plus.log.autoconfigure;

import io.github.guanxiangkai.web.plus.log.bridge.JpaPlusDataAuditEventBridge;
import io.github.guanxiangkai.web.plus.log.spi.DataChangeHandler;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ReactiveWebApplicationContextRunner;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;

class JpaPlusDataAuditAutoConfigurationTest {

    private final ReactiveWebApplicationContextRunner contextRunner = new ReactiveWebApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    WebPlusLogAutoConfiguration.class,
                    JpaPlusDataAuditAutoConfiguration.class))
            .withBean(ObjectMapper.class, ObjectMapper::new);

    @Test
    void shouldRegisterSingleJpaPlusDataAuditBridgeWhenAuditClasspathPresent() {
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(DataChangeHandler.class);
            assertThat(context).hasSingleBean(JpaPlusDataAuditEventBridge.class);
        });
    }

    @Test
    void shouldDisableJpaPlusDataAuditBridgeByProperty() {
        contextRunner
                .withPropertyValues("web-plus.log.data-audit-enabled=false")
                .run(context -> assertThat(context).doesNotHaveBean(JpaPlusDataAuditEventBridge.class));
    }
}
