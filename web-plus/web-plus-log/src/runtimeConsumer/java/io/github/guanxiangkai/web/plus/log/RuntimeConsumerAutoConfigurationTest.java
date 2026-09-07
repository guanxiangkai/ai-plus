package io.github.guanxiangkai.web.plus.log;

import io.github.guanxiangkai.web.plus.core.net.ClientIpResolver;
import io.github.guanxiangkai.web.plus.log.aspect.OperationLogAspect;
import io.github.guanxiangkai.web.plus.log.autoconfigure.WebPlusLogAutoConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ReactiveWebApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

/** 仅消费已发布运行时依赖时，日志自动配置仍可创建操作日志切面。 */
class RuntimeConsumerAutoConfigurationTest {

    private final ReactiveWebApplicationContextRunner contextRunner = new ReactiveWebApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(WebPlusLogAutoConfiguration.class))
            .withBean(ClientIpResolver.class, () -> request -> "127.0.0.1");

    @Test
    void operationLogAutoConfigurationRequiresPublishedAspectjRuntime() {
        contextRunner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).hasSingleBean(OperationLogAspect.class);
        });
    }
}
