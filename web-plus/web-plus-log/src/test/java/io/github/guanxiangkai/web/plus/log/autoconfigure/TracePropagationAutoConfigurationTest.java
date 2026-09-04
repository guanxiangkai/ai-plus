package io.github.guanxiangkai.web.plus.log.autoconfigure;

import io.github.guanxiangkai.web.plus.core.spi.TraceIdGenerator;
import io.github.guanxiangkai.web.plus.log.client.TraceIdWebClientCustomizer;
import io.github.guanxiangkai.web.plus.log.filter.TraceIdFilter;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.TraceContext;
import io.micrometer.tracing.Tracer;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.runner.ReactiveWebApplicationContextRunner;
import org.springframework.boot.webclient.WebClientCustomizer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TracePropagationAutoConfigurationTest {

    private final ReactiveWebApplicationContextRunner contextRunner = new ReactiveWebApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(TracePropagationAutoConfiguration.class));

    @Test
    void registersTraceEntryAndWebClientPropagationByDefault() {
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(TraceIdGenerator.class);
            assertThat(context).hasSingleBean(TraceIdFilter.class);
            assertThat(context).hasSingleBean(TraceIdWebClientCustomizer.class);
        });
    }

    @Test
    void resolvesCurrentMicrometerTraceId() {
        Tracer tracer = mock(Tracer.class);
        Span span = mock(Span.class);
        TraceContext traceContext = mock(TraceContext.class);
        when(tracer.currentSpan()).thenReturn(span);
        when(span.context()).thenReturn(traceContext);
        when(traceContext.traceId()).thenReturn("0123456789abcdef0123456789abcdef");

        contextRunner.withBean(Tracer.class, () -> tracer).run(context ->
                assertThat(context.getBean(TraceIdGenerator.class).currentTraceId())
                        .isEqualTo("0123456789abcdef0123456789abcdef"));
    }

    @Test
    void disablesTracePropagationWithLogModuleSwitch() {
        contextRunner.withPropertyValues("web-plus.log.enabled=false").run(context -> {
            assertThat(context).doesNotHaveBean(TraceIdGenerator.class);
            assertThat(context).doesNotHaveBean(TraceIdFilter.class);
            assertThat(context).doesNotHaveBean(TraceIdWebClientCustomizer.class);
        });
    }

    @Test
    void keepsInboundTracePropagationWhenWebClientCustomizerIsAbsent() {
        contextRunner.withClassLoader(new FilteredClassLoader(WebClientCustomizer.class))
                .run(context -> {
                    assertThat(context).hasSingleBean(TraceIdGenerator.class);
                    assertThat(context).hasSingleBean(TraceIdFilter.class);
                    assertThat(context).doesNotHaveBean(TraceIdWebClientCustomizer.class);
                });
    }

    @Test
    void preservesCustomTraceIdGenerator() {
        TraceIdGenerator custom = () -> "custom-trace";

        contextRunner.withBean(TraceIdGenerator.class, () -> custom).run(context ->
                assertThat(context.getBean(TraceIdGenerator.class)).isSameAs(custom));
    }
}
