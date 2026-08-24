package io.github.guanxiangkai.web.plus.mq.config;

import io.github.guanxiangkai.web.plus.core.context.RequestContextThreadLocalAccessor;
import io.github.guanxiangkai.web.plus.core.spi.TraceIdGenerator;
import io.github.guanxiangkai.web.plus.mq.context.TraceMessageChannelInterceptor;
import io.github.guanxiangkai.web.plus.mq.producer.MessageProducer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.cloud.stream.function.StreamBridge;
import org.springframework.context.annotation.Bean;
import org.springframework.integration.config.GlobalChannelInterceptor;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * MQ 模块自动配置
 * <p>
 * 基于 Spring Cloud Stream 实现。
 * 仅在 spring-cloud-stream 在 classpath 时生效。
 * 底层 Binder（Kafka / RabbitMQ 等）由 YAML 配置决定。
 * </p>
 *
 * @author guanxiangkai
 * @since 1.0.0
 */
@Slf4j
@AutoConfiguration
@EnableAsync
@ConditionalOnClass(StreamBridge.class)
public class MqAutoConfiguration {

    public MqAutoConfiguration() {
        log.info("[web-plus] MQ 模块已启用（Spring Cloud Stream）");
    }

    @Bean
    @ConditionalOnMissingBean(MessageProducer.class)
    public MessageProducer messageProducer(StreamBridge streamBridge) {
        return new MessageProducer(streamBridge);
    }

    /** 注册覆盖所有函数式入站 binding 的 TraceId 消费上下文拦截器。 */
    @Bean
    @GlobalChannelInterceptor(patterns = "*-in-*", order = -100)
    @ConditionalOnMissingBean(TraceMessageChannelInterceptor.class)
    public TraceMessageChannelInterceptor traceMessageChannelInterceptor(
            RequestContextThreadLocalAccessor contextAccessor,
            ObjectProvider<TraceIdGenerator> traceIdGeneratorProvider) {
        TraceIdGenerator generator = traceIdGeneratorProvider.getIfAvailable(() -> () ->
                java.util.UUID.randomUUID().toString().replace("-", ""));
        return new TraceMessageChannelInterceptor(contextAccessor, generator);
    }
}
