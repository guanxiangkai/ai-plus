package io.github.guanxiangkai.web.plus.core.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.jackson.autoconfigure.JsonMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.SerializationFeature;

import java.util.TimeZone;

/**
 * Jackson 3 配置
 * <p>
 * 使用 {@code tools.jackson} 包中的 Jackson 3 API。
 * </p>
 *
 * @author guanxiangkai
 * @since 1.0.0
 */
@Slf4j
@Configuration
public class JacksonConfig {

    /**
     * JsonMapper 构建器定制
     * <p>
     * 使用 JsonMapper 构建不可变配置，默认日期格式为 ISO-8601，属性按字母顺序排序。
     * <p>
     * 日期时间格式通过 {@code spring.jackson.date-format} 配置。
     * </p>
     */
    @Bean
    public JsonMapperBuilderCustomizer jsonMapperBuilderCustomizer() {
        return builder -> {
            log.info("初始化 Jackson 3 JsonMapper 自定义配置");
            builder.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
            builder.disable(SerializationFeature.FAIL_ON_EMPTY_BEANS);
            builder.defaultTimeZone(TimeZone.getTimeZone("Asia/Shanghai"));
        };
    }
}
