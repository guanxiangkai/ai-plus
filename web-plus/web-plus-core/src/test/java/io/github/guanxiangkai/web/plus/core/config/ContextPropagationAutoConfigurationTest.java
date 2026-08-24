package io.github.guanxiangkai.web.plus.core.config;

import io.github.guanxiangkai.web.plus.core.context.RequestContext;
import io.github.guanxiangkai.web.plus.core.context.RequestContextHolder;
import io.github.guanxiangkai.web.plus.core.context.RequestContextThreadLocalAccessor;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.core.task.TaskDecorator;

import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;

class ContextPropagationAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(ContextPropagationAutoConfiguration.class));

    @Test
    void virtualThreadExecutorPropagatesAndRestoresRequestContext() throws Exception {
        ContextPropagationAutoConfiguration configuration = new ContextPropagationAutoConfiguration();
        RequestContextThreadLocalAccessor accessor = configuration.requestContextThreadLocalAccessor();
        configuration.requestContextAccessorRegistrar(accessor).afterSingletonsInstantiated();
        TaskDecorator decorator = configuration.contextTaskDecorator(accessor);
        VirtualThreadConfig virtualThreads = new VirtualThreadConfig(decorator);
        RequestContext parent = new RequestContext(
                "trace-parent", "/test", "GET", null, null, System.currentTimeMillis());

        RequestContextHolder.set(parent);
        try {
            Future<RequestContext> child = virtualThreads.applicationTaskExecutor()
                    .submit(RequestContextHolder::get);

            assertThat(child.get()).isEqualTo(parent);
            assertThat(RequestContextHolder.get()).isEqualTo(parent);
        } finally {
            RequestContextHolder.clear();
        }
    }

    @Test
    void preservesCustomAccessorAndTaskDecorator() {
        RequestContextThreadLocalAccessor accessor = new RequestContextThreadLocalAccessor();
        TaskDecorator decorator = runnable -> runnable;

        contextRunner
                .withBean(RequestContextThreadLocalAccessor.class, () -> accessor)
                .withBean(ContextPropagationAutoConfiguration.TASK_DECORATOR_BEAN_NAME,
                        TaskDecorator.class, () -> decorator)
                .run(context -> {
                    assertThat(context).hasSingleBean(RequestContextThreadLocalAccessor.class);
                    assertThat(context.getBean(RequestContextThreadLocalAccessor.class)).isSameAs(accessor);
                    assertThat(context.getBean(
                            ContextPropagationAutoConfiguration.TASK_DECORATOR_BEAN_NAME,
                            TaskDecorator.class)).isSameAs(decorator);
                });
    }
}
