package io.github.guanxiangkai.web.plus.mq.config;

import io.github.guanxiangkai.web.plus.mq.producer.MessageProducer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.cloud.stream.function.StreamBridge;
import org.springframework.context.annotation.Bean;
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
        log.info("AI-Common-MQ 模块已启用（Spring Cloud Stream）");
    }

    @Bean
    public MessageProducer messageProducer(StreamBridge streamBridge) {
        return new MessageProducer(streamBridge);
    }
}
