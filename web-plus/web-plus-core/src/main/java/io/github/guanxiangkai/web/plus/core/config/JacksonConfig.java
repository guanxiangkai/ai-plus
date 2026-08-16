package io.github.guanxiangkai.web.plus.core.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.jackson.autoconfigure.JsonMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.SerializationFeature;

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
public class JacksonConfig {

    /**
     * JsonMapper 构建器定制
     * <p>
     * 保持对未知字段和空 Bean 的兼容读取。日期格式与时区由最终应用通过
     * {@code spring.jackson.*} 或运行环境统一配置，基础库不修改 JVM 或映射器的地域默认值。
     * </p>
     */
    @Bean("webPlusJsonMapperBuilderCustomizer")
    @ConditionalOnMissingBean(name = "webPlusJsonMapperBuilderCustomizer")
    public JsonMapperBuilderCustomizer jsonMapperBuilderCustomizer() {
        return builder -> {
            log.info("初始化 Jackson 3 JsonMapper 自定义配置");
            builder.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
            builder.disable(SerializationFeature.FAIL_ON_EMPTY_BEANS);
        };
    }
}
