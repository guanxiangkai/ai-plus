package io.github.guanxiangkai.web.plus.log.autoconfigure;

import io.github.guanxiangkai.web.plus.core.spi.TraceIdGenerator;
import io.github.guanxiangkai.web.plus.log.client.TraceIdWebClientCustomizer;
import io.github.guanxiangkai.web.plus.log.filter.TraceIdFilter;
import io.github.guanxiangkai.web.plus.log.properties.LogProperties;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.UUID;

/**
 * HTTP TraceId 生成、入站响应和出站透传自动配置。
 *
 * @author guanxiangkai
 * @since 1.0.0
 */
@AutoConfiguration(beforeName = "org.springframework.boot.webclient.autoconfigure.WebClientAutoConfiguration")
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.REACTIVE)
@ConditionalOnProperty(prefix = "web-plus.log", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(LogProperties.class)
public class TracePropagationAutoConfiguration {

    /** 创建优先复用 Micrometer 当前 Span 的 TraceId 生成器。 */
    @Bean
    @ConditionalOnMissingBean(TraceIdGenerator.class)
    public TraceIdGenerator traceIdGenerator(ObjectProvider<Tracer> tracerProvider) {
        return new TraceIdGenerator() {
            @Override
            public String currentTraceId() {
                Tracer tracer = tracerProvider.getIfAvailable();
                Span span = tracer == null ? null : tracer.currentSpan();
                return span == null ? null : span.context().traceId();
            }

            @Override
            public String generate() {
                return UUID.randomUUID().toString().replace("-", "");
            }
        };
    }

    /** 创建覆盖所有 WebFlux 响应（包括网关短路响应）的 TraceId 过滤器。 */
    @Bean
    @ConditionalOnMissingBean(TraceIdFilter.class)
    public TraceIdFilter traceIdFilter(TraceIdGenerator generator, LogProperties properties) {
        return new TraceIdFilter(generator, properties.traceHeaderName());
    }

    /**
     * 仅在 WebClient 自定义器 API 可用时注册出站 TraceId 透传。
     *
     * <p>可选类型只能由嵌套配置引用，避免缺少该 API 时外层自动配置在条件求值前解析方法返回类型。</p>
     */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(name = {
            "org.springframework.web.reactive.function.client.WebClient",
            "org.springframework.boot.webclient.WebClientCustomizer"
    })
    static class WebClientTracePropagationConfiguration {

        /** 让所有通过 Spring Boot 自动配置构建器创建的 WebClient 继续透传 TraceId。 */
        @Bean
        @ConditionalOnMissingBean(TraceIdWebClientCustomizer.class)
        TraceIdWebClientCustomizer traceIdWebClientCustomizer(LogProperties properties) {
            return new TraceIdWebClientCustomizer(properties.traceHeaderName());
        }
    }
}
